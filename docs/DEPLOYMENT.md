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
| 애플리케이션 | Spring Boot JAR, 내부 8080 (`application-prod.yml`의 `server.address: 127.0.0.1`로 루프백에만 바인딩) |
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

### 4.2 EBS 마운트 (EC2 초기 설정 4.3보다 먼저)

사전 조건: 저장소를 EC2에 복사해 `deploy/` 디렉터리가 있어야 한다 (예: `git clone` 또는 `scp`). 마운트 스크립트는 설치된 사본이 아니라 저장소 안의 파일을 직접 실행한다. `/opt/busan-local-food/bin/`에는 아직 아무것도 없다 (4.3에서 설치).

```bash
# 저장소 루트(deploy/ 가 보이는 디렉터리)에서 실행한다.
# 장치 이름 확인 (볼륨 크기로 구분). 실제 장치 경로는 lsblk 결과로 정하고, 아래 예시 이름을 그대로 쓰지 않는다.
lsblk
# 새 볼륨(서명 없음)인 경우에만 포맷 옵션을 준다. 기존 데이터가 있는 볼륨에는 옵션을 주지 않는다.
sudo bash deploy/scripts/mount-data-volume.sh /dev/nvme1n1 --format-new-volume
# 이미 ext4로 포맷된 볼륨(재부팅·재연결 등)이면 포맷 옵션 없이 실행한다. 옵션이 있어도 ext4 볼륨은 포맷하지 않는다.
sudo bash deploy/scripts/mount-data-volume.sh /dev/nvme1n1
```

- 파일 시스템 분기 (기존 데이터 보호 우선):
  - 서명이 없는 새 장치: `--format-new-volume`을 명시했을 때만 ext4로 만든다.
  - 이미 ext4가 있는 장치: 포맷하지 않고 마운트만 한다. `--format-new-volume`은 무시한다.
  - ext4가 아닌 파일 시스템(xfs, btrfs 등): 포맷하지 않고, fstab을 쓰지 않고, 종료 코드 1로 중단한다. 데이터를 확인한 뒤 사람이 직접 결정한다.
  - 장치에 서명이 없는데 옵션도 없으면 중단한다.
