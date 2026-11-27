# 운영 배포 가이드 (Stage 11)

이 문서는 EC2 운영 배포, MySQL 영속 저장소, DB 백업·복구, GitHub Actions 배포, 롤백 절차를 정리한다.
배포 파일은 `deploy/`와 `.github/workflows/deploy.yml`에 있다.

> **상태: 파일과 문서만 준비됨 (미검증).** 실제 AWS 리소스, EC2, EBS, MySQL 설치, S3, IAM, OIDC, SSM, 백업·복구, 브라우저 접속, Google OAuth 운영 콜백, HTTPS는 아직 확인하지 않았다. 8절과 14절의 미검증 항목을 먼저 본다.

---

## 1. 범위와 원칙

- 이번 작업은 배포 기반 구축이다. AWS 계정에 리소스를 만들거나 운영 서버에 접속하는 작업은 포함하지 않는다.
- 인증 구조(화면 Session, API JWT Stateless), Google OAuth, DB 구조와 SQL 001~008, 기능 로직은 바꾸지 않았다.
- 장기 AWS 액세스 키는 GitHub Secrets에 저장하지 않는다. EC2는 인스턴스 역할, GitHub Actions는 OIDC를 쓴다.
- 운영 비밀 값(DB 비밀번호, JWT 키, Google 클라이언트 값, OpenRouter 키)은 저장소에 두지 않는다.

## 2. 확정된 운영 구성

| 항목 | 결정 |
|---|---|
| 서버 | AWS EC2, Ubuntu 24.04 LTS, t3.medium 권장 |
| DB | 같은 EC2 내부 MySQL 8 (Ubuntu에 직접 설치) |
| DB 데이터 | 별도 EBS 볼륨에 영속 저장 (`/data/mysql`) |
| DB 포트 | 외부 공개 금지 (3306은 보안 그룹에서 열지 않음) |
| 웹 진입 | Nginx (80, 나중에 443) |
| 애플리케이션 | Spring Boot JAR, 내부 8080 (127.0.0.1 바인딩) |
| 이미지 | 기존 AWS S3 (`AWS_S3_BUCKET`) |
| 백업 | 매일 1회 `mysqldump` → 별도 백업 S3 버킷 (`BACKUP_S3_BUCKET`) |
| CI/CD | GitHub Actions, AWS OIDC, SSM Run Command로 EC2 배포 |
| 도메인·HTTPS | 나중에 연결. Let's Encrypt는 도메인 연결 단계에서 적용 |
| RDS | 추후 이전 가능하도록 DB 접속 정보는 환경변수(`DB_URL` 등)로만 관리 |

## 3. 저장소 파일 목록

| 경로 | 용도 |
|---|---|
| `deploy/env/app.env.example` | 운영 환경변수 목록과 예시 (실제 값 없음) |
| `deploy/systemd/busan-local-food.service` | Spring Boot systemd 서비스 |
| `deploy/systemd/busan-mysql-backup.service`, `.timer` | 일일 백업 실행 단위 (KST 03:20) |
| `deploy/nginx/busan-local-food.conf` | Nginx reverse proxy (HTTP 단계, HTTPS 블록은 주석) |
| `deploy/mysql/busan-mysql-datadir.cnf` | 데이터 디렉터리를 `/data/mysql`로 옮기는 설정 |
| `deploy/mysql/bootstrap-accounts.sql.example` | DB·계정 생성 예시 (CHANGE_ME 치환 후 1회 실행) |
| `deploy/backup/s3-lifecycle.json` | 백업 버킷 수명 규칙 (daily 7일, weekly 28일) |
| `deploy/scripts/setup-ec2.sh` | EC2 초기 설정 (멱등) |
| `deploy/scripts/mount-data-volume.sh` | EBS를 `/data`에 UUID로 마운트 (기존 파일 시스템은 포맷하지 않음) |
| `deploy/scripts/deploy.sh` | 아티팩트 검증, 링크 교체, 재시작, 헬스 체크, 실패 시 자동 롤백 |
| `deploy/scripts/rollback.sh` | 수동 롤백 |
| `deploy/scripts/backup-mysql.sh` | 백업 덤프, gzip, S3 업로드·크기 검증 |
| `.github/workflows/deploy.yml` | `main` 푸시 시 테스트·빌드·배포 |

EC2 설치 경로와 역할:

