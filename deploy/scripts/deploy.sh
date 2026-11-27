#!/usr/bin/env bash
# 운영 배포 스크립트 (EC2에서 root로 실행. GitHub Actions가 SSM Run Command로 호출한다).
# 사용법: deploy.sh <git-sha> <expected-sha256>
#   - 아티팩트: s3://$DEPLOY_ARTIFACT_BUCKET/releases/<git-sha>.jar (DEPLOY_ARTIFACT_BUCKET은 /etc/busan-local-food/deploy.env)
#   - 내려받은 JAR의 SHA-256이 기대값과 다르면 아무것도 바꾸지 않고 실패한다.
#   - 새 JAR로 current.jar 심볼릭 링크를 바꾸고 서비스를 재시작한 뒤 헬스 체크를 한다.
#   - 헬스 체크가 실패하면 직전 JAR로 되돌리고 다시 기동한 뒤 실패로 끝낸다.
set -euo pipefail

APP_DIR=/opt/busan-local-food
RELEASES="$APP_DIR/releases"
TMP_DIR="$APP_DIR/tmp"
CURRENT_LINK="$APP_DIR/current.jar"
SERVICE=busan-local-food
KEEP_RELEASES=5
HEALTH_URL=http://127.0.0.1:8080/
DEPLOY_ENV=/etc/busan-local-food/deploy.env

log() { echo "[deploy] $*"; logger -t busan-deploy -- "$*" || true; }
die() { log "FAILED: $*"; exit 1; }

[ "$(id -u)" -eq 0 ] || die "root로 실행해야 합니다."
[ $# -eq 2 ] || die "사용법: deploy.sh <git-sha> <expected-sha256>"
GIT_SHA="$1"
EXPECTED_SHA256="$2"
[[ "$GIT_SHA" =~ ^[0-9a-f]{40}$ ]] || die "git sha 형식이 아닙니다."
[[ "$EXPECTED_SHA256" =~ ^[0-9a-f]{64}$ ]] || die "sha256 형식이 아닙니다."
[ -r "$DEPLOY_ENV" ] || die "$DEPLOY_ENV 가 없습니다. docs/DEPLOYMENT.md 5절 참고."
# shellcheck disable=SC1090
. "$DEPLOY_ENV"
[ -n "${DEPLOY_ARTIFACT_BUCKET:-}" ] || die "DEPLOY_ARTIFACT_BUCKET 이 비어 있습니다."

health_ok() {
  local code i
  for i in $(seq 1 30); do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$HEALTH_URL" || true)
    if [ "$code" = "200" ]; then return 0; fi
    sleep 2
  done
  return 1
}

switch_to() {
  # 심볼릭 링크를 원자적으로 바꾼다.
  ln -sfn "$1" "$CURRENT_LINK.tmp"
  mv -T "$CURRENT_LINK.tmp" "$CURRENT_LINK"
}

PREVIOUS=""
if [ -L "$CURRENT_LINK" ]; then
  PREVIOUS="$(readlink -f "$CURRENT_LINK")"
fi

mkdir -p "$RELEASES" "$TMP_DIR"
STAMP="$(TZ=Asia/Seoul date +%Y%m%d-%H%M%S)"
NEW_JAR="$RELEASES/${STAMP}-${GIT_SHA:0:7}.jar"
DOWNLOAD="$TMP_DIR/download-${GIT_SHA}.jar"

log "아티팩트 내려받기: s3://${DEPLOY_ARTIFACT_BUCKET}/releases/${GIT_SHA}.jar"
aws s3 cp "s3://${DEPLOY_ARTIFACT_BUCKET}/releases/${GIT_SHA}.jar" "$DOWNLOAD" --only-show-errors \
  || { rm -f "$DOWNLOAD"; die "아티팩트 내려받기 실패"; }

ACTUAL_SHA256="$(sha256sum "$DOWNLOAD" | cut -d' ' -f1)"
if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
  rm -f "$DOWNLOAD"
  die "SHA-256 불일치. 배포를 중단하고 현재 버전을 유지합니다."
fi

mv "$DOWNLOAD" "$NEW_JAR"
chown root:busan "$NEW_JAR"
chmod 640 "$NEW_JAR"
log "새 JAR 준비: $NEW_JAR (이전: ${PREVIOUS:-없음})"

switch_to "$NEW_JAR"
systemctl restart "$SERVICE"

if health_ok; then
  log "헬스 체크 통과 (GET /). 배포 완료: ${GIT_SHA}"
else
  log "헬스 체크 실패. 직전 JAR로 되돌립니다."
  if [ -n "$PREVIOUS" ] && [ -f "$PREVIOUS" ]; then
    switch_to "$PREVIOUS"
    systemctl restart "$SERVICE"
    if health_ok; then
      log "롤백 완료. 직전 버전으로 정상 응답합니다: $PREVIOUS"
    else
      log "롤백 후에도 헬스 체크 실패. journalctl -u $SERVICE 확인이 필요합니다."
    fi
  else
    log "직전 JAR가 없어 롤백할 수 없습니다. journalctl -u $SERVICE 확인이 필요합니다."
  fi
  die "배포 실패 (${GIT_SHA})"
fi

# 오래된 릴리스 정리: 최근 KEEP_RELEASES개와 현재 JAR만 남긴다.
CURRENT_TARGET="$(readlink -f "$CURRENT_LINK")"
ls -1t "$RELEASES"/*.jar 2>/dev/null | tail -n +$((KEEP_RELEASES + 1)) | while read -r old; do
  [ "$old" = "$CURRENT_TARGET" ] && continue
  rm -f -- "$old"
  log "오래된 릴리스 삭제: $old"
done
