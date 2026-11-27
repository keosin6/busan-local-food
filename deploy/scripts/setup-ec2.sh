#!/usr/bin/env bash
# EC2(Ubuntu 24.04 LTS) 초기 설정 스크립트. root로 실행하며 여러 번 실행해도 안전하다(멱등).
# 하는 일: 시간대, 패키지(Nginx, JRE 21, MySQL 8, AWS CLI v2), 서비스 계정, 디렉터리, systemd·Nginx·journald 설정.
# 하지 않는 일: EBS 포맷(mount-data-volume.sh로 따로 한다), MySQL 계정·스키마 생성(docs/DEPLOYMENT.md 4·7절),
#              운영 환경변수 값 작성(/etc/busan-local-food/app.env는 사람이 직접 만든다), 인증서 발급.
set -euo pipefail

[ "$(id -u)" -eq 0 ] || { echo "root로 실행해야 합니다." >&2; exit 1; }
SRC_DIR="$(cd "$(dirname "$0")/.." && pwd)"   # deploy/ 디렉터리

echo "== 시간대 =="
timedatectl set-timezone Asia/Seoul

echo "== 패키지 (MySQL은 기본 데이터 경로에 초기화될 수 있으므로 데이터 경로 변경은 docs/DEPLOYMENT.md 4절에 따라 별도로 한다) =="
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y nginx openjdk-21-jre-headless mysql-server unzip curl ca-certificates logrotate

if ! command -v aws >/dev/null 2>&1; then
  # AWS CLI v2는 공식 설치 파일을 쓴다. 설치 후 버전을 기록한다.
  tmp="$(mktemp -d)"
  curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "$tmp/awscliv2.zip"
  unzip -q "$tmp/awscliv2.zip" -d "$tmp"
  "$tmp/aws/install"
  rm -rf "$tmp"
fi
aws --version

echo "== 서비스 계정과 디렉터리 =="
id busan >/dev/null 2>&1 || useradd --system --home-dir /opt/busan-local-food --shell /usr/sbin/nologin busan
install -d -o root -g root -m 755 /opt/busan-local-food /opt/busan-local-food/bin
install -d -o busan -g busan -m 755 /opt/busan-local-food/releases /opt/busan-local-food/tmp
install -d -o root -g busan -m 750 /etc/busan-local-food
install -d -o root -g root -m 700 /var/backups/busan-local-food /var/backups/busan-local-food/tmp

echo "== 배포 스크립트 설치 =="
install -m 755 "$SRC_DIR/scripts/deploy.sh" /opt/busan-local-food/bin/deploy.sh
install -m 755 "$SRC_DIR/scripts/rollback.sh" /opt/busan-local-food/bin/rollback.sh
install -m 755 "$SRC_DIR/scripts/backup-mysql.sh" /opt/busan-local-food/bin/backup-mysql.sh

echo "== systemd =="
install -m 644 "$SRC_DIR/systemd/busan-local-food.service" /etc/systemd/system/busan-local-food.service
install -m 644 "$SRC_DIR/systemd/busan-mysql-backup.service" /etc/systemd/system/busan-mysql-backup.service
install -m 644 "$SRC_DIR/systemd/busan-mysql-backup.timer" /etc/systemd/system/busan-mysql-backup.timer
systemctl daemon-reload
systemctl enable busan-local-food.service
# 타이머는 활성화만 한다. 백업 설정 파일을 만든 뒤 시작한다 (docs/DEPLOYMENT.md 6절).
systemctl enable busan-mysql-backup.timer

echo "== journald 크기 제한 (로그 무한 증가 방지) =="
install -d -m 755 /etc/systemd/journald.conf.d
cat > /etc/systemd/journald.conf.d/busan.conf <<'CONF'
[Journal]
SystemMaxUse=500M
MaxRetentionSec=30day
CONF
systemctl restart systemd-journald

echo "== Nginx =="
install -m 644 "$SRC_DIR/nginx/busan-local-food.conf" /etc/nginx/sites-available/busan-local-food
ln -sfn /etc/nginx/sites-available/busan-local-food /etc/nginx/sites-enabled/busan-local-food
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable --now nginx
systemctl reload nginx

echo "== 확인 =="
if [ ! -f /etc/busan-local-food/app.env ]; then
  echo "주의: /etc/busan-local-food/app.env 가 없습니다. deploy/env/app.env.example을 참고해 직접 만드세요 (docs/DEPLOYMENT.md 5절)."
fi
echo "설정 완료. 이 스크립트는 EBS 포맷, MySQL 계정 생성, 스키마 적용을 하지 않습니다."
