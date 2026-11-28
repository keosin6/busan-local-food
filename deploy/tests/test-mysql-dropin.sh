#!/usr/bin/env bash
# MySQL EBS 의존성 drop-in 의 내용, 설치 경로, setup-ec2.sh 설치 동작을 검사한다.
# 실제 systemd 부팅 순서는 검사하지 않는다 (EC2에서만 확인 가능, 미검증으로 기록).
# 실행: bash deploy/tests/test-mysql-dropin.sh
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
DROPIN="$HERE/../systemd/mysql.service.d/ebs-data-mount.conf"
SETUP="$HERE/../scripts/setup-ec2.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

PASS=0; FAIL=0
ok()   { PASS=$((PASS + 1)); echo "  ok   - $1"; }
bad()  { FAIL=$((FAIL + 1)); echo "  FAIL - $1"; }
check() { if [ "$2" -eq 0 ]; then ok "$1"; else bad "$1"; fi; }

echo "== drop-in 내용과 설치 경로"
[ -f "$DROPIN" ]; check "drop-in 파일이 deploy/systemd/mysql.service.d/ 에 있다" $?
grep -qx '\[Unit\]' "$DROPIN"; check "[Unit] 섹션이 있다" $?
grep -qx 'RequiresMountsFor=/data/mysql' "$DROPIN"; check "RequiresMountsFor=/data/mysql 이 있다" $?
grep -qx 'AssertPathIsMountPoint=/data' "$DROPIN"; check "AssertPathIsMountPoint=/data 가 있다 (마운트 실패 시 루트 볼륨 기록 방지)" $?
# [Unit] 섹션 밖에 있는 지시어가 없는지 (잘못 배치되면 systemd가 무시한다)
awk '/^\[/{sec=$0} /^[A-Za-z]+=/{ if (sec!="[Unit]") bad=1 } END{exit bad}' "$DROPIN"; check "지시어가 모두 [Unit] 안에 있다" $?
grep -q 'mysql.service.d/ebs-data-mount.conf' "$SETUP"; check "setup-ec2.sh 가 설치 경로를 가리킨다" $?

echo "== setup-ec2.sh 설치 동작"
grep -q 'install -d -m 755 /etc/systemd/system/mysql.service.d' "$SETUP"; check "drop-in 디렉터리를 만든다" $?
grep -q 'install -m 644 "$SRC_DIR/systemd/mysql.service.d/ebs-data-mount.conf" /etc/systemd/system/mysql.service.d/ebs-data-mount.conf' "$SETUP"; check "drop-in 파일을 복사한다" $?
DROP_LINE=$(grep -n 'mysql.service.d/ebs-data-mount.conf" /etc/systemd' "$SETUP" | cut -d: -f1 | head -n 1)
RELOAD_LINE=$(grep -n '^systemctl daemon-reload' "$SETUP" | cut -d: -f1 | head -n 1)
[ -n "$DROP_LINE" ] && [ -n "$RELOAD_LINE" ] && [ "$DROP_LINE" -lt "$RELOAD_LINE" ]; check "drop-in 설치 뒤에 daemon-reload 가 온다" $?
if grep -Eq '/(etc|lib)/systemd/system/mysql\.service( |$)' "$SETUP"; then
  bad "setup-ec2.sh 가 기존 mysql.service 파일을 건드린다"
else
  ok "기존 mysql.service 파일을 건드리지 않는다"
fi

echo "== systemd 구문 (가짜 mysql.service 에 drop-in 을 합쳐 검증)"
if command -v systemd-analyze >/dev/null 2>&1; then
  printf '[Unit]\nDescription=MySQL verify stub\n[Service]\nType=simple\nExecStart=/bin/true\n' > "$WORK/mysql.service"
  cat "$WORK/mysql.service" "$DROPIN" > "$WORK/combined.service"
  systemd-analyze verify "$WORK/combined.service" >"$WORK/verify.log" 2>&1
  check "합친 유닛의 구문 검사 통과" $?
  # 음성 대조: 잘못된 키를 넣으면 경고가 나와야 한다 (검사가 실제로 구문을 본다는 확인)
  printf '[Unit]\nRequiresMountsForTypo=/data/mysql\n' | cat "$WORK/mysql.service" - > "$WORK/bad.service"
  systemd-analyze verify "$WORK/bad.service" >"$WORK/bad.log" 2>&1
  if [ -s "$WORK/bad.log" ]; then ok "잘못된 키는 경고로 검출된다 (검사 대조군)"; else bad "잘못된 키가 검출되지 않았다 (검사 신뢰 불가)"; fi
else
  bad "systemd-analyze 가 없어 구문 검사를 할 수 없다"
fi

echo
echo "결과: 통과 $PASS, 실패 $FAIL"
[ "$FAIL" -eq 0 ]
