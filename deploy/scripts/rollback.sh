#!/usr/bin/env bash
# 수동 롤백 (root 실행). 사용법: rollback.sh [릴리스 JAR 경로]
#   인자가 없으면 releases/ 에서 현재 JAR 바로 전 파일을 고른다.
#   인자를 주면 그 JAR로 되돌린다. 목록은 ls -1t /opt/busan-local-food/releases 로 본다.
#
# 종료 코드: 0 롤백 후 정상 응답, 1 롤백 실패 (대상 없음, 재시작 명령 실패, 기동 실패, 헬스 체크 실패).
# 테스트용 덮어쓰기 변수 (운영에서는 지정하지 않는다): BUSAN_APP_DIR, BUSAN_HEALTH_URL, BUSAN_HEALTH_ATTEMPTS, BUSAN_HEALTH_INTERVAL
set -euo pipefail

APP_DIR="${BUSAN_APP_DIR:-/opt/busan-local-food}"
RELEASES="$APP_DIR/releases"
CURRENT_LINK="$APP_DIR/current.jar"
SERVICE=busan-local-food
HEALTH_URL="${BUSAN_HEALTH_URL:-http://127.0.0.1:8080/}"
HEALTH_ATTEMPTS="${BUSAN_HEALTH_ATTEMPTS:-30}"
HEALTH_INTERVAL="${BUSAN_HEALTH_INTERVAL:-2}"

log() { echo "$*"; logger -t busan-deploy -- "$*" 2>/dev/null || true; }
fail() { log "롤백 실패: $*"; exit 1; }

[ "$(id -u)" -eq 0 ] || fail "root로 실행해야 합니다."

CURRENT_TARGET="$(readlink -f "$CURRENT_LINK" 2>/dev/null || true)"
if [ $# -ge 1 ]; then
  TARGET="$(readlink -f "$1" 2>/dev/null || true)"
else
  TARGET="$(ls -1t "$RELEASES"/*.jar 2>/dev/null | grep -vxF "$CURRENT_TARGET" | head -n 1 || true)"
fi

case "$TARGET" in
  "$RELEASES"/*.jar) ;;
  *) fail "롤백 대상이 releases 안의 JAR가 아닙니다: ${TARGET:-없음}" ;;
esac
[ -f "$TARGET" ] || fail "대상 JAR가 없습니다: $TARGET"

log "현재: ${CURRENT_TARGET:-없음}"
log "롤백 대상: $TARGET"
ln -sfn "$TARGET" "$CURRENT_LINK.tmp"
mv -T "$CURRENT_LINK.tmp" "$CURRENT_LINK"

if ! systemctl restart "$SERVICE"; then
  fail "systemctl restart 명령이 실패했습니다. 링크는 $TARGET 를 가리킵니다. 확인: systemctl status $SERVICE"
fi
if ! systemctl is-active --quiet "$SERVICE"; then
  fail "재시작 뒤 서비스가 active 상태가 아닙니다. 확인: journalctl -u $SERVICE -n 200"
fi

for ((i = 1; i <= HEALTH_ATTEMPTS; i++)); do
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$HEALTH_URL" || true)"
  if [ "$code" = "200" ]; then
    log "롤백 완료. 헬스 체크 통과."
    exit 0
  fi
  sleep "$HEALTH_INTERVAL"
done
fail "헬스 체크(GET /)가 ${HEALTH_ATTEMPTS}회 안에 200을 반환하지 않았습니다. 확인: journalctl -u $SERVICE -n 200"
