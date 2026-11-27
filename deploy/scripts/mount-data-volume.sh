#!/usr/bin/env bash
# 영속 EBS 볼륨을 /data 에 마운트하고 부팅 시 자동 마운트를 설정한다 (root). docs/DEPLOYMENT.md 4절 참고.
# 사용법: mount-data-volume.sh <장치 경로, 예: /dev/nvme1n1> [--format-new-volume]
#
# 파일 시스템 분기 (기존 데이터 보호가 최우선):
#   - 서명이 없는 장치: --format-new-volume 을 명시했을 때만 ext4로 만든다. 없으면 중단한다.
#   - ext4가 있는 장치: 포맷하지 않고 마운트한다. --format-new-volume 은 무시한다.
#   - 그 밖의 파일 시스템(xfs, btrfs 등): 포맷하지 않고, fstab도 쓰지 않고 중단한다.
#     앱과 MySQL 설정은 ext4를 전제로 한다. 다른 파일 시스템이 필요하면 사람이 판단한다.
# fstab은 UUID 기반이며 nofail을 붙인다. 볼륨이 없어도 부팅은 멈추지 않는다 (MySQL 유닛은 RequiresMountsFor로 기다린다).
#
# 함수 모음은 source 해서 테스트할 수 있다. 직접 실행할 때만 main을 호출한다.
set -euo pipefail

MOUNT_POINT="${BUSAN_MOUNT_POINT:-/data}"
FSTAB_PATH="${BUSAN_FSTAB:-/etc/fstab}"
REQUIRED_FS=ext4

# 장치의 기존 파일 시스템 종류를 돌려준다. 서명이 없으면 빈 문자열.
detect_fs_type() {
  blkid -p -o value -s TYPE "$1" 2>/dev/null || true
}

# 판단 결과를 출력한다. 종료 코드: 0 마운트만, 10 새로 포맷, 11 중단(포맷 옵션 없음), 12 중단(지원하지 않는 파일 시스템)
# 인자: 기존 파일 시스템 종류(없으면 빈 문자열), 포맷 옵션("--format-new-volume" 또는 빈 문자열)
decide_fs_action() {
  local existing="$1" format_flag="$2"
  if [ -z "$existing" ]; then
    if [ "$format_flag" = "--format-new-volume" ]; then
      echo "format"; return 10
    fi
    echo "refuse-no-flag"; return 11
  fi
  if [ "$existing" = "$REQUIRED_FS" ]; then
    echo "mount-only"; return 0
  fi
  echo "refuse-unsupported:$existing"; return 12
}

# 마운트 지점과 UUID로 fstab 한 줄을 만든다.
build_fstab_line() {
  echo "UUID=$1 $MOUNT_POINT $REQUIRED_FS defaults,nofail 0 2"
}

main() {
  [ "$(id -u)" -eq 0 ] || { echo "root로 실행해야 합니다." >&2; exit 1; }
  local device="${1:-}" format_flag="${2:-}"
  [ -n "$device" ] || { echo "사용법: $0 <장치 경로> [--format-new-volume]" >&2; exit 1; }
  [ -b "$device" ] || { echo "블록 장치가 아닙니다: $device" >&2; exit 1; }

  local existing action rc
  existing="$(detect_fs_type "$device")"
  set +e
  action="$(decide_fs_action "$existing" "$format_flag")"
  rc=$?
  set -e

  case "$rc" in
    0) echo "기존 파일 시스템: $existing. 포맷하지 않고 마운트합니다." ;;
    10)
      echo "경고: 서명이 없는 새 볼륨을 ext4로 만듭니다: $device"
      mkfs.ext4 -L busan-data "$device"
      ;;
    11)
      echo "파일 시스템이 없습니다. 새 볼륨이면 --format-new-volume 을 명시하세요. 기존 데이터가 있는 볼륨이라면 중단하세요." >&2
      exit 1 ;;
    12)
      echo "중단: 기존 파일 시스템이 ext4가 아닙니다 (${existing}). fstab을 쓰지 않았고 포맷하지 않았습니다. 데이터를 확인한 뒤 직접 결정하세요." >&2
      exit 1 ;;
    *) echo "알 수 없는 판단 결과: $action" >&2; exit 1 ;;
  esac

  local uuid
  uuid="$(blkid -p -o value -s UUID "$device")"
  [ -n "$uuid" ] || { echo "UUID를 읽을 수 없습니다." >&2; exit 1; }

  mkdir -p "$MOUNT_POINT"
  local fstab_line
  fstab_line="$(build_fstab_line "$uuid")"
  if ! grep -q "UUID=${uuid} " "$FSTAB_PATH"; then
    cp "$FSTAB_PATH" "${FSTAB_PATH}.bak.$(date +%Y%m%d%H%M%S)"
    echo "$fstab_line" >> "$FSTAB_PATH"
    echo "fstab 추가: $fstab_line"
  fi

  mountpoint -q "$MOUNT_POINT" || mount "$MOUNT_POINT"
  systemctl daemon-reload
  mount -a
  mountpoint -q "$MOUNT_POINT" || { echo "마운트 확인 실패" >&2; exit 1; }

  # 마운트된 실제 파일 시스템도 ext4인지 확인한다.
  local mounted_fs
  mounted_fs="$(findmnt -no FSTYPE "$MOUNT_POINT")"
  [ "$mounted_fs" = "$REQUIRED_FS" ] || { echo "마운트된 파일 시스템이 ext4가 아닙니다: $mounted_fs" >&2; exit 1; }

  if id mysql >/dev/null 2>&1 && [ -d "$MOUNT_POINT/mysql" ]; then
    chown -R mysql:mysql "$MOUNT_POINT/mysql"
    chmod 750 "$MOUNT_POINT/mysql"
  fi
  echo "마운트 완료: $device (UUID=$uuid, $mounted_fs) -> $MOUNT_POINT"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
