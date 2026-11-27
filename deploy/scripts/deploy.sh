#!/usr/bin/env bash
# 운영 배포 스크립트 (EC2에서 root로 실행. GitHub Actions가 SSM Run Command로 호출한다).
# 사용법: deploy.sh <git-sha> <expected-sha256>
#
# 동작:
#   1. 아티팩트 s3://$DEPLOY_ARTIFACT_BUCKET/releases/<git-sha>.jar 를 내려받고 SHA-256을 검증한다. 불일치하면 현재 버전을 건드리지 않는다.
#   2. current.jar 심볼릭 링크를 원자적으로 새 JAR로 바꾸고 서비스를 재시작한다.
#   3. 재시작 명령의 성공, 서비스 active 상태, 헬스 체크(GET /)를 각각 확인한다.
#   4. 새 버전이 어느 단계에서든 실패하면 직전 JAR로 링크를 되돌리고 재시작·헬스 체크를 다시 한다.
#
# 종료 코드:
#   0  배포 성공
#   1  배포 전 단계 실패(인자, 설정, 다운로드, SHA-256). 현재 버전은 그대로다.
#   2  새 버전 실패, 이전 버전으로 복구 성공
#   3  새 버전 실패, 이전 버전 복구도 실패. 즉시 수동 확인이 필요하다.
#   4  최초 배포 실패. 이전 JAR가 없어 자동 복구할 수 없다. 서비스가 비정상 상태일 수 있다.
#
# 테스트용 덮어쓰기 변수 (운영에서는 지정하지 않는다): BUSAN_APP_DIR, BUSAN_DEPLOY_ENV, BUSAN_HEALTH_URL, BUSAN_HEALTH_ATTEMPTS, BUSAN_HEALTH_INTERVAL
set -euo pipefail

APP_DIR="${BUSAN_APP_DIR:-/opt/busan-local-food}"
RELEASES="$APP_DIR/releases"
TMP_DIR="$APP_DIR/tmp"
CURRENT_LINK="$APP_DIR/current.jar"
SERVICE=busan-local-food
KEEP_RELEASES=5
HEALTH_URL="${BUSAN_HEALTH_URL:-http://127.0.0.1:8080/}"
HEALTH_ATTEMPTS="${BUSAN_HEALTH_ATTEMPTS:-30}"
HEALTH_INTERVAL="${BUSAN_HEALTH_INTERVAL:-2}"
DEPLOY_ENV="${BUSAN_DEPLOY_ENV:-/etc/busan-local-food/deploy.env}"

log() { echo "[deploy] $*"; logger -t busan-deploy -- "$*" 2>/dev/null || true; }
fail() { local code="$1"; shift; log "실패(코드 $code): $*"; exit "$code"; }
trap 'log "예기치 않은 오류 (스크립트 줄 $LINENO)"' ERR

# 헬스 체크: GET /가 200을 낼 때까지 기다린다. 제한 횟수를 넘으면 1을 돌려준다.
health_ok() {
  local i code
  for ((i = 1; i <= HEALTH_ATTEMPTS; i++)); do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$HEALTH_URL" || true)"
    if [ "$code" = "200" ]; then
      return 0
    fi
    sleep "$HEALTH_INTERVAL"
  done
  return 1
}

# 서비스를 재시작하고 확인한다. 실패 사유는 STEP_REASON에 남긴다.
#   반환 2: systemctl restart 명령 자체가 실패
#   반환 3: 재시작 뒤 서비스가 active가 아님 (기동 실패)
#   반환 4: 헬스 체크 실패
restart_and_verify() {
  STEP_REASON=""
  if ! systemctl restart "$SERVICE"; then
    STEP_REASON="systemctl restart 명령이 실패했습니다"
    return 2
  fi
  if ! systemctl is-active --quiet "$SERVICE"; then
    STEP_REASON="재시작 뒤 서비스가 active 상태가 아닙니다 (기동 실패)"
    return 3
  fi
  if ! health_ok; then
    STEP_REASON="헬스 체크(GET /)가 ${HEALTH_ATTEMPTS}회 안에 200을 반환하지 않았습니다"
    return 4
  fi
  return 0
}

# current.jar를 원자적으로 바꾼다 (임시 링크 생성 후 rename).
switch_to() {
  ln -sfn "$1" "$CURRENT_LINK.tmp"
  mv -T "$CURRENT_LINK.tmp" "$CURRENT_LINK"
}