| 경로 | 내용 |
|---|---|
| `/opt/busan-local-food/current.jar` | 현재 실행 JAR (심볼릭 링크) |
| `/opt/busan-local-food/releases/` | 최근 5개 JAR 보관 |
| `/opt/busan-local-food/bin/` | 배포·롤백·백업 스크립트 |
| `/etc/busan-local-food/app.env` | 운영 환경변수 (root:busan, 640) |
| `/etc/busan-local-food/deploy.env` | `DEPLOY_ARTIFACT_BUCKET=...` (root:busan, 640) |
| `/etc/busan-local-food/backup.env` | `BACKUP_S3_BUCKET=...` (root, 600) |
| `/etc/busan-local-food/mysql-backup.cnf` | 백업 전용 DB 계정 (root, 600) |
| `/data/mysql` | MySQL 데이터 (EBS) |
| `/var/backups/busan-local-food/tmp` | 백업 임시 파일 (root, 700) |

## 4. EC2와 EBS, MySQL 설치 절차

### 4.1 AWS에서 사용자가 직접 할 일 (이 저장소는 생성하지 않는다)

1. VPC 안에 EC2 인스턴스를 만든다. Ubuntu 24.04 LTS, t3.medium, 루트 볼륨 gp3.
2. 같은 가용 영역에 데이터용 EBS 볼륨을 만들어 인스턴스에 붙인다. 종료 시 삭제 옵션은 끈다.
3. 인스턴스 프로파일(IAM 역할)을 붙인다 (10절). SSM 사용을 위해 `AmazonSSMManagedInstanceCore` 정책을 포함한다.
4. 보안 그룹 설정 (아래 표). 22번은 가능하면 열지 않고 SSM Session Manager로 접속한다. 꼭 SSH가 필요하면 사용자 IP 한 개로만 제한한다.

| 방향 | 포트 | 출처 | 목적 |
|---|---|---|---|
| 인바운드 | 80 | 0.0.0.0/0 | 웹 (HTTP, 임시) |
| 인바운드 | 443 | 0.0.0.0/0 | 웹 (HTTPS, 도메인 연결 후) |
| 인바운드 | 22 | 사용자 IP/32 | 필요할 때만 (기본은 닫음) |
| 인바운드 | 3306 | 없음 | MySQL 외부 공개 금지 |
| 인바운드 | 8080 | 없음 | 앱 직접 공개 금지 (Nginx만 접근) |

### 4.2 EBS 마운트 (MySQL 설치 전에 먼저)

```bash
# 장치 이름 확인 (볼륨 크기로 구분)
lsblk
# 새 볼륨인 경우에만 포맷 옵션을 준다. 기존 데이터가 있는 볼륨에는 옵션을 주지 않는다.
sudo /opt/busan-local-food/bin/mount-data-volume.sh /dev/nvme1n1 --format-new-volume
# 이미 포맷된 볼륨(재부팅·재연결)이면 옵션 없이 실행한다.
sudo /opt/busan-local-food/bin/mount-data-volume.sh /dev/nvme1n1
```

- 스크립트는 파일 시스템 서명이 있으면 포맷하지 않는다.
- `/etc/fstab`은 `UUID=... /data ext4 defaults,nofail 0 2` 형식이다. `nofail`이므로 볼륨이 없어도 부팅은 멈추지 않으며, MySQL 유닛은 `RequiresMountsFor=/data/mysql`로 마운트를 기다린다.
- 재부팅 후 확인: `findmnt /data` 와 `sudo mount -a`.

> 주의: EBS 볼륨을 분리·삭제·교체하면 DB 데이터가 사라지거나 되돌릴 수 없게 된다. 볼륨 삭제 보호를 켜고, 볼륨 변경 전에는 반드시 최신 백업을 확인한다.

### 4.3 EC2 기본 설정

```bash
# 저장소의 deploy/ 디렉터리를 서버로 가져온 뒤 실행한다 (예: scp 또는 SSM 세션에서 git clone).
sudo bash deploy/scripts/setup-ec2.sh
```

설치되는 것: Nginx, OpenJDK 21 JRE(headless), MySQL 8(Ubuntu 패키지), AWS CLI v2, systemd 유닛, journald 크기 제한(500MB, 30일).
스크립트는 MySQL 데이터 경로를 옮기지 않으며, 계정과 스키마도 만들지 않는다.

### 4.4 MySQL 데이터 디렉터리를 `/data/mysql`로 옮기기 (최초 1회)

Ubuntu 패키지는 `/var/lib/mysql`에 초기화된다. 아래 순서로 옮긴다. 이미 데이터가 있는 서버라면 이 절차를 건너뛰고 기존 경로를 유지한다.

```bash
sudo systemctl stop mysql
sudo install -d -o mysql -g mysql -m 750 /data/mysql
# 빈 디렉터리일 때만 초기화한다. 기존 데이터가 있으면 아래 조건이 실패해 여기서 멈춘다.
if [ -z "$(ls -A /data/mysql)" ]; then
  sudo mysqld --initialize-insecure --datadir=/data/mysql --user=mysql
else
  echo "/data/mysql 에 파일이 있습니다. 초기화하지 않고 중단합니다." >&2
  exit 1
fi
sudo cp deploy/mysql/busan-mysql-datadir.cnf /etc/mysql/mysql.conf.d/99-busan.cnf
```

