#!/usr/bin/env bash
# 영속 EBS 볼륨을 /data 에 마운트하고 부팅 시 자동 마운트를 설정한다 (root). docs/DEPLOYMENT.md 4절 참고.
# 사용법: mount-data-volume.sh <장치 경로, 예: /dev/nvme1n1> [--format-new-volume]
#   - 파일 시스템 서명이 이미 있으면 절대 포맷하지 않는다. 기존 데이터를 보존한다.
#   - 서명이 없는 새 볼륨만 --format-new-volume 을 명시했을 때 ext4로 만든다.
#   - fstab은 UUID로 등록하고 nofail을 붙인다. 볼륨이 없어도 부팅은 멈추지 않는다 (MySQL은 RequiresMountsFor로 기다린다).
set -euo pipefail

DEVICE="${1:-}"
FORMAT_FLAG="${2:-}"
MOUNT_POINT=/data

[ "$(id -u)" -eq 0 ] || { echo "root로 실행해야 합니다." >&2; exit 1; }
[ -n "$DEVICE" ] || { echo "사용법: $0 <장치 경로> [--format-new-volume]" >&2; exit 1; }
[ -b "$DEVICE" ] || { echo "블록 장치가 아닙니다: $DEVICE" >&2; exit 1; }

EXISTING_FS="$(blkid -p -o value -s TYPE "$DEVICE" || true)"
if [ -z "$EXISTING_FS" ]; then
  if [ "$FORMAT_FLAG" != "--format-new-volume" ]; then
    echo "파일 시스템이 없습니다. 새 볼륨이면 --format-new-volume 을 명시하세요. 기존 데이터가 있는 볼륨이라면 중단하세요." >&2
    exit 1
  fi
  echo "경고: 서명이 없는 새 볼륨을 ext4로 만듭니다: $DEVICE"
  mkfs.ext4 -L busan-data "$DEVICE"
elif [ "$FORMAT_FLAG" = "--format-new-volume" ]; then
  echo "이미 파일 시스템($EXISTING_FS)이 있어 포맷하지 않습니다. --format-new-volume 옵션을 무시합니다." >&2
fi

UUID="$(blkid -p -o value -s UUID "$DEVICE")"
[ -n "$UUID" ] || { echo "UUID를 읽을 수 없습니다." >&2; exit 1; }

mkdir -p "$MOUNT_POINT"
FSTAB_LINE="UUID=${UUID} ${MOUNT_POINT} ext4 defaults,nofail 0 2"
if ! grep -q "UUID=${UUID} " /etc/fstab; then
  cp /etc/fstab "/etc/fstab.bak.$(date +%Y%m%d%H%M%S)"
  echo "$FSTAB_LINE" >> /etc/fstab
  echo "fstab 추가: $FSTAB_LINE"
fi

mountpoint -q "$MOUNT_POINT" || mount "$MOUNT_POINT"
systemctl daemon-reload
# 'mount -a'로 fstab 문법과 마운트 가능 여부를 확인한다.
mount -a
mountpoint -q "$MOUNT_POINT" || { echo "마운트 확인 실패" >&2; exit 1; }

if id mysql >/dev/null 2>&1 && [ -d "$MOUNT_POINT/mysql" ]; then
  chown -R mysql:mysql "$MOUNT_POINT/mysql"
  chmod 750 "$MOUNT_POINT/mysql"
fi
echo "마운트 완료: $DEVICE (UUID=$UUID) -> $MOUNT_POINT"