prune_releases() {
  local current old
  current="$(readlink -f "$CURRENT_LINK")"
  ls -1t "$RELEASES"/*.jar 2>/dev/null | tail -n +$((KEEP_RELEASES + 1)) | while read -r old; do
    if [ "$old" != "$current" ]; then
      rm -f -- "$old"
      log "오래된 릴리스 삭제: $old"
    fi
  done
  return 0
}

# ---- 1. 배포 전 단계 (실패해도 현재 버전은 바뀌지 않는다) ----

[ "$(id -u)" -eq 0 ] || fail 1 "root로 실행해야 합니다."
[ $# -eq 2 ] || fail 1 "사용법: deploy.sh <git-sha> <expected-sha256>"
GIT_SHA="$1"
EXPECTED_SHA256="$2"
[[ "$GIT_SHA" =~ ^[0-9a-f]{40}$ ]] || fail 1 "git sha 형식이 아닙니다."
[[ "$EXPECTED_SHA256" =~ ^[0-9a-f]{64}$ ]] || fail 1 "sha256 형식이 아닙니다."
[ -r "$DEPLOY_ENV" ] || fail 1 "$DEPLOY_ENV 가 없습니다. docs/DEPLOYMENT.md 10절 참고."
# shellcheck disable=SC1090
. "$DEPLOY_ENV"
[ -n "${DEPLOY_ARTIFACT_BUCKET:-}" ] || fail 1 "DEPLOY_ARTIFACT_BUCKET 이 비어 있습니다."

mkdir -p "$RELEASES" "$TMP_DIR"

PREVIOUS=""
if [ -L "$CURRENT_LINK" ]; then
  PREVIOUS="$(readlink -f "$CURRENT_LINK")"
  if [ ! -f "$PREVIOUS" ]; then
    log "경고: current.jar가 가리키는 파일이 없습니다: $PREVIOUS (복구 대상 없음으로 취급)"
    PREVIOUS=""
  fi
fi

STAMP="$(TZ=Asia/Seoul date +%Y%m%d-%H%M%S)"
NEW_JAR="$RELEASES/${STAMP}-${GIT_SHA:0:7}.jar"
DOWNLOAD="$TMP_DIR/download-${GIT_SHA}.jar"

log "아티팩트 내려받기: s3://${DEPLOY_ARTIFACT_BUCKET}/releases/${GIT_SHA}.jar"
if ! aws s3 cp "s3://${DEPLOY_ARTIFACT_BUCKET}/releases/${GIT_SHA}.jar" "$DOWNLOAD" --only-show-errors; then
  rm -f "$DOWNLOAD"
  fail 1 "아티팩트 내려받기 실패"
fi

ACTUAL_SHA256="$(sha256sum "$DOWNLOAD" | cut -d' ' -f1)"
if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
  rm -f "$DOWNLOAD"
  fail 1 "SHA-256 불일치. 배포를 중단하고 현재 버전을 유지합니다."
fi

mv "$DOWNLOAD" "$NEW_JAR"
chown root:busan "$NEW_JAR"
chmod 640 "$NEW_JAR"

# ---- 2. 새 버전 적용과 확인 ----

switch_to "$NEW_JAR"
log "새 JAR 적용: $NEW_JAR (이전: ${PREVIOUS:-없음})"

if restart_and_verify; then
  log "배포 완료: ${GIT_SHA}"
  prune_releases
  exit 0
fi

NEW_REASON="$STEP_REASON"
log "새 버전 실패: $NEW_REASON. 실패한 JAR는 검사용으로 남겨 둡니다: $NEW_JAR"

# ---- 3. 복구 ----

if [ -z "$PREVIOUS" ]; then
  fail 4 "최초 배포 실패: 이전 JAR가 없어 자동 복구할 수 없습니다. 서비스가 비정상 상태일 수 있습니다. 확인: journalctl -u $SERVICE -n 200"
fi

log "이전 JAR로 복구합니다: $PREVIOUS"
switch_to "$PREVIOUS"

if restart_and_verify; then
  fail 2 "배포 실패 (${GIT_SHA}), 원인: ${NEW_REASON}. 이전 버전으로 복구했고 정상 응답합니다."
fi

fail 3 "배포 실패 (${GIT_SHA}), 원인: ${NEW_REASON}. 이전 버전 복구도 실패했습니다 (${STEP_REASON}). 즉시 확인하세요: journalctl -u $SERVICE -n 200"