AppArmor가 `/data/mysql`을 허용하도록 두 곳을 고친다.

```bash
# /etc/apparmor.d/tunables/alias 에 한 줄 추가:
#   alias /var/lib/mysql/ -> /data/mysql/,
# /etc/apparmor.d/local/usr.sbin.mysqld 에 추가:
#   /data/mysql/ r,
#   /data/mysql/** rwk,
sudo apparmor_parser -r /etc/apparmor.d/usr.sbin.mysqld
sudo systemctl start mysql
sudo systemctl enable mysql
```

- `sudo systemctl status mysql`로 기동을 확인하고, `SELECT @@datadir;`이 `/data/mysql/`인지 확인한다.
- 데이터 디렉터리 권한은 `mysql:mysql`, 750이어야 한다.

> 검증 필요: AppArmor 규칙과 `mysqld --initialize-insecure` 경로는 실제 Ubuntu 24.04에서 확인하지 않았다. 실패하면 `journalctl -u mysql`과 `dmesg | grep -i apparmor`로 원인을 찾는다.

### 4.5 DB와 계정 생성 (최초 1회)

1. `deploy/mysql/bootstrap-accounts.sql.example`을 서버의 임시 파일로 복사한다.
2. 모든 `CHANGE_ME_...` 값을 `openssl rand -base64 24`로 만든 값으로 바꾼다. Git에 올리지 않는다.
3. `sudo mysql < /root/bootstrap.sql`로 실행하고, 실행 뒤 파일을 삭제한다 (`shred -u` 권장).

| 계정 | 접속 | 권한 | 용도 |
|---|---|---|---|
| `busan_admin`@`localhost` | 소켓 | 전체 (DB 한정) | 수동 SQL 001~008 적용. 끝나면 비활성화 |
| `busan_app`@`127.0.0.1` | TCP | SELECT, INSERT, UPDATE, DELETE | 애플리케이션. DDL 없음 |
| `busan_backup`@`127.0.0.1` | TCP | SELECT, SHOW VIEW, TRIGGER, EVENT, LOCK TABLES | 백업 전용 |

- 문자셋: `utf8mb4`, 콜레이션 `utf8mb4_0900_ai_ci`. DB 이름은 `busan_local_food`.
- `CREATE DATABASE IF NOT EXISTS`를 쓰므로 기존 DB를 지우지 않는다. 이 저장소에는 DB를 포맷하거나 초기화하는 명령이 없다.

## 5. 운영 환경변수

위치: `/etc/busan-local-food/app.env` (root:busan, 640). 예시는 `deploy/env/app.env.example`.

```bash
sudo install -d -o root -g busan -m 750 /etc/busan-local-food
sudo install -o root -g busan -m 640 /dev/null /etc/busan-local-food/app.env
sudoedit /etc/busan-local-food/app.env      # 예시 파일 내용을 채운다
```

| 변수 | 필수 | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | 필수 | `prod` |
| `DB_URL` | 필수 | `jdbc:mysql://127.0.0.1:3306/busan_local_food?characterEncoding=UTF-8&sslMode=REQUIRED` |
| `DB_USERNAME` | 필수 | `busan_app` |
| `DB_PASSWORD` | 필수 | `busan_app` 계정 비밀번호 |
| `JWT_SECRET` | 필수 | Base64, 디코딩 32바이트 이상 |
| `AUTH_RATE_LIMIT_HMAC_SECRET` | 필수 | Base64, 32바이트 이상. `JWT_SECRET`과 달라야 함 |
| `GOOGLE_CLIENT_ID` | 필수 (prod) | 둘 다 있어야 함. 한쪽만 있으면 기동 실패 |
| `GOOGLE_CLIENT_SECRET` | 필수 (prod) | 위와 같음 |
| `AWS_REGION` | 필수 | 이미지 버킷의 리전 |
| `AWS_S3_BUCKET` | 필수 | 기존 이미지 버킷 |
| `APP_SECURITY_TRUSTED_PROXIES` | 권장 | `127.0.0.1`. Nginx 뒤에서 실제 클라이언트 IP를 쓰기 위함 (아래 참고) |
| `OPENROUTER_API_KEY` | 선택 | 없으면 AI 요약·추천은 UNAVAILABLE |
| `OPENROUTER_MODEL` | 선택 | 모델 ID는 T10에서 확정. 없으면 UNAVAILABLE |
| `JWT_ACCESS_TOKEN_EXPIRY_SECONDS` | 선택 | 기본 1800 |
| `JWT_REFRESH_TOKEN_EXPIRY_SECONDS` | 선택 | 기본 1209600 |

