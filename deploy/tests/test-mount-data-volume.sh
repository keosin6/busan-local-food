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

echo
echo "결과: 통과 $PASS, 실패 $FAIL"
[ "$FAIL" -eq 0 ]
