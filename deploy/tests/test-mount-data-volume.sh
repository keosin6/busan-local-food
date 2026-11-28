#!/usr/bin/env bash
# mount-data-volume.sh 의 파일 시스템 분기와 fstab 줄 생성 테스트.
# 실제 블록 장치, mkfs, mount는 실행하지 않는다. 판단 함수만 source 해서 검사한다.
# 실행: bash deploy/tests/test-mount-data-volume.sh
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
# 함수 파일이 set -e를 켜므로 테스트에서는 끈다 (종료 코드를 직접 검사한다).
. "$HERE/../scripts/mount-data-volume.sh"   # 직접 실행이 아니므로 main은 호출되지 않는다
set +e

PASS=0; FAIL=0
expect() {  # 설명, 기대 출력, 기대 종료 코드, 실제 함수 호출
  local desc="$1" want_out="$2" want_rc="$3"; shift 3
  local out rc
  out="$("$@")"; rc=$?
  if [ "$out" = "$want_out" ] && [ "$rc" -eq "$want_rc" ]; then
    PASS=$((PASS + 1)); echo "  ok   - $desc"
  else
    FAIL=$((FAIL + 1)); echo "  FAIL - $desc (출력='$out' 종료=$rc, 기대='$want_out' 종료=$want_rc)"
  fi
}

echo "== 파일 시스템 분기"
expect "서명 없음 + 포맷 옵션 없음 → 중단 (포맷하지 않음)" "refuse-no-flag" 11 decide_fs_action "" ""
expect "서명 없음 + 포맷 옵션 → ext4 포맷 허용" "format" 10 decide_fs_action "" "--format-new-volume"
expect "ext4 있음 + 옵션 없음 → 마운트만" "mount-only" 0 decide_fs_action "ext4" ""
expect "ext4 있음 + 포맷 옵션 → 포맷하지 않고 마운트만" "mount-only" 0 decide_fs_action "ext4" "--format-new-volume"
expect "xfs 있음 + 포맷 옵션 → 중단 (포맷 금지)" "refuse-unsupported:xfs" 12 decide_fs_action "xfs" "--format-new-volume"
expect "xfs 있음 + 옵션 없음 → 중단" "refuse-unsupported:xfs" 12 decide_fs_action "xfs" ""
expect "btrfs 있음 → 중단" "refuse-unsupported:btrfs" 12 decide_fs_action "btrfs" ""

echo "== fstab 줄"
expect "UUID 기반, ext4, nofail" "UUID=1234-abcd /data ext4 defaults,nofail 0 2" 0 build_fstab_line "1234-abcd"

echo "== 포맷 명령 호출 경로 점검 (정적 검사)"
# mkfs.ext4 는 판단 코드 10 분기(10) ... 11)) 사이에만 있어야 한다.
SCRIPT="$HERE/../scripts/mount-data-volume.sh"
mkfs_line="$(grep -n 'mkfs\.ext4' "$SCRIPT" | cut -d: -f1)"
case10="$(grep -n '^    10)' "$SCRIPT" | cut -d: -f1)"
case11="$(grep -n '^    11)' "$SCRIPT" | cut -d: -f1)"
if [ -n "$mkfs_line" ] && [ "$mkfs_line" -gt "$case10" ] && [ "$mkfs_line" -lt "$case11" ]; then
  PASS=$((PASS + 1)); echo "  ok   - mkfs.ext4는 판단 코드 10 분기 안에서만 호출됨"
else
  FAIL=$((FAIL + 1)); echo "  FAIL - mkfs.ext4 호출 위치 확인 필요 (mkfs=$mkfs_line, 10=$case10, 11=$case11)"
fi

# 조건 결과 도우미: check "설명" 종료값(0이면 통과)
check() {
  if [ "$2" -eq 0 ]; then PASS=$((PASS + 1)); echo "  ok   - $1"
  else FAIL=$((FAIL + 1)); echo "  FAIL - $1"; fi
}