- 키 생성: `openssl rand -base64 32` (JWT_SECRET, AUTH_RATE_LIMIT_HMAC_SECRET 각각 따로).
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`는 넣지 않는다. 인스턴스 역할을 쓴다.
- `APP_SECURITY_TRUSTED_PROXIES`를 비워 두면 Nginx 뒤의 모든 요청이 `127.0.0.1`로 집계되어 요청 제한이 전체 사용자에게 걸린다. 그래서 `127.0.0.1`을 넣는다. 신뢰 프록시 규칙 자체는 바꾸지 않았다.
- 운영 보안 검증을 약화하지 않았다. Google 값이 없으면 운영 프로필은 기동하지 않는다 (8절).

## 6. DB 백업과 복구 (T4)

### 6.1 정책

| 항목 | 내용 |
|---|---|
| 주기 | 매일 KST 03:20 (systemd timer, Persistent=true) |
| 보관 | 일일 백업 7일, 주간 백업 4주(28일) |
| 주간 백업 | 일요일 KST 실행 때 `weekly/`에 한 부 더 올림 |
| 저장 위치 | 백업 전용 S3 버킷 (`BACKUP_S3_BUCKET`), 이미지 버킷과 분리 |
| 암호화 | 업로드 시 `--sse AES256` + 버킷 기본 암호화 |
| 공개 접근 | 차단 (아래 명령으로 4개 설정 모두 켬) |
| 형식 | `mysqldump` 출력을 gzip으로 압축, 파일명 `busan_local_food-YYYY-MM-DD_HHMMSS.sql.gz` |
| 경로 | `daily/YYYY/MM/DD/`, `weekly/YYYY/MM/DD/` |

### 6.2 설정 (최초 1회)

```bash
# 1) 백업 버킷 생성 (사용자가 AWS에서 직접). 공개 접근 차단과 기본 암호화(SSE-S3)를 켠다.
aws s3api put-public-access-block --bucket <BACKUP_BUCKET> --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
aws s3api put-bucket-encryption --bucket <BACKUP_BUCKET> --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

# 2) 수명 규칙 적용
aws s3api put-bucket-lifecycle-configuration --bucket <BACKUP_BUCKET> \
  --lifecycle-configuration file://deploy/backup/s3-lifecycle.json

# 3) 백업 전용 DB 계정의 설정 파일 (root 600). 비밀번호는 명령행에 넣지 않는다.
sudo install -o root -g root -m 600 /dev/null /etc/busan-local-food/mysql-backup.cnf
sudoedit /etc/busan-local-food/mysql-backup.cnf
#   [client]
#   user=busan_backup
#   password=<busan_backup 비밀번호>
#   host=127.0.0.1

# 4) 백업 대상 버킷 지정
echo 'BACKUP_S3_BUCKET=<BACKUP_BUCKET>' | sudo tee /etc/busan-local-food/backup.env >/dev/null
sudo chmod 600 /etc/busan-local-food/backup.env

# 5) 수동 1회 실행으로 확인한 뒤 타이머 시작
sudo /opt/busan-local-food/bin/backup-mysql.sh
sudo systemctl start busan-mysql-backup.timer
systemctl list-timers busan-mysql-backup.timer
```

### 6.3 스크립트 동작

1. `mysqldump --defaults-extra-file`로 읽는다. `--single-transaction`으로 InnoDB 일관 스냅샷을 쓰므로 쓰기를 막지 않는다.
2. 결과를 gzip으로 임시 디렉터리(`/var/backups/busan-local-food/tmp`, 700)에 쓰고 `gzip -t`로 검사한다.
3. S3에 올린 뒤 `head-object`로 크기가 같은지 확인한다.
4. 확인되면 로컬 임시 파일을 지우고 종료 코드 0으로 끝낸다.
5. 실패하면 로컬 파일을 지우지 않고 종료 코드 1로 끝낸다. 덤프 자체가 실패한 부분 파일만 지운다.

로그 확인: `journalctl -t busan-mysql-backup --since today`, 실패 상태: `systemctl status busan-mysql-backup.service`.

### 6.4 실패 파일 정리 (수동)

업로드가 실패하면 `/var/backups/busan-local-food/tmp/`에 파일이 남는다. 원인을 확인하고 재업로드하거나, 다음 백업이 성공한 뒤 직접 지운다. 자동으로 지우지 않는다.

### 6.5 복구 절차 (운영 DB에 직접 하지 않는다)

복구는 별도 테스트 DB에서 먼저 한다.

```bash
# 1) 최신 백업 확인
aws s3 ls s3://<BACKUP_BUCKET>/daily/ --recursive | sort | tail -n 3
# 2) 내려받아 무결성 확인
aws s3 cp s3://<BACKUP_BUCKET>/daily/YYYY/MM/DD/<파일>.sql.gz /var/tmp/restore/
gzip -t /var/tmp/restore/<파일>.sql.gz
# 3) 테스트 DB에 복원 (운영 DB 이름과 다른 이름을 쓴다)
sudo mysql -e 'CREATE DATABASE busan_restore_check CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;'
gunzip -c /var/tmp/restore/<파일>.sql.gz | sudo mysql busan_restore_check
# 4) 검증: 테이블 수, 주요 테이블 행 수를 운영 기준 값과 비교
sudo mysql busan_restore_check -e 'SHOW TABLES; SELECT COUNT(*) FROM member; SELECT COUNT(*) FROM restaurant; SELECT COUNT(*) FROM review;'
# 5) 검증이 끝나면 테스트 DB를 삭제한다
sudo mysql -e 'DROP DATABASE busan_restore_check;'
```

운영 DB로 되돌려야 하는 실제 장애가 생기면, 먼저 현재 상태를 새 백업으로 남기고 복구 방식(전체 교체 또는 일부 복원)을 결정한다. 이 절차는 사용자 승인 후 실행한다.

> 미검증: 백업 실행, S3 업로드, 복구 절차는 실제 환경에서 실행하지 않았다. 첫 운영 배포 뒤 1회 백업과 복구 연습을 해야 한다.

## 7. DB 스키마 적용과 최초 관리자 지정

### 7.1 수동 SQL 001~008 적용 (새 DB, 최초 1회)

순서를 지킨다. 이미 적용된 DB에는 다시 실행하지 않는다.

```bash
# 관리 계정(busan_admin)으로, 저장소의 db/manual 디렉터리에서 실행
for f in 001 002 003 004 005 006 007 008; do
  echo "apply ${f}"
  mysql --user=busan_admin --password busan_local_food < db/manual/${f}_*.sql
