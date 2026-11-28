#!/usr/bin/env bash
# .github/workflows/deploy.yml 의 "배포 결과 기다리기" 단계 스크립트를 가짜 aws·sleep·date 로 실행한다.
# 실제 AWS, SSM, GitHub 를 쓰지 않는다. 실행: bash deploy/tests/test-ssm-wait.sh
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
WF="$HERE/../../.github/workflows/deploy.yml"

# 워크플로에서 해당 단계의 run 스크립트를 꺼낸다.
python3 - "$WF" "$WORK/wait.sh" <<'PY'
import sys, yaml
wf = yaml.safe_load(open(sys.argv[1], encoding='utf-8'))
steps = wf['jobs']['test-build-deploy']['steps']
step = next(s for s in steps if s.get('name', '').startswith('배포 결과 기다리기'))
open(sys.argv[2], 'w', encoding='utf-8').write(step['run'])
PY

mkdir -p "$WORK/bin"
# 가짜 aws: 상태 목록 파일에서 한 줄씩 꺼내 get-command-invocation 의 Status 를 돌려준다.
cat > "$WORK/bin/aws" <<'STUB'
#!/usr/bin/env bash
case " $* " in
  *" --query Status "*)
    line=$(head -n 1 "$STATUS_FILE"); sed -i '1d' "$STATUS_FILE"
    [ -z "$line" ] && line="Pending"
    echo "$line"; exit 0 ;;
  *StandardOutputContent*)
    echo "Success	deploy-output	"; exit 0 ;;
esac
exit 0
STUB
# 가짜 sleep: 실제로 기다리지 않는다.
cat > "$WORK/bin/sleep" <<'STUB'
#!/usr/bin/env bash
exit 0
STUB
# 가짜 date: 호출마다 100초씩 지난 것처럼 돌려준다. +%s 형식만 지원한다.
cat > "$WORK/bin/date" <<'STUB'
#!/usr/bin/env bash
now=$(cat "$CLOCK_FILE"); echo $((now + 100)) > "$CLOCK_FILE"; echo "$now"
STUB
chmod +x "$WORK/bin/"*

PASS=0; FAIL=0
run_case() {  # 설명, 기대 종료 코드, 기대 문자열, 상태 목록(줄바꿈 구분)
  local desc="$1" want_rc="$2" want_text="$3" statuses="$4"
  printf '%b' "$statuses" > "$WORK/status.txt"
  echo 1000 > "$WORK/clock.txt"
  out=$(STATUS_FILE="$WORK/status.txt" CLOCK_FILE="$WORK/clock.txt" PATH="$WORK/bin:$PATH" \
        INSTANCE_ID=i-test CMD_ID=cmd-test bash "$WORK/wait.sh" 2>&1)
  rc=$?
  if [ "$rc" -eq "$want_rc" ] && printf '%s' "$out" | grep -q "$want_text"; then
    PASS=$((PASS + 1)); echo "  ok   - $desc (종료 $rc)"
  else
    FAIL=$((FAIL + 1)); echo "  FAIL - $desc (종료 $rc, 기대 $want_rc, 출력: $out)"
  fi
}

echo "== SSM 대기 단계"
run_case "Pending, InProgress 를 거쳐 Success" 0 "배포 명령 성공" "Pending\nInProgress\nSuccess\n"
run_case "Failed 는 실패로 끝난다" 1 "배포 명령 실패" "InProgress\nFailed\n"
run_case "TimedOut 은 실패로 끝난다" 1 "배포 명령 실패" "TimedOut\n"
run_case "한도를 넘기면 실패하고 명령 ID를 안내한다" 1 "대기 한도" "InProgress\nInProgress\nInProgress\nInProgress\n"

echo
echo "== 한도 계산 확인"
grep -q "DEADLINE=\$(( \$(date +%s) + 1080 ))" "$WORK/wait.sh" && { PASS=$((PASS + 1)); echo "  ok   - 대기 한도 1080초"; } || { FAIL=$((FAIL + 1)); echo "  FAIL - 대기 한도 1080초 확인 실패"; }
grep -q -- "--timeout-seconds 900" "$WF" && { PASS=$((PASS + 1)); echo "  ok   - SSM 명령 제한 900초"; } || { FAIL=$((FAIL + 1)); echo "  FAIL - SSM 명령 제한 확인 실패"; }

echo
echo "결과: 통과 $PASS, 실패 $FAIL"
[ "$FAIL" -eq 0 ]