echo "== 마운트 UUID 검증 (가짜 blkid, findmnt, mount, mountpoint, systemctl)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
ST="$TMP/state"; BIN="$TMP/bin"; mkdir -p "$ST" "$BIN"
cat > "$BIN/id" <<'STUB'
#!/usr/bin/env bash
[ "$1" = "-u" ] && echo 0 || exec /usr/bin/id "$@"
STUB
cat > "$BIN/blkid" <<'STUB'
#!/usr/bin/env bash
echo "blkid $*" >> "$ST/log"
case " $* " in
  *" -s TYPE "*) cat "$ST/dev_fs" 2>/dev/null ;;
  *" -s UUID "*) cat "$ST/dev_uuid" 2>/dev/null ;;
esac
exit 0
STUB
cat > "$BIN/findmnt" <<'STUB'
#!/usr/bin/env bash
echo "findmnt $*" >> "$ST/log"
[ -f "$ST/mounted" ] || exit 1
case " $* " in
  *" UUID "*) cat "$ST/mnt_uuid" ;;
  *" FSTYPE "*) cat "$ST/mnt_fs" ;;
esac
exit 0
STUB
cat > "$BIN/mountpoint" <<'STUB'
#!/usr/bin/env bash
echo "mountpoint $*" >> "$ST/log"
[ -f "$ST/mounted" ]
STUB
cat > "$BIN/mount" <<'STUB'
#!/usr/bin/env bash
echo "mount $*" >> "$ST/log"
# mount -a 는 이미 마운트된 지점에 대해 아무것도 하지 않는다 (실제 동작과 같게).
[ "$1" = "-a" ] && exit 0
mode=$(cat "$ST/mount_mode" 2>/dev/null || echo ok)
if [ "$mode" = "fail" ]; then exit 32; fi
touch "$ST/mounted"
case "$mode" in
  wrong) echo "WRONG-UUID" > "$ST/mnt_uuid"; cat "$ST/dev_fs" > "$ST/mnt_fs" ;;
  xfs)   cat "$ST/dev_uuid" > "$ST/mnt_uuid"; echo xfs > "$ST/mnt_fs" ;;
  *)     cat "$ST/dev_uuid" > "$ST/mnt_uuid"; cat "$ST/dev_fs" > "$ST/mnt_fs" ;;
esac
exit 0
STUB
cat > "$BIN/mkfs.ext4" <<'STUB'
#!/usr/bin/env bash
echo "mkfs.ext4 $*" >> "$ST/log"
echo ext4 > "$ST/dev_fs"; cat "$ST/new_uuid" > "$ST/dev_uuid"
exit 0
STUB
for c in systemctl chown chmod; do
  printf '#!/usr/bin/env bash\necho "%s $*" >> "$ST/log"\nexit 0\n' "$c" > "$BIN/$c"