done
```

- 실행 전 `mysqldump`로 백업을 떠 둔다. 파일명 순서: `001_create_member_refresh_token.sql` ~ `008_create_favorite.sql`.
- 적용 뒤 `busan_admin` 계정은 비활성화하거나 삭제한다 (`DROP USER 'busan_admin'@'localhost';`).

### 7.2 최초 ADMIN 지정

관리자 화면과 API에는 역할을 바꾸는 기능이 없다 (T2 A안). 최초 관리자는 DB에서 직접 지정한다.

```sql
-- 회원 가입을 먼저 한 뒤, 실제 관리자 이메일로 한 번만 실행한다. 이메일은 문서나 Git에 기록하지 않는다.
UPDATE member SET role = 'ADMIN' WHERE email = '실제_관리자_이메일' AND role = 'USER';
```

- 역할 변경은 다음 로그인 또는 토큰 재발급부터 반영된다.
- 실행 결과 확인: `SELECT member_id, role FROM member WHERE role = 'ADMIN';` (이메일은 출력 확인 후 화면에서 지운다).

## 8. 도메인·HTTPS 전후 제약

| 단계 | 가능한 것 | 불가능하거나 제한된 것 |
|---|---|---|
| 도메인 없음 (HTTP) | 서버 기동, 홈·맛집 목록·상세 등 비로그인 화면 확인 | 화면 로그인·API 인증: 운영 프로필은 Secure 쿠키를 쓰므로 브라우저가 HTTP에서 쿠키를 저장하지 않는다. Google 로그인: 콜백 주소를 Google에 등록할 수 없다 |
| 도메인 + HTTPS | 전체 기능 확인 가능 | 인증서 갱신 자동화는 certbot timer로 확인해야 함 |

도메인 연결 절차 (나중에):
1. DNS A 레코드를 EC2 공인 IP로 연결한다.
2. Nginx `server_name`을 실제 도메인으로 바꾸고, `certbot --nginx -d <도메인>`으로 인증서를 받는다. 또는 `certbot certonly --webroot`로 받은 뒤 `deploy/nginx/busan-local-food.conf`의 HTTPS 블록 주석을 해제한다.
3. HTTP를 HTTPS로 리다이렉트하고 HSTS를 추가한다.
4. Google Cloud Console에서 승인된 리디렉션 URI를 `https://<도메인>/login/oauth2/code/google`로 등록한다.
5. 갱신 확인: `sudo certbot renew --dry-run`.

Google OAuth 제약: 운영 프로필은 `GOOGLE_CLIENT_ID`와 `GOOGLE_CLIENT_SECRET`이 모두 있어야 기동한다. 도메인이 없을 때 임시 값을 넣으면 기동은 되지만 Google 로그인은 동작하지 않는다. 이 검증은 약화하지 않았다.

## 9. S3, IAM, OIDC

### 9.1 이미지 버킷 (기존 `AWS_S3_BUCKET`)