- 마운트 UUID 검증 (PR #49 후속): `/data`가 이미 마운트되어 있으면, 그 마운트의 UUID가 입력 장치의 UUID와 같을 때만 성공한다. 다른 UUID가 마운트되어 있으면 fstab과 기존 마운트를 건드리지 않고 즉시 실패한다.
- `/data`가 마운트되어 있지 않으면 fstab에 항목을 추가하고 마운트한 뒤, `findmnt`로 실제 마운트의 UUID와 파일 시스템(ext4)을 모두 확인한다. 하나라도 다르면 실패한다.
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

### 4.4 MySQL 데이터 디렉터리를 `/data/mysql`로 옮기기 (신규 EC2 최초 1회)

> **적용 대상 구분 (반드시 먼저 읽는다)**
>
> - **이 절차는 신규 EC2의 빈 MySQL 데이터 디렉터리를 대상으로 한다.** 이번 배포 절차의 기본 경로다. 6번 단계는 `/data/mysql`이 비어 있을 때만 초기화하고, 파일이 하나라도 있으면 아무것도 하지 않고 멈춘다.
> - **기존 데이터가 있는 MySQL 서버에서는 이 절차를 그대로 실행하면 안 된다.** 기존 데이터가 `/var/lib/mysql`에 있으면, 이 경로를 그대로 두는 것은 안전하지 않다. `setup-ec2.sh`가 설치하는 drop-in(`RequiresMountsFor=/data/mysql`, `AssertPathIsMountPoint=/data`)과 애플리케이션 서비스의 `/data/mysql` 의존성 때문에 MySQL이 시작하지 않을 수 있다. 기존 경로를 "유지"한다는 말은 이 배포 구조에서는 성립하지 않는다.
> - **기존 데이터 서버는 별도의 데이터 이전·검증·롤백 계획을 먼저 세운다.** 최소한 다음을 정한다: (1) 이전 전 전체 백업과 복구 검증 (6.5절), (2) 이전 방식과 쓰기 중지 시점, (3) 이전 후 행 수와 주요 테이블 검증, (4) 실패 시 원래 경로와 서비스 설정으로 되돌리는 롤백 절차, (5) 승인자. 계획이 확정되기 전에는 이 절차의 어떤 명령도 기존 서버에서 실행하지 않는다.
> - **기존 데이터 서버에서 `mysqld --initialize-insecure`, 디렉터리 초기화, `rm`, `install -d`로 데이터 경로를 바꾸는 명령을 실행하지 않는다.**
> - `setup-ec2.sh`, MySQL drop-in, 애플리케이션 서비스의 EBS 의존성은 기존 서버에서도 제거하지 않는다. 계획에 따라 데이터를 옮긴 뒤 그대로 적용한다.

전제: 4.3(`setup-ec2.sh`)으로 MySQL 패키지가 설치되어 있다. 패키지는 `/var/lib/mysql`에 기본 데이터를 만들고 기동했을 수 있다. 아래 순서를 지킨다.

순서가 중요하다. AppArmor 규칙을 먼저 넣고 프로필을 다시 읽힌 뒤에야 `/data/mysql`에서 초기화하고 기동한다.

1. MySQL 서비스를 멈춘다.

   ```bash
   sudo systemctl stop mysql
   ```

2. `/data`가 실제로 마운트되어 있는지 확인한 뒤 `/data/mysql` 디렉터리를 만들고 권한을 준다. (이 단계는 파일을 만들거나 지우지 않는다.)

   ```bash
   # EBS가 마운트되지 않았다면 여기서 멈춘다. 마운트 전에 만들면 루트 볼륨에 빈 디렉터리가 생긴다.
   mountpoint -q /data || { echo "/data 가 마운트되어 있지 않습니다. 4.2절을 먼저 끝내세요." >&2; exit 1; }
   sudo install -d -o mysql -g mysql -m 750 /data/mysql
   ```

3. AppArmor alias를 추가한다. 같은 줄이 이미 있으면 추가하지 않는다 (반복 실행해도 중복되지 않는다).

   ```bash
   ALIAS_FILE=/etc/apparmor.d/tunables/alias
   ALIAS_LINE='alias /var/lib/mysql/ -> /data/mysql/,'
   sudo grep -qxF "$ALIAS_LINE" "$ALIAS_FILE" || echo "$ALIAS_LINE" | sudo tee -a "$ALIAS_FILE" >/dev/null
   ```

4. mysqld 로컬 규칙을 추가한다. 역시 같은 줄이 있으면 건너뛴다.

   ```bash
   LOCAL_FILE=/etc/apparmor.d/local/usr.sbin.mysqld
   sudo install -d -m 755 /etc/apparmor.d/local
   sudo touch "$LOCAL_FILE"
   for RULE in '/data/mysql/ r,' '/data/mysql/** rwk,'; do
     sudo grep -qxF "$RULE" "$LOCAL_FILE" || echo "$RULE" | sudo tee -a "$LOCAL_FILE" >/dev/null
   done
   ```

5. AppArmor 프로필을 다시 읽힌다.

   ```bash
   sudo apparmor_parser -r /etc/apparmor.d/usr.sbin.mysqld
   ```

6. **빈 디렉터리일 때만** 초기화한다. 파일이 하나라도 있으면 아무것도 하지 않고 멈춘다. **기존 데이터가 있는 서버에서는 이 단계를 실행하지 않는다.** 위의 적용 대상 구분을 따른다.

   ```bash
   if [ -z "$(sudo ls -A /data/mysql)" ]; then
     sudo mysqld --initialize-insecure --datadir=/data/mysql --user=mysql
   else
     echo "/data/mysql 에 파일이 있습니다. 초기화하지 않고 중단합니다. 기존 데이터를 확인하세요." >&2
     exit 1
   fi
   ```

   `--initialize-insecure`는 root 비밀번호를 비워 둔다. 기동 직후 계정 설정 전까지 root는 소켓 접속(`sudo mysql`)으로만 관리한다.

7. 데이터 경로 설정 파일을 적용하고 MySQL을 기동한다.

   ```bash
   sudo cp deploy/mysql/busan-mysql-datadir.cnf /etc/mysql/mysql.conf.d/99-busan.cnf
   sudo systemctl start mysql
   sudo systemctl enable mysql
   sudo systemctl status mysql --no-pager
   ```

8. 실제 데이터 경로를 확인한다. `/data/mysql/`이어야 한다.

   ```bash
   sudo mysql -e 'SELECT @@datadir;'
   ```

- 데이터 디렉터리 권한은 `mysql:mysql`, 750이어야 한다.
- 기동 실패 시 `sudo journalctl -u mysql -n 100`과 `sudo dmesg | grep -i apparmor`로 원인을 찾는다.

#### MySQL의 EBS 마운트 의존성

`setup-ec2.sh`는 `/etc/systemd/system/mysql.service.d/ebs-data-mount.conf`를 설치한다. 원본은 `deploy/systemd/mysql.service.d/ebs-data-mount.conf`다. 기존 `mysql.service` 파일은 덮어쓰지 않는다. 이 파일만 추가한다.

```ini
[Unit]
RequiresMountsFor=/data/mysql
AssertPathIsMountPoint=/data
```

- MySQL은 `/data`(EBS) 마운트가 완료된 뒤에만 시작한다. `RequiresMountsFor`는 마운트 유닛에 대한 의존성을 만든다. 마운트가 실패하면 MySQL 시작이 실패하고, 마운트가 나중에 멈추면 MySQL도 함께 멈춘다.
- `AssertPathIsMountPoint=/data`는 마지막 방어선이다. `/data`가 마운트 지점이 아니면 시작을 거부하고 이유를 남긴다. fstab 항목이 빠졌거나 `nofail`로 마운트가 조용히 빠진 경우에도 루트 볼륨의 빈 `/data/mysql`에 데이터가 쓰이지 않게 한다.
- 설치 뒤 `systemctl daemon-reload`가 실행된다. 이미 떠 있는 MySQL에는 다음 시작부터 적용된다.

확인 명령 (EC2에서):

```bash
systemctl cat mysql                                   # 기본 유닛 뒤에 drop-in(ebs-data-mount.conf)이 보여야 한다
systemctl show mysql -p RequiresMountsFor -p AssertPathIsMountPoint
systemctl list-dependencies mysql | grep -i data      # /data 관련 마운트 유닛이 보여야 한다
sudo findmnt /data                                    # 마운트 상태 확인
```

> 미검증: 실제 systemd 부팅 순서, EBS 분리 시 MySQL 거부 동작, 재부팅 후 자동 마운트와 MySQL 기동 순서는 EC2에서 확인하지 않았다. 로컬에서는 drop-in의 구문과 설치 동작만 검사했다 (`deploy/tests/test-mysql-dropin.sh`).

> 미검증: 위 AppArmor 규칙, alias 문법, `apparmor_parser -r`, `mysqld --initialize-insecure` 경로는 실제 Ubuntu 24.04 EC2에서 확인하지 않았다. 검증 완료로 기록하지 않는다.

### 4.5 DB와 계정 생성 (최초 1회)

1. `deploy/mysql/bootstrap-accounts.sql.example`을 서버의 임시 파일로 복사한다.
2. 모든 `CHANGE_ME_...` 값을 `openssl rand -base64 24`로 만든 값으로 바꾼다. Git에 올리지 않는다.
3. `sudo mysql < /root/bootstrap.sql`로 실행하고, 실행 뒤 파일을 삭제한다 (`shred -u` 권장).

| 계정 | 접속 | 권한 | 용도 |
|---|---|---|---|
| `busan_admin`@`localhost` | 소켓 | 전체 (DB 한정) | 수동 SQL 001~008 적용. 끝나면 비활성화 |
| `busan_app`@`127.0.0.1` | TCP | SELECT, INSERT, UPDATE, DELETE | 애플리케이션. DDL 없음 |
| `busan_backup`@`127.0.0.1` | TCP | DB 범위: SELECT, SHOW VIEW, TRIGGER, EVENT, LOCK TABLES. 전역 범위: SHOW_ROUTINE | 백업 전용 (읽기만) |

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

### 6.2.1 백업 계정 권한 확인 (필수)

백업 계정 `busan_backup`은 두 범위의 권한이 필요하다.

- DB 범위(`busan_local_food.*`): SELECT, SHOW VIEW, TRIGGER, EVENT, LOCK TABLES. 테이블, 트리거, 이벤트를 덤프한다.
- 전역 범위(`*.*`): **SHOW_ROUTINE**. 프로시저와 함수 정의를 덤프한다. MySQL 8.0.22 이상의 동적 권한이며, DB 단위로는 줄 수 없다 (`GRANT SHOW_ROUTINE ON db.*`는 오류 3619). 이 권한은 메타데이터 조회만 허용하고 루틴 실행이나 변경은 허용하지 않는다.

SHOW_ROUTINE이 없으면 `mysqldump --routines`는 루틴을 **조용히 빼고 종료 코드 0으로 끝난다.** 백업 스크립트는 성공으로 보고하므로, 권한 누락은 복구 때에만 드러난다. 그래서 권한을 설정한 뒤 확인한다.

```sql
SHOW GRANTS FOR 'busan_backup'@'127.0.0.1';   -- SHOW_ROUTINE ON *.* 줄이 있어야 한다
```

권한 누락을 검증한 방법 (PR #49 후속, 로컬 MySQL 8.0.46): 테스트 DB에 프로시저, 함수, 트리거, 이벤트를 하나씩 만들고 백업 계정으로 `mysqldump`를 실행했다. SHOW_ROUTINE이 있으면 프로시저와 함수가 모두 덤프에 들어갔다. 없으면 트리거와 이벤트만 들어가고 프로시저와 함수는 빠졌으며 종료 코드는 0이었다.

운영 DB에 루틴이 있으면, 백업 뒤 덤프에 `CREATE ... PROCEDURE` 또는 `FUNCTION`이 있는지 확인한다. 현재 앱은 저장 프로시저를 쓰지 않으므로 이 확인은 권한 점검용이다.

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

### 9.1 이미지 버킷 (기존 `AWS_S3_BUCKET`, T21)

**상태: 권장안 검토 중. 사용자 확정 전까지 결정 완료로 보지 않는다.**

- 현재 앱은 `https://<버킷>.s3.<리전>.amazonaws.com/<키>` 형식의 공개 S3 URL을 만든다 (`ImageUrls`). 이 URL은 버킷 공개 읽기를 전제한다.
- 권장안: 이미지 버킷은 비공개로 유지하고, CloudFront + Origin Access Control(OAC)로만 읽는다. 버킷 정책은 CloudFront 배포(SourceArn 조건)에만 `s3:GetObject`를 허용한다.
- 비공개 S3 + CloudFront OAC를 적용하려면 **이미지 URL 생성 방식의 후속 변경이 필요하다.** 지금의 S3 공개 URL을 설정된 CloudFront 주소 기준으로 만들도록 바꿔야 한다. 이번 PR은 애플리케이션 코드와 URL 구조를 바꾸지 않았다.
- CloudFront 적용 전에는 운영 환경에서 이미지 표시를 검증할 수 없다. 로컬 테스트는 목 구현체로 URL 형식만 확인한다.
- 공개 접근을 임시로 허용해서 문제를 해결하지 않는다. 비공개 원칙을 유지한다.
- 후속 작업 목록: (1) CloudFront 배포와 OAC 생성 (사용자), (2) 버킷을 비공개로 설정하고 CloudFront만 허용하는 정책 적용 (사용자), (3) `ImageUrls`가 CloudFront 기준 URL을 쓰도록 설정화하는 코드 변경과 테스트 (별도 PR), (4) 운영에서 이미지 표시 확인.
- 업로드·삭제 권한은 EC2 역할에 `s3:PutObject`, `s3:DeleteObject`로 이미지 버킷 객체 경로만 준다.

### 9.2 백업 버킷 (`BACKUP_S3_BUCKET`)

EC2 역할에 필요한 권한만: 백업 키 경로에 `s3:PutObject`, `s3:GetObject`(검증·복구), `s3:ListBucket`(복구 목록). 수명 규칙 설정은 운영자 권한으로 한다.

### 9.3 배포 아티팩트 버킷 (`DEPLOY_ARTIFACT_BUCKET`, T22)

**상태: 결정됨. 배포 아티팩트 전용 버킷을 따로 쓴다. 사용자가 AWS에서 직접 만든다.**

- 이 버킷이 필요한 이유: 배포 JAR는 이미지(사용자 업로드)나 백업(DB 덤프)과 수명과 접근 권한이 다르다. 한 버킷을 같이 쓰면 배포 권한으로 이미지나 백업을 건드리거나, 백업 권한으로 배포 JAR를 덮을 수 있고, 수명 규칙도 충돌한다. 그래서 이미지 버킷과 백업 버킷을 재사용하지 않는다.
- 버킷 이름과 ARN은 사용자가 정한다. 이 문서와 저장소에는 실제 이름을 넣지 않았다.
- 설정:
  - 공개 접근 차단 4개 옵션 모두 켠다.
  - 기본 암호화 SSE-S3(AES256).
  - 객체 키는 `releases/<커밋 SHA>.jar`만 쓴다.
- 접근 제한:
  - GitHub Actions 배포 역할: `releases/*`에 `s3:PutObject`만. 읽기·삭제 권한 없음.
  - EC2 인스턴스 역할: `releases/*`에 `s3:GetObject`만. 쓰기·삭제 권한 없음.
- 수명 규칙은 두지 않는다. 배포 스크립트가 EC2에서 최근 5개 JAR만 로컬에 남긴다. 버킷의 오래된 아티팩트를 지울지는 사용자가 정한다.

차단 설정 예시 (버킷 이름은 사용자가 채운다):

```bash
aws s3api put-public-access-block --bucket <DEPLOY_ARTIFACT_BUCKET> --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
aws s3api put-bucket-encryption --bucket <DEPLOY_ARTIFACT_BUCKET> --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
```

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
2. 배포 스크립트 가짜 명령 테스트와 `bash -n` 검사를 실행한다 (`deploy/tests/`).
3. 워크플로의 MySQL 8 서비스 컨테이너(테스트 전용, 운영 DB 아님)에 수동 SQL 001~008을 순서대로 적용한다.
4. Java 21에서 `./gradlew clean test`를 실행한다. `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`는 이 단계 env에만 설정한다. DB 테스트 191개가 실제로 실행된다.
5. 테스트 결과 XML을 검사한다. 실패, 건너뜀, DB 테스트 0개 중 하나라도 있으면 워크플로가 실패하고 배포 단계로 가지 않는다.
6. `./gradlew bootJar`를 실행하고 JAR의 SHA-256을 계산한다. OIDC로 임시 자격 증명을 받는다.
7. `releases/<커밋 SHA>.jar`를 배포 버킷에 올린다.
8. SSM Run Command로 `/opt/busan-local-food/bin/deploy.sh <SHA> <sha256>`을 실행한다.
9. 워크플로는 명시적 대기 한도 1080초(SSM 명령 제한 900초 + 여유 180초)로 결과를 기다리고, 상태와 출력을 보여 준다. 한도를 넘으면 실패로 끝내고 명령 ID를 안내한다. 이때 배포가 계속 진행 중일 수 있으므로 서버 상태를 직접 확인한다.

### 11.2 `deploy.sh` 동작

1. 아티팩트를 내려받고 SHA-256을 검증한다. 불일치하면 아무것도 바꾸지 않고 종료 코드 1로 끝낸다.
2. `current.jar` 심볼릭 링크를 원자적으로 새 JAR로 바꾸고 `systemctl restart busan-local-food`를 실행한다.
3. 재시작 명령의 성공, 서비스 active 상태, `GET http://127.0.0.1:8080/`의 200 응답을 각각 확인한다.
4. 새 버전이 어느 단계에서든 실패하면 직전 JAR로 링크를 되돌리고 같은 확인을 다시 한다. 로그에 실패 사유가 단계별로 남는다.
5. 성공하면 최근 5개만 남기고 오래된 JAR를 지운다. 실패한 새 JAR는 검사용으로 남긴다.

종료 코드:

| 코드 | 의미 | 할 일 |
|---|---|---|
| 0 | 배포 성공 | 없음 |
| 1 | 배포 전 단계 실패 (인자, 설정, 다운로드, SHA-256). 현재 버전 그대로 | 원인 확인 후 재실행 |
| 2 | 새 버전 실패, 이전 버전으로 복구 성공 | 실패 원인 확인 후 수정 |
| 3 | 새 버전 실패, 이전 버전 복구도 실패 | 즉시 서비스 확인 (`journalctl -u busan-local-food -n 200`) |
| 4 | 최초 배포 실패, 이전 JAR 없음 | 서비스가 비정상일 수 있음. 원인 확인 후 재배포 |

시간 예산 (SSM 명령 제한 900초 안에 끝나야 한다): 아티팩트 내려받기 최대 약 60초, 새 버전 재시작 최대 120초(`TimeoutStartSec`) + 헬스 체크 최대 약 150초(30회, 각 3초 제한과 2초 간격), 복구 재시작과 헬스 체크도 같은 상한. 최악의 경우 약 600초로 900초 안에 든다. 헬스 체크 횟수나 간격을 늘리면 이 예산을 다시 계산하고 `--timeout-seconds`와 워크플로 대기 한도를 함께 올린다.

`deploy/tests/test-deploy-rollback.sh`가 이 분기를 가짜 `systemctl`, `curl`, `aws`로 검사한다 (새 버전 재시작 실패, 헬스 실패, 이전 버전 재시작 실패, 최초 배포 실패, SHA 불일치).

### 11.3 수동 롤백

```bash
# 종료 코드 0: 롤백 후 정상 응답. 1: 대상 없음, 재시작 명령 실패, 기동 실패, 헬스 체크 실패 중 하나 (메시지 확인)
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

### 11.6 웹 바인딩과 Nginx 연결 확인

- 운영 프로필(`SPRING_PROFILES_ACTIVE=prod`)은 `application-prod.yml`의 `server.address: 127.0.0.1`로 8080을 루프백에만 연다. 외부 인터페이스의 8080 접속은 거부된다.
- 로컬 프로필과 기본 설정(`application.yml`, `application-local.yml`)은 바인딩 주소를 정하지 않는다. 기본값(모든 주소)을 그대로 쓴다.
- Nginx는 같은 EC2에서 `http://127.0.0.1:8080`으로 프록시하므로 연결에 영향이 없다. 헬스 체크(`deploy.sh`의 `GET http://127.0.0.1:8080/`)도 그대로다.

배포 후 확인 (EC2에서):

```bash
ss -ltn | grep ':8080'        # 127.0.0.1:8080 만 보여야 한다. 0.0.0.0:8080 또는 *:8080이면 잘못된 설정이다.
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/      # 200
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1/            # Nginx 경유, 200
curl -s -o /dev/null -m 5 -w '%{http_code}\n' http://<EC2 사설 IP>:8080/   # 000이어야 한다 (연결 거부)
```

Nginx 설정은 IPv4 전용이다. 검증 기록 (PR #49 후속, 로컬 호스트, EC2 아님): 바인딩 수정 전 jar는 `0.0.0.0:8080`(모든 주소)에 떴다. 수정 후 jar는 `127.0.0.1:8080`만 열고, 사설 IP 접속은 거부되었다. 저장소의 Nginx 설정(IPv6 줄 제외 사본)으로 `:80` 요청이 앱까지 전달되어 200을 받았다. 7MB 업로드는 Nginx에서 413, 3KB 요청은 앱까지 전달되었다 (403: CSRF 토큰 없음). **EC2에서의 확인은 아직 하지 않았다.**

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

### 14.1 실행한 것

이 절은 PR #49 후속 수정(배포 롤백 수정, T21·T22 문서화, DB 테스트 CI 보완, EBS 파일 시스템 검사)의 검증이다.

- `./gradlew clean test` (`TEST_DB_URL` 설정, 로컬 격리 MySQL 8.0, 테스트 전용 계정, `db/manual/001`~`008` 적용): 913개 실행, 건너뜀 0, 실패 0, 오류 0. DB 테스트 케이스 191개 실행. 워크플로와 같은 JDBC 설정(`sslMode=DISABLED&allowPublicKeyRetrieval=true`)을 썼다.
- MySQL 백업 계정 권한 (로컬 격리 MySQL 8.0.46, 테스트 전용 DB `busan_local_food`, 프로시저·함수·트리거·이벤트 각 하나):
  - `GRANT SHOW_ROUTINE ON busan_local_food.*`는 오류 3619로 거부됨 (전역 권한).
  - SHOW_ROUTINE 없이 `mysqldump --routines --triggers --events` 실행: 종료 코드 0, 트리거·이벤트는 포함, 프로시저·함수는 빠짐 (백업 스크립트도 종료 코드 0으로 성공 보고).
  - SHOW_ROUTINE ON `*.*` 부여 후 `backup-mysql.sh`를 실제 실행: 업로드 확인 성공, 덤프에 프로시저·함수·트리거·이벤트·테이블 모두 포함.
  - `deploy/mysql/bootstrap-accounts.sql.example`를 플레이스홀더 값만 바꿔 테스트 서버에 적용: 세 계정 생성과 권한 부여 성공, `SHOW GRANTS`에 SHOW_ROUTINE 확인.
- `./gradlew clean test` (DB 미설정): 913개 중 191개 건너뜀, 실패 0. 워크플로의 결과 검사는 이 경우를 실패로 처리한다 (건너뜀이 있으면 실패).
- `./gradlew bootJar`: 성공.
- `bash -n`: `deploy/scripts/*.sh`, `deploy/tests/*.sh` 전부 통과.
- `deploy/tests/test-deploy-rollback.sh`: 22개 검사 통과. 가짜 `systemctl`, `curl`, `aws`, `id`를 쓴다. 케이스: SHA 불일치, 새 버전 재시작 실패 → 복구, 헬스 실패 → 복구, 기동 실패(active 아님) → 복구, 이전 버전 재시작도 실패 → 복구 실패 보고(코드 3), 최초 배포 헬스 실패와 재시작 실패 → 이전 버전 없음 보고(코드 4), 최초 배포 성공, `rollback.sh` 재시작 실패 보고, `rollback.sh` 정상 롤백.
- `deploy/tests/test-mount-data-volume.sh`: 30개 검사 통과. 파일 시스템 분기와 fstab 줄(9개), 가짜 명령으로 마운트 UUID 검증 (이미 올바른 UUID가 마운트됨 → 성공, 다른 UUID의 ext4가 마운트됨 → 실패와 fstab 불변, 마운트되지 않음 → 마운트 후 UUID 검증, 마운트 후 UUID 불일치와 비 ext4 → 실패, 마운트 명령 실패, 포맷 후 마운트와 검증, 포맷 옵션 없음 중단).
- `deploy/tests/test-mysql-dropin.sh`: MySQL drop-in 내용, 설치 경로, `setup-ec2.sh`의 설치와 daemon-reload 순서, `systemd-analyze verify` 구문 검사 (대조군으로 잘못된 키 검출 확인). 실제 systemd 부팅 순서는 검사하지 않았다.
- `deploy/tests/test-ssm-wait.sh`: 워크플로 대기 단계 스크립트를 가짜 `aws`·`sleep`·`date`로 실행. Pending→Success, Failed, TimedOut, 한도 초과 케이스 통과. 대기 한도 1080초와 SSM 명령 제한 900초 확인. 파일 시스템 분기(서명 없음·옵션 없음 중단, 서명 없음·옵션 포맷, ext4 마운트만, ext4 + 포맷 옵션 무시, xfs·btrfs 중단), fstab 줄 형식, `mkfs.ext4`가 포맷 분기 안에서만 호출되는지.
- 이전 작성 검사: 백업 스크립트 가짜 명령 시험(업로드 실패 시 로컬 보존, 성공 시 삭제), systemd 유닛 `systemd-analyze verify`, S3 수명 규칙 JSON, 워크플로 YAML 파싱.
- `git diff --check`: 통과 (결과는 PR 보고 참고).

- 배포 사전 점검 (PR #49, 2026-11-28, 로컬 호스트): Nginx 저장소 설정을 직접 `nginx -t`로 검사했다. 이 호스트에는 IPv6가 없다. 이전 설정(`listen [::]:80`)은 실패, 새 설정(IPv4 전용)은 통과. EBS 장치 경로 점검은 입력만 확인했다 (`/dev/null`과 인자 없음은 종료 코드 1로 거부). 실제 장치는 건드리지 않았다. AppArmor와 MySQL 기동 순서는 로컬 MySQL로 재현하지 않았다.

### 14.2 실행하지 않은 것 (미검증)

- 실제 EC2 생성, EBS 마운트, 재부팅 후 자동 마운트
- MySQL 설치, AppArmor 규칙(alias, 로컬 규칙, `apparmor_parser -r`), 데이터 디렉터리 이동, `mysqld --initialize-insecure`, SQL 001~008 적용
- EC2에서의 `nginx -t`와 `ss -ltn`에 의한 리스닝 확인
- MySQL drop-in(`RequiresMountsFor`, `AssertPathIsMountPoint`)의 실제 systemd 부팅 순서, EBS 분리 시 MySQL 시작 거부
- GitHub Actions 러너에서 워크플로 대기 단계 실행 (가짜 명령 테스트만 함)
- 실제 S3 업로드·다운로드, 이미지 표시
- 실제 IAM 권한, GitHub OIDC 인증, SSM 배포
- 백업 실행, S3 업로드 검증, 복구
- 실제 브라우저 접속, 화면 로그인 (Secure 쿠키 제약)
- Google OAuth 운영 콜백, HTTPS 인증서 발급
- GitHub Actions 워크플로의 실제 실행 (서비스 컨테이너 `mysql:8.0`, 스키마 적용 step, 결과 검사 step): 로컬 MySQL과 같은 설정으로 시험했을 뿐, 러너에서 실행하지는 않았다.
- `mount-data-volume.sh` 실제 장치 마운트와 fstab 반영 (분기 판단 함수만 시험).
- `rollback.sh`를 실제 systemd에서 돌린 결과 (가짜 명령만 썼다).

## 15. 남은 위험과 미확정 항목

| 번호 | 내용 | 상태 |
|---|---|---|
| T4 | DB 백업 정책 | 정책 확정, 스크립트 작성, 실행 미검증 |
| T5 | MySQL 설치와 영속 저장소 | 절차 작성, 실제 설치 미검증 |
| T6 | S3 삭제 자동 재시도 | 미확정 유지 (자동 재시도 없음, 수동만) |
| T10 | OpenRouter 모델 ID | 미확정 유지 (`OPENROUTER_MODEL` 미설정이면 UNAVAILABLE) |
| T21 | 이미지 공개 방식 (9.1절): 비공개 S3 + CloudFront OAC 권장안 | 권장안 검토 중. 사용자 확정 전까지 결정 완료 아님. 후속 코드 변경 필요 |
| T22 | 배포 아티팩트 전용 버킷 (9.3절) | 결정됨. 사용자가 AWS에서 직접 생성 |
| 신규 | 도메인 없는 동안 로그인 불가 (8절) | 도메인 연결 시 해소 |
| 신규 | Google OAuth 운영 값 필요 (8절) | Google Console 설정 필요 |
| 해결 | CI DB 테스트 | 워크플로에 MySQL 8 서비스 컨테이너를 추가해 DB 테스트를 실행하고, 건너뜀이 있으면 실패한다 (이 PR) |
| 해결 | Nginx IPv6 | 저장소 Nginx 설정을 IPv4 전용(`listen 80 default_server`)으로 바꿨다. IPv6 listen은 두지 않는다. 이 호스트(IPv6 없음)에서 이전 설정은 `nginx -t` 실패, 새 설정은 통과를 확인했다. EC2의 IPv6 상태와 무관하게 동작해야 한다는 점은 EC2에서 `nginx -t`로 확인한다 |
| 기존 | 여러 인스턴스 사이의 S3 재시도 동시 실행 미방지 | 단일 인스턴스 운영 전제 |
| 기존 | `GlobalExceptionHandlerTest` 시간 의존 간헐 실패 | 별도 수정 필요 |
| 기존 | 공개 요약 API 요청 제한 없음 | 후속 과제 |
