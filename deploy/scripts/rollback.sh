#!/usr/bin/env bash
# 직전 릴리스로 수동 롤백한다 (root 실행). 사용법: rollback.sh [릴리스 파일 경로]
#   인자가 없으면 releases/ 에서 현재 JAR 바로 전 파일을 고른다.
#   인자를 주면 그 JAR로 되돌린다. 목록은 ls -1t /opt/busan-local-food/releases 로 본다.
set -euo pipefail

APP_DIR=/opt/busan-local-food
RELEASES="$APP_DIR/releases"
CURRENT_LINK="$APP_DIR/current.jar"
SERVICE=busan-local-food
HEALTH_URL=http://127.0.0.1:8080/

[ "$(id -u)" -eq 0 ] || { echo "root로 실행해야 합니다." >&2; exit 1; }

CURRENT_TARGET="$(readlink -f "$CURRENT_LINK" || true)"
if [ $# -ge 1 ]; then
  TARGET="$(readlink -f "$1")"
else
  TARGET="$(ls -1t "$RELEASES"/*.jar | grep -vxF "$CURRENT_TARGET" | head -n 1 || true)"
fi

case "$TARGET" in
  "$RELEASES"/*.jar) ;;
  *) echo "롤백 대상이 releases 안의 JAR가 아닙니다: ${TARGET:-없음}" >&2; exit 1 ;;
esac
[ -f "$TARGET" ] || { echo "대상 JAR가 없습니다: $TARGET" >&2; exit 1; }

echo "현재: ${CURRENT_TARGET:-없음}"
echo "롤백 대상: $TARGET"
ln -sfn "$TARGET" "$CURRENT_LINK.tmp"
mv -T "$CURRENT_LINK.tmp" "$CURRENT_LINK"
systemctl restart "$SERVICE"

for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$HEALTH_URL" || true)
  if [ "$code" = "200" ]; then
    echo "롤백 완료. 헬스 체크 통과."
    logger -t busan-deploy -- "rollback to $TARGET" || true
    exit 0
  fi
  sleep 2
done
echo "롤백 후 헬스 체크 실패. journalctl -u $SERVICE -n 200 로 확인하세요." >&2
exit 1