- 현재 앱은 `https://<버킷>.s3.<리전>.amazonaws.com/<키>` 형식의 공개 URL을 화면에 쓴다 (`ImageUrls`).
- 그래서 이 방식을 유지하면 버킷 읽기를 공개해야 하고, 그러면 "공개 접근 금지" 원칙과 충돌한다.
- **결정 필요 (T21, 신규)**: (a) 버킷을 비공개로 두고 CloudFront(OAC)로 읽기, (b) 앱이 pre-signed URL을 만들기 (앱 코드 변경 필요), (c) 공개 읽기 유지(예외 승인). 세 방식 가운데 하나를 정하기 전까지 이미지 표시는 운영에서 확인되지 않는다. 이번 작업에서는 코드를 바꾸지 않았다.
- 업로드·삭제 권한은 EC2 역할에 `s3:PutObject`, `s3:DeleteObject`로 이미지 버킷 객체 경로만 준다.

### 9.2 백업 버킷 (`BACKUP_S3_BUCKET`)

EC2 역할에 필요한 권한만: 백업 키 경로에 `s3:PutObject`, `s3:GetObject`(검증·복구), `s3:ListBucket`(복구 목록). 수명 규칙 설정은 운영자 권한으로 한다.

### 9.3 배포 아티팩트 버킷 (`DEPLOY_ARTIFACT_BUCKET`, T22 추가 결정)

JAR를 EC2로 전달하려면 S3가 필요하다. 이미지 버킷과 백업 버킷을 섞지 않기 위해 세 번째 버킷을 쓰는 안을 택했다. **사용자 확인 필요**: 버킷을 새로 만들지, 다른 방법을 쓸지 정해 달라.
- 공개 접근 차단, SSE-S3 기본 암호화, `releases/` 아래에만 쓴다.
- 수명 규칙으로 오래된 아티팩트를 지울 수 있다 (선택). 스크립트는 EC2에서 최근 5개를 로컬에 보관한다.

### 9.4 EC2 인스턴스 역할 (예시 권한 목록)

| 권한 | 대상 |
|---|---|
| `AmazonSSMManagedInstanceCore` (관리형 정책) | SSM Run Command 수신 |
| `s3:GetObject` | `DEPLOY_ARTIFACT_BUCKET/releases/*` |
| `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject` | `AWS_S3_BUCKET/*` (이미지 키 경로) |
| `s3:PutObject`, `s3:GetObject`, `s3:ListBucket` | `BACKUP_S3_BUCKET` (`daily/*`, `weekly/*`) |

`s3:GetBucketLocation`은 리전 확인이 필요할 때만 추가한다. 계정 ID와 ARN은 사용자가 실제 값으로 채운다.

### 9.5 GitHub OIDC 역할 (배포용)

1. IAM에 OIDC 아이디 공급자 `token.actions.githubusercontent.com`을 등록한다 (사용자 작업).
2. 역할 신뢰 정책의 조건을 이 저장소의 `main`으로 제한한다.

```json
{
  "Effect": "Allow",
  "Principal": { "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com" },
  "Action": "sts:AssumeRoleWithWebIdentity",
  "Condition": {
    "StringEquals": {
      "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
      "token.actions.githubusercontent.com:sub": "repo:keosin6/busan-local-food:ref:refs/heads/main"
    }
  }
}
```

3. 권한 정책 (배포 역할):
   - `s3:PutObject` on `arn:aws:s3:::<DEPLOY_ARTIFACT_BUCKET>/releases/*`
   - `ssm:SendCommand` on `arn:aws:ec2:<REGION>:<ACCOUNT_ID>:instance/<INSTANCE_ID>` 과 `arn:aws:ssm:<REGION>::document/AWS-RunShellScript`
   - `ssm:GetCommandInvocation`, `ssm:ListCommandInvocations` on `*`
   - `ec2:DescribeInstances`는 기본적으로 필요하지 않다. `wait command-executed`가 권한 오류를 내면 추가를 검토한다.

- `environment:` 보호 규칙을 쓰면 sub 값이 바뀐다. 이번 워크플로는 environment를 쓰지 않는다.

## 10. GitHub 설정

Repository Variables (Settings → Secrets and variables → Actions → Variables. 비밀이 아니다):

| 이름 | 값 |
|---|---|
| `AWS_REGION` | 사용자가 정한 리전 |
| `AWS_DEPLOY_ROLE_ARN` | 9.5의 역할 ARN |
| `DEPLOY_ARTIFACT_BUCKET` | 9.3의 아티팩트 버킷 이름 |
| `EC2_INSTANCE_ID` | 배포 대상 인스턴스 ID |

- GitHub Secrets에는 AWS 키를 넣지 않는다. 이 워크플로는 Secrets를 쓰지 않는다.
- 운영 환경변수(DB 비밀번호, JWT 키 등)는 GitHub에 넣지 않는다. EC2의 `app.env`에만 둔다.

