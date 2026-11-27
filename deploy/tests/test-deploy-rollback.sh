#!/usr/bin/env bash
# deploy.sh, rollback.sh 가짜 명령 테스트. 실제 systemd, S3, 네트워크, root 권한을 쓰지 않는다.
# 실행: bash deploy/tests/test-deploy-rollback.sh   (종료 코드 0: 모두 통과)
#
# 가짜 명령 동작 (테스트 임시 디렉터리의 bin/ 에 만든다):
#   - 현재 current.jar가 가리키는 JAR 내용으로 결과를 정한다.
#       RESTART_FAIL  : systemctl restart 실패
#       NOT_ACTIVE    : restart는 성공, is-active 실패 (기동 실패)
#       HEALTH_FAIL   : curl 헬스 체크 500
#   - aws s3 cp 는 store/<키의 파일명>을 복사한다.
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
DEPLOY="$HERE/../scripts/deploy.sh"
ROLLBACK="$HERE/../scripts/rollback.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

export BUSAN_APP_DIR="$WORK/app"
export BUSAN_DEPLOY_ENV="$WORK/etc/deploy.env"
export BUSAN_HEALTH_URL="http://127.0.0.1:8080/"
export BUSAN_HEALTH_ATTEMPTS=2
export BUSAN_HEALTH_INTERVAL=0
export FAKE_STORE="$WORK/store"
BIN="$WORK/bin"

mkdir -p "$BIN" "$FAKE_STORE" "$WORK/etc"

# ---- 가짜 명령 ----
cat > "$BIN/id" <<'STUB'
#!/usr/bin/env bash
[ "$1" = "-u" ] && echo 0 || exec /usr/bin/id "$@"
STUB
cat > "$BIN/chown" <<'STUB'
#!/usr/bin/env bash
exit 0
STUB
cat > "$BIN/logger" <<'STUB'
#!/usr/bin/env bash
exit 0
STUB
cat > "$BIN/aws" <<'STUB'
#!/usr/bin/env bash
# aws s3 cp s3://bucket/releases/<sha>.jar <dst>
src="$3"; dst="$4"
cp "$FAKE_STORE/$(basename "$src")" "$dst"
STUB
cat > "$BIN/systemctl" <<'STUB'
#!/usr/bin/env bash
target="$(readlink -f "$BUSAN_APP_DIR/current.jar" 2>/dev/null || true)"
case "$1" in
  restart)
    if [ -n "$target" ] && grep -q RESTART_FAIL "$target"; then exit 1; fi
    exit 0 ;;
  is-active)
    if [ -n "$target" ] && grep -q NOT_ACTIVE "$target"; then exit 3; fi
    exit 0 ;;