done
chmod +x "$BIN"/*

# 실행 도우미: 상태를 세팅하고 main 을 하위 셸에서 돌린다. 블록 장치 검사만 가짜로 통과시킨다.
reset_state() {  # dev_uuid, dev_fs, mounted(0/1), mnt_uuid, mnt_fs, mount_mode
  rm -f "$ST"/*
  echo "$1" > "$ST/dev_uuid"; echo "$2" > "$ST/dev_fs"
  [ "$3" = "1" ] && { touch "$ST/mounted"; echo "$4" > "$ST/mnt_uuid"; echo "$5" > "$ST/mnt_fs"; }
  echo "$6" > "$ST/mount_mode"
  echo "NEW-UUID-0001" > "$ST/new_uuid"
  printf 'UUID=ROOT-UUID / ext4 defaults 0 1\n' > "$ST/fstab"
  : > "$ST/log"
}
run_main() {  # 추가 인자는 main 에 그대로 넘긴다. 결과는 RC, OUT 에 남긴다.
  OUT="$(PATH="$BIN:$PATH" ST="$ST" BUSAN_MOUNT_POINT=/data BUSAN_FSTAB="$ST/fstab" bash -c '
    . "'"$HERE"'/../scripts/mount-data-volume.sh"
    set +e
    is_block_device() { return 0; }
    main "$@"
  ' _ /dev/fake-volume "$@" 2>&1)"
  RC=$?
}
fstab_has() { grep -q "$1" "$ST/fstab"; }
mount_called() { grep -q '^mount ' "$ST/log"; }

echo "  -- 올바른 UUID가 이미 마운트된 경우"
reset_state GOOD-UUID ext4 1 GOOD-UUID ext4 ok
run_main
check "종료 코드 0" $([ "$RC" -eq 0 ]; echo $?)
check "이미 같은 볼륨이라고 보고" $(echo "$OUT" | grep -q "이미 같은 볼륨이 마운트되어 있습니다"; echo $?)
check "이미 마운트된 경우 mount를 다시 호출하지 않음" $(mount_called; [ $? -ne 0 ]; echo $?)
check "fstab에 장치 UUID 항목이 있음" $(fstab_has "UUID=GOOD-UUID /data ext4"; echo $?)

echo "  -- 다른 UUID의 ext4 볼륨이 /data 에 마운트된 경우"
reset_state GOOD-UUID ext4 1 OTHER-UUID ext4 ok
before=$(cksum < "$ST/fstab")
run_main
check "종료 코드 1" $([ "$RC" -eq 1 ]; echo $?)
check "중단 사유에 다른 마운트 안내" $(echo "$OUT" | grep -q "다른 마운트가 있습니다"; echo $?)
check "fstab을 바꾸지 않음" $([ "$before" = "$(cksum < "$ST/fstab")" ]; echo $?)
check "기존 마운트를 건드리지 않음 (mount 호출 없음)" $(mount_called; [ $? -ne 0 ]; echo $?)

echo "  -- /data 가 마운트되지 않은 경우: 마운트 후 UUID 검증"
reset_state GOOD-UUID ext4 0 "" "" ok
run_main
check "종료 코드 0" $([ "$RC" -eq 0 ]; echo $?)
check "mount 를 호출함" $(mount_called; echo $?)
check "fstab에 항목 추가됨" $(fstab_has "UUID=GOOD-UUID /data ext4"; echo $?)
check "새로 마운트한 뒤 검증했다고 보고" $(echo "$OUT" | grep -q "새로 마운트했고 검증했습니다"; echo $?)

echo "  -- 마운트 후 실제 UUID가 장치와 다른 경우 실패"
reset_state GOOD-UUID ext4 0 "" "" wrong
run_main
check "종료 코드 1" $([ "$RC" -eq 1 ]; echo $?)
check "검증 실패 사유 출력" $(echo "$OUT" | grep -q "검증 실패\|마운트 후 검증에 실패"; echo $?)

echo "  -- 마운트 후 파일 시스템이 ext4가 아닌 경우 실패"
reset_state GOOD-UUID ext4 0 "" "" xfs
run_main
check "종료 코드 1" $([ "$RC" -eq 1 ]; echo $?)

echo "  -- 마운트 명령 자체가 실패한 경우"
reset_state GOOD-UUID ext4 0 "" "" fail
run_main
check "종료 코드가 0이 아님" $([ "$RC" -ne 0 ]; echo $?)

echo "  -- 신규 볼륨 포맷 후 마운트와 검증"
reset_state "" "" 0 "" "" ok
run_main --format-new-volume
check "mkfs.ext4 호출됨" $(grep -q '^mkfs.ext4 ' "$ST/log"; echo $?)
check "포맷 후 새 UUID로 fstab 추가" $(fstab_has "UUID=NEW-UUID-0001 /data ext4"; echo $?)
check "포맷 후 마운트 검증 통과 (종료 코드 0)" $([ "$RC" -eq 0 ]; echo $?)

echo "  -- 서명 없는 볼륨에 포맷 옵션이 없으면 포맷하지 않음"
reset_state "" "" 0 "" "" ok
run_main
check "종료 코드 1" $([ "$RC" -eq 1 ]; echo $?)
check "mkfs.ext4 호출 없음" $(grep -q '^mkfs' "$ST/log"; [ $? -ne 0 ]; echo $?)

echo
echo "결과: 통과 $PASS, 실패 $FAIL"
[ "$FAIL" -eq 0 ]