EC2의 배포용 설정 파일 (root 640):

```bash
sudo install -o root -g busan -m 640 /dev/null /etc/busan-local-food/deploy.env
echo 'DEPLOY_ARTIFACT_BUCKET=<DEPLOY_ARTIFACT_BUCKET>' | sudo tee /etc/busan-local-food/deploy.env >/dev/null
```

## 11. 배포와 롤백

### 11.1 자동 배포 흐름 (`.github/workflows/deploy.yml`)

1. `main`에 푸시(병합)되면 실행된다. 수동 실행(`workflow_dispatch`)도 `main` 기준에서만 배포된다.
2. Java 21에서 `./gradlew clean test`, `./gradlew bootJar`를 실행한다. CI에는 MySQL이 없어 DB 테스트는 건너뛴다.
3. JAR의 SHA-256을 계산하고 OIDC로 임시 자격 증명을 받는다.
4. `releases/<커밋 SHA>.jar`를 배포 버킷에 올린다.
5. SSM Run Command로 `/opt/busan-local-food/bin/deploy.sh <SHA> <sha256>`을 실행한다.
6. 워크플로는 SSM 명령이 끝날 때까지 기다리고 결과를 출력한다.

### 11.2 `deploy.sh` 동작

1. 아티팩트를 내려받고 SHA-256이 다르면 아무것도 바꾸지 않고 실패한다.
2. 새 JAR를 `releases/<시각>-<SHA7>.jar`로 두고 `current.jar`를 원자적으로 바꾼다.
3. `systemctl restart busan-local-food`.
4. `GET http://127.0.0.1:8080/`이 200을 낼 때까지 최대 60초 기다린다.
5. 실패하면 직전 JAR로 되돌리고 다시 기동한 뒤 실패로 끝낸다.
6. 성공하면 최근 5개만 남기고 오래된 JAR를 지운다.

### 11.3 수동 롤백

```bash
sudo /opt/busan-local-food/bin/rollback.sh                       # 현재 바로 전 JAR로
sudo /opt/busan-local-food/bin/rollback.sh /opt/busan-local-food/releases/<파일>.jar   # 지정 JAR로
ls -1t /opt/busan-local-food/releases
```

### 11.4 첫 배포 절차 (체크리스트)

1. 4절 완료 (EBS, MySQL, 계정, 스키마 001~008, 최초 관리자는 배포 뒤에 지정해도 된다).
2. 5절 `app.env`와 9.3절 `deploy.env` 작성, 6.2절 백업 설정.
3. `sudo bash deploy/scripts/setup-ec2.sh` (이미 했다면 생략하거나 다시 실행해도 안전).
4. 9절 IAM 역할과 OIDC, 10절 Repository Variables 설정.
5. 워크플로를 `workflow_dispatch`로 한 번 실행하거나 `main`에 병합한다.
6. 확인: `curl -I http://<EC2 공인 IP>/`, `systemctl status busan-local-food`, `journalctl -u busan-local-food -n 100`.

> 주의: `deploy.sh`와 `setup-ec2.sh` 자체는 EC2에 설치된 사본을 쓴다. 스크립트를 바꾸면 `setup-ec2.sh`를 다시 실행해 사본을 갱신한다.

### 11.5 Nginx 확인

```bash
sudo nginx -t && sudo systemctl reload nginx
```

## 12. 로그와 임시 파일 관리

| 대상 | 위치 | 제한 |
|---|---|---|
| 애플리케이션 로그 | journald (`journalctl -u busan-local-food`) | `SystemMaxUse=500M`, 30일 |
| 백업 로그 | journald (`-t busan-mysql-backup`) | 같은 제한 |
| 배포 로그 | journald (`-t busan-deploy`) | 같은 제한 |
| Nginx 로그 | `/var/log/nginx/` | Ubuntu 기본 logrotate |
| JAR 릴리스 | `/opt/busan-local-food/releases/` | 최근 5개 |
| 백업 임시 파일 | `/var/backups/busan-local-food/tmp/` | 성공 시 삭제, 실패 시 수동 정리 (6.4) |
| 배포 다운로드 임시 파일 | `/opt/busan-local-food/tmp/` | 검증 실패 시 삭제, 성공 시 이동 |

- 디스크 확인: `df -h /` 와 `df -h /data`. `/data`는 DB 용량을 먼저 확인한다.

## 13. 운영 주의사항

