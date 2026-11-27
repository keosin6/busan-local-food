#!/usr/bin/env bash
# MySQL 일일·주간 백업 (root, systemd timer로 매일 실행). docs/DEPLOYMENT.md 6절 참고.
#   - mysqldump --single-transaction 으로 InnoDB 일관된 스냅샷을 뜬다 (잠금 없이 읽기).
#   - 비밀번호는 명령행이 아니라 root 전용 defaults 파일로 넘긴다.
#   - 임시 파일을 gzip으로 만들고, S3 업로드 뒤 크기를 확인한다.
#   - 업로드 실패 시 로컬 파일을 지우지 않고 종료 코드 1로 끝낸다 (성공 처리 금지).
#   - 일요일(KST)에는 weekly/ 에도 한 부를 더 올린다. 수명은 S3 Lifecycle이 관리한다 (deploy/backup/s3-lifecycle.json).
# 설정: /etc/busan-local-food/backup.env (BACKUP_S3_BUCKET), /etc/busan-local-food/mysql-backup.cnf (root 600)
set -euo pipefail

DB_NAME=busan_local_food
CONF=/etc/busan-local-food/backup.env
MYSQL_CNF=/etc/busan-local-food/mysql-backup.cnf
WORK_DIR=/var/backups/busan-local-food/tmp
TAG=busan-mysql-backup

log() { echo "[backup] $*"; logger -t "$TAG" -- "$*" || true; }
fail() { log "FAILED: $*"; exit 1; }

[ "$(id -u)" -eq 0 ] || fail "root로 실행해야 합니다."
[ -r "$CONF" ] || fail "$CONF 가 없습니다."
[ -r "$MYSQL_CNF" ] || fail "$MYSQL_CNF 가 없습니다."
# shellcheck disable=SC1090
. "$CONF"
[ -n "${BACKUP_S3_BUCKET:-}" ] || fail "BACKUP_S3_BUCKET 이 비어 있습니다."

mkdir -p "$WORK_DIR"
chmod 700 "$WORK_DIR"

NOW_KST="$(TZ=Asia/Seoul date +%Y-%m-%d_%H%M%S)"
Y="$(TZ=Asia/Seoul date +%Y)"; M="$(TZ=Asia/Seoul date +%m)"; D="$(TZ=Asia/Seoul date +%d)"
WEEKDAY="$(TZ=Asia/Seoul date +%u)"   # 7 = 일요일
FILE_NAME="${DB_NAME}-${NOW_KST}.sql.gz"
LOCAL_FILE="$WORK_DIR/$FILE_NAME"

log "덤프 시작: $DB_NAME -> $LOCAL_FILE"
# 파이프 중간 실패도 잡도록 pipefail을 쓴다. 덤프 파일은 실패 시 부분 파일이 남을 수 있어 아래에서 정리한다.
if ! mysqldump --defaults-extra-file="$MYSQL_CNF" \
      --single-transaction --quick --routines --triggers --events \
      --no-tablespaces --default-character-set=utf8mb4 \
      "$DB_NAME" | gzip -9 > "$LOCAL_FILE"; then
  # 덤프 자체가 실패한 부분 파일은 백업이 아니므로 지운다. 업로드 실패와는 다르다.
  rm -f "$LOCAL_FILE"
  fail "mysqldump 실패. 부분 파일은 삭제했습니다."
fi
gzip -t "$LOCAL_FILE" || fail "gzip 무결성 검사 실패: $LOCAL_FILE (파일은 보존)"
LOCAL_SIZE="$(stat -c %s "$LOCAL_FILE")"
[ "$LOCAL_SIZE" -gt 0 ] || fail "덤프 파일이 비어 있습니다: $LOCAL_FILE (파일은 보존)"
log "덤프 완료: ${LOCAL_SIZE} bytes"

upload_and_verify() {
  local key="$1"
  aws s3 cp "$LOCAL_FILE" "s3://${BACKUP_S3_BUCKET}/${key}" --sse AES256 --only-show-errors \
    || return 1
  local remote_size
  remote_size="$(aws s3api head-object --bucket "$BACKUP_S3_BUCKET" --key "$key" \
      --query ContentLength --output text)" || return 1
  [ "$remote_size" = "$LOCAL_SIZE" ] || return 1
  log "업로드 확인: s3://${BACKUP_S3_BUCKET}/${key} (${remote_size} bytes)"
}

DAILY_KEY="daily/${Y}/${M}/${D}/${FILE_NAME}"
if ! upload_and_verify "$DAILY_KEY"; then
  fail "일일 백업 업로드 또는 검증 실패. 로컬 파일 보존: $LOCAL_FILE"
fi

if [ "$WEEKDAY" = "7" ]; then
  WEEKLY_KEY="weekly/${Y}/${M}/${D}/${FILE_NAME}"
  if ! upload_and_verify "$WEEKLY_KEY"; then
    fail "주간 백업 업로드 또는 검증 실패. 로컬 파일 보존: $LOCAL_FILE"
  fi
fi

rm -f "$LOCAL_FILE"
SUFFIX=""
[ "$WEEKDAY" = "7" ] && SUFFIX=" (주간 백업 포함)"
log "백업 성공: ${DAILY_KEY}${SUFFIX}, 로컬 임시 파일 삭제"
exit 0