esac
exit 0
STUB
cat > "$BIN/curl" <<'STUB'
#!/usr/bin/env bash
target="$(readlink -f "$BUSAN_APP_DIR/current.jar" 2>/dev/null || true)"
if [ -n "$target" ] && grep -q HEALTH_FAIL "$target"; then printf 500; else printf 200; fi
STUB
chmod +x "$BIN"/*
export PATH="$BIN:$PATH"

# ---- 도우미 ----
PASS=0; FAIL=0
check() {  # 설명, 결과(0 또는 1)
  if [ "$2" -eq 0 ]; then PASS=$((PASS + 1)); echo "  ok   - $1"
  else FAIL=$((FAIL + 1)); echo "  FAIL - $1"; fi
}
fresh() {  # 빈 앱 디렉터리와 설정 파일
  rm -rf "$BUSAN_APP_DIR" && mkdir -p "$BUSAN_APP_DIR/releases" "$BUSAN_APP_DIR/tmp"
  echo "DEPLOY_ARTIFACT_BUCKET=test-bucket" > "$BUSAN_DEPLOY_ENV"
}
make_jar() {  # 이름(sha), 내용 표식
  printf '%s\n' "$2" > "$FAKE_STORE/$1.jar"
}
sha_of() { sha256sum "$FAKE_STORE/$1.jar" | cut -d' ' -f1; }
current_name() { basename "$(readlink -f "$BUSAN_APP_DIR/current.jar" 2>/dev/null)" 2>/dev/null || true; }
run_deploy() {  # sha, 기대 sha256 → 출력은 $WORK/out.log, 종료 코드는 $RC
  bash "$DEPLOY" "$1" "$2" > "$WORK/out.log" 2>&1
  RC=$?
}

A=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa   # 정상 버전
B=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb   # 새 버전 후보 (실패 표식)
C=cccccccccccccccccccccccccccccccccccccccc   # 추가 후보
DD=dddddddddddddddddddddddddddddddddddddddd  # 최초 배포 후보

echo "== 0. 기본 성공과 SHA-256 불일치"
fresh
make_jar "$A" "GOOD-A"
run_deploy "$A" "$(sha_of "$A")"
check "정상 배포 종료 코드 0" $([ "$RC" -eq 0 ]; echo $?)
OLD_TARGET="$(current_name)"
make_jar "$B" "GOOD-B"
run_deploy "$B" "0000000000000000000000000000000000000000000000000000000000000000"
check "SHA-256 불일치는 종료 코드 1" $([ "$RC" -eq 1 ]; echo $?)
check "SHA-256 불일치 시 현재 버전 유지" $([ "$(current_name)" = "$OLD_TARGET" ]; echo $?)

echo "== 1. 새 버전 재시작 명령 실패 → 이전 버전 복구"
fresh
make_jar "$A" "GOOD-A"; run_deploy "$A" "$(sha_of "$A")"
OLD_TARGET="$(current_name)"
make_jar "$B" "RESTART_FAIL-B"
run_deploy "$B" "$(sha_of "$B")"
check "종료 코드 2 (이전 버전으로 복구 성공)" $([ "$RC" -eq 2 ]; echo $?)
check "링크가 이전 버전으로 되돌아감" $([ "$(current_name)" = "$OLD_TARGET" ]; echo $?)
check "로그에 재시작 명령 실패 기록" $(grep -q "systemctl restart 명령이 실패" "$WORK/out.log"; echo $?)
check "로그에 복구 완료 기록" $(grep -q "이전 버전으로 복구했고 정상 응답" "$WORK/out.log"; echo $?)

echo "== 2. 새 버전 재시작 성공, 헬스 체크 실패 → 이전 버전 복구"
fresh
make_jar "$A" "GOOD-A"; run_deploy "$A" "$(sha_of "$A")"
OLD_TARGET="$(current_name)"
make_jar "$B" "HEALTH_FAIL-B"
run_deploy "$B" "$(sha_of "$B")"
check "종료 코드 2 (이전 버전으로 복구 성공)" $([ "$RC" -eq 2 ]; echo $?)
check "링크가 이전 버전으로 되돌아감" $([ "$(current_name)" = "$OLD_TARGET" ]; echo $?)
check "로그에 헬스 체크 실패 기록" $(grep -q "헬스 체크(GET /)가" "$WORK/out.log"; echo $?)

echo "== 2b. 새 버전 기동 실패(active 아님) → 이전 버전 복구"
fresh
make_jar "$A" "GOOD-A"; run_deploy "$A" "$(sha_of "$A")"
OLD_TARGET="$(current_name)"
make_jar "$B" "NOT_ACTIVE-B"
run_deploy "$B" "$(sha_of "$B")"
check "종료 코드 2" $([ "$RC" -eq 2 ]; echo $?)
check "링크가 이전 버전으로 되돌아감" $([ "$(current_name)" = "$OLD_TARGET" ]; echo $?)

echo "== 3. 이전 버전 재시작도 실패 → 복구 실패 로그"
fresh
make_jar "$A" "GOOD-A"; run_deploy "$A" "$(sha_of "$A")"
# 이전 버전을 손상 상황으로 만든다 (재시작 실패 표식 추가)
echo "RESTART_FAIL" >> "$(readlink -f "$BUSAN_APP_DIR/current.jar")"
make_jar "$B" "HEALTH_FAIL-B"
run_deploy "$B" "$(sha_of "$B")"
check "종료 코드 3 (복구도 실패)" $([ "$RC" -eq 3 ]; echo $?)
check "로그에 복구 실패와 수동 확인 안내" $(grep -q "이전 버전 복구도 실패했습니다" "$WORK/out.log" && grep -q "즉시 확인하세요" "$WORK/out.log"; echo $?)

echo "== 4. 최초 배포 실패 → 이전 버전 없음을 명확히 보고"
fresh
make_jar "$DD" "HEALTH_FAIL-D"
run_deploy "$DD" "$(sha_of "$DD")"
check "종료 코드 4" $([ "$RC" -eq 4 ]; echo $?)
check "이전 JAR가 없다고 보고" $(grep -q "이전 JAR가 없어 자동 복구할 수 없습니다" "$WORK/out.log"; echo $?)

echo "== 4b. 최초 배포의 재시작 명령 실패도 같은 방식으로 보고"
fresh
make_jar "$DD" "RESTART_FAIL-D"
run_deploy "$DD" "$(sha_of "$DD")"
check "종료 코드 4" $([ "$RC" -eq 4 ]; echo $?)

echo "== 4c. 최초 배포 성공"
fresh
make_jar "$DD" "GOOD-D"
run_deploy "$DD" "$(sha_of "$DD")"
check "종료 코드 0" $([ "$RC" -eq 0 ]; echo $?)

echo "== 5. rollback.sh: 재시작 명령 실패를 명확히 보고"
fresh
make_jar "$A" "GOOD-A"; run_deploy "$A" "$(sha_of "$A")"
make_jar "$B" "RESTART_FAIL-B"
cp "$FAKE_STORE/$B.jar" "$BUSAN_APP_DIR/releases/20990101-000000-bbbbbbb.jar"
bash "$ROLLBACK" "$BUSAN_APP_DIR/releases/20990101-000000-bbbbbbb.jar" > "$WORK/rb.log" 2>&1
RBRC=$?
check "재시작 실패 시 종료 코드 1" $([ "$RBRC" -eq 1 ]; echo $?)
check "재시작 실패 메시지 출력" $(grep -q "systemctl restart 명령이 실패" "$WORK/rb.log"; echo $?)

echo "== 5b. rollback.sh: 정상 롤백"
fresh
make_jar "$A" "GOOD-A"; run_deploy "$A" "$(sha_of "$A")"
make_jar "$B" "GOOD-B"; run_deploy "$B" "$(sha_of "$B")"
bash "$ROLLBACK" > "$WORK/rb.log" 2>&1
RBRC=$?
check "인자 없는 롤백 종료 코드 0" $([ "$RBRC" -eq 0 ]; echo $?)
check "직전 버전(A)으로 되돌아감" $(grep -q "GOOD-A" "$(readlink -f "$BUSAN_APP_DIR/current.jar")"; echo $?)

echo
echo "결과: 통과 $PASS, 실패 $FAIL"
[ "$FAIL" -eq 0 ]