- EBS 볼륨 삭제·교체 시 DB 데이터가 사라질 수 있다. 볼륨 삭제 보호를 켜고, 변경 전 백업을 확인한다.
- `mount-data-volume.sh --format-new-volume`은 서명이 없는 새 볼륨에만 쓴다. 기존 데이터가 있는 볼륨에는 쓰지 않는다.
- 백업은 복구 연습을 해야 의미가 있다. 첫 운영 뒤 한 번 연습한다.
- 역할 변경(ADMIN 지정)은 다음 로그인 또는 토큰 재발급부터 반영된다.
- S3 삭제 실패 객체는 `storage_delete_task`에 남으며, 자동 재시도는 없다. 관리자 수동 재시도(F1-09)를 쓴다.
- 한 EC2에 앱과 DB가 함께 있으므로 디스크와 메모리 여유를 모니터링한다. t3.medium 기준 JVM 힙은 1GB로 고정했다 (`-Xmx1024m`).

## 14. 테스트와 미검증 항목

### 14.1 이 작업에서 실행한 것

- `./gradlew clean test bootJar` (DB 미설정, 기준 `main` 4a2a11c, 애플리케이션 코드 변경 없음): 913개 실행, 191개 건너뜀(모두 DB 테스트), 실패 0, 오류 0. `bootJar` 성공.
- 셸 문법 검사: `bash -n` (배포·롤백·백업·볼륨·EC2 설정 스크립트) 통과.
- 스크립트 동작 시험 (실제 서버가 아닌 임시 디렉터리, `aws`·`systemctl`·`curl`·`mysqldump`를 가짜 명령으로 대체):
  - `deploy.sh`: 정상 배포, 헬스 체크 실패 시 직전 JAR로 자동 롤백(종료 코드 1), SHA-256 불일치 시 현재 버전 유지(종료 코드 1). 세 경우 모두 기대대로 동작.
  - `backup-mysql.sh`: 업로드 실패 시 로컬 파일 보존(종료 코드 1), 업로드와 크기 검증 성공 시 로컬 임시 파일 삭제(종료 코드 0). 일요일 주간 백업 경로는 시험하지 않았다.
  - `rollback.sh`, `mount-data-volume.sh`는 가짜 명령 시험을 하지 않았다. 블록 장치와 systemd가 필요하다.
- systemd 유닛 검증: `systemd-analyze verify` (서비스·백업 서비스·타이머). 백업 스크립트 미설치 경고 외 구문 오류 없음.
- Nginx 구문 검사 (`nginx -t`): 이 환경에 nginx가 없어 실행하지 않았다. EC2 설치 후 `setup-ec2.sh`가 `nginx -t`를 실행한다.
- GitHub Actions YAML 구문 검사: PyYAML로 파싱 확인. 전체 워크플로 실행과 actionlint는 실행하지 않았다.
- S3 수명 규칙 JSON 파싱 확인.

### 14.2 실행하지 않은 것 (미검증)

- 실제 EC2 생성, EBS 마운트, 재부팅 후 자동 마운트
- MySQL 설치, AppArmor 규칙, 데이터 디렉터리 이동, SQL 001~008 적용
- 실제 S3 업로드·다운로드, 이미지 표시
- 실제 IAM 권한, GitHub OIDC 인증, SSM 배포
- 백업 실행, S3 업로드 검증, 복구
- 실제 브라우저 접속, 화면 로그인 (Secure 쿠키 제약)
- Google OAuth 운영 콜백, HTTPS 인증서 발급
- DB 테스트 (`TEST_DB_URL` 설정 시) — 이 작업의 CI는 DB를 띄우지 않는다

## 15. 남은 위험과 미확정 항목

| 번호 | 내용 | 상태 |
|---|---|---|
| T4 | DB 백업 정책 | 정책 확정, 스크립트 작성, 실행 미검증 |
| T5 | MySQL 설치와 영속 저장소 | 절차 작성, 실제 설치 미검증 |
| T6 | S3 삭제 자동 재시도 | 미확정 유지 (자동 재시도 없음, 수동만) |
| T10 | OpenRouter 모델 ID | 미확정 유지 (`OPENROUTER_MODEL` 미설정이면 UNAVAILABLE) |
| T21 (신규) | 이미지 공개 방식 (9.1절) | 결정 필요 |
| T22 | 배포 아티팩트 버킷 추가 (9.3절) | 사용자 확인 필요 |
| 신규 | 도메인 없는 동안 로그인 불가 (8절) | 도메인 연결 시 해소 |
| 신규 | Google OAuth 운영 값 필요 (8절) | Google Console 설정 필요 |
| 신규 | CI에서 DB 테스트 미실행 | 후속 과제 (별도 검토) |
| 기존 | 여러 인스턴스 사이의 S3 재시도 동시 실행 미방지 | 단일 인스턴스 운영 전제 |
| 기존 | `GlobalExceptionHandlerTest` 시간 의존 간헐 실패 | 별도 수정 필요 |
| 기존 | 공개 요약 API 요청 제한 없음 | 후속 과제 |
