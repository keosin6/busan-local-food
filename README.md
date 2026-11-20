# busan-local-food
부산 지역 기반 맛집 정보 및 리뷰 공유 플랫폼

사용자가 부산의 맛집을 등록·검색하고, 리뷰와 평점을 남기며, AI가 요약한 리뷰와 AI 맛집 추천을 받을 수 있는 웹 서비스입니다.

> **현재 상태 (`main` 기준, 기준 커밋 `d861d80`):** 프로젝트 기본 골격과 F1-01 회원·인증의 기반이 `main`에 구현되었습니다. 구현된 기반: 회원가입·로그인 검증 서비스, JWT Access Token 발급·검증, Refresh Token 발급·회전·재사용 탐지·로그아웃 폐기 서비스, 인증 요청 제한 서비스 R1~R10, 회원가입 요청 제한 HTTP 필터와 공통 HTTP 429 응답(PR #17), API 전용 JWT 인증 필터, API용 STATELESS SecurityFilterChain과 화면용 세션 SecurityFilterChain 분리, Bearer 및 `access_token` 쿠키 인증, API CSRF 정책(PR #19), API 인증 HTTP 5종과 JWT 쿠키 발급·갱신·만료, 로그인·재발급의 요청 제한 HTTP 연결(PR #23), 화면 회원가입·로그인·로그아웃과 화면 세션, T12 서버 측 로그아웃 승인(PR #25, PR #26). **Google 로그인과 Google 신규 가입은 `main` 병합 완료 (PR #28, merge commit `37df8e0`):** Google 로그인 시작(`/oauth2/authorization/google`)과 콜백(`/login/oauth2/code/google`), Google 연결 회원의 로그인, 같은 이메일의 기존 회원은 자동 연결하지 않고 안내, 미검증 이메일 거부, 신규 Google 회원의 닉네임 설정 화면(`/signup/google/nickname`)과 가입 완료 뒤 세션·JWT 발급, Google 로그인 요청 제한(IP별 15분 10회)과 닉네임 제출 요청 제한(IP별 1시간 5회). **Google 계정 연동·해제와 로컬 비밀번호 설정은 `main` 병합 완료 (PR #30, merge commit `05778895c03da90ad56570e5ddae3485acc97ba9`):** 로그인한 회원의 Google 계정 연결(`POST /mypage/google/link`, 콜백 뒤에도 기존 로그인 세션과 JWT 유지), 연결 해제(`POST /mypage/google/unlink`, 현재 비밀번호 확인, 비밀번호가 없는 회원은 먼저 설정해야 함), Google 전용 회원의 로컬 비밀번호 설정(`GET·POST /mypage/password`, 비밀번호 변경은 미지원), 로그인 회원 전용 마이페이지(`/mypage`, 미인증은 `/login`으로 이동), 화면용 429 안내 페이지(`/rate-limited`, GET과 POST 모두 처리, 재로그인 없이 대기 시간만 안내), Google 연결 시작·콜백의 요청 제한 공유(IP별 15분 10회). **프런트엔드 Access Token 자동 갱신(T13)은 `main` 병합 완료(PR #32, merge commit `31525ba`)입니다. 프런트엔드 429 처리(T20)는 PR #33(merge commit `d861d808f930d42519bb717ef978804460c7f24e`)으로 `main` 병합 완료되었습니다. 429는 인증 만료가 아니므로 쿠키와 화면 세션을 유지하고, 응답의 `Retry-After`로 재시도 가능한 시점을 안내합니다. 자동 재요청은 하지 않으며 사용자가 직접 다시 요청합니다. **아직 미구현:** `/mypage` 외 보호 대상 화면, F1-02 ~ F1-09(맛집 관리·이미지·리뷰·AI 요약·찜·AI 추천·메인 페이지·관리자 기능)는 미착수.

## 필수 기능

| ID | 기능 | 요약 |
|----|------|------|
| F1-01 | 회원 | 회원가입, 이메일 로그인, 로그아웃, 화면 세션 인증과 API JWT 인증(Refresh Token 로테이션), Google 로그인, 기존 계정에 Google 계정 직접 연동 |
| F1-02 | 맛집 | 맛집 등록·수정·삭제, 목록, 카테고리, 검색 |
| F1-03 | 이미지 | 맛집 이미지 AWS S3 업로드 |
| F1-04 | 리뷰 | 리뷰 작성·삭제, 평점(1~5), 평균 평점 |
| F1-05 | AI 리뷰 요약 | 리뷰 3개 이상일 때 AI 요약 생성 및 DB 캐싱 |
| F1-06 | 찜 | 찜하기/찜 취소, 내 찜 목록 |
| F1-07 | AI 맛집 추천 | DB에 등록된 맛집만 대상으로 AI 추천 |
| F1-08 | 메인 페이지 | 카테고리, 최신 맛집 |
| F1-09 | 관리자 | 전체 맛집·리뷰 관리, 회원 조회, 관리자 대시보드, S3 삭제 재시도 (`ADMIN` 권한) |

기능 상세는 [docs/FUNCTION_SPEC.md](docs/FUNCTION_SPEC.md)에 있습니다.

## 기술 스택 (고정)

| 구분 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.x, Spring Security, Spring Data JPA, Spring AI (도입 예정) |
| AI | Spring AI + OpenRouter 무료 모델 (Spring AI의 OpenAI 호환 클라이언트 사용). Spring AI는 AI 기능 구현 단계(F1-05, F1-07)에서 도입하며, 현재 `build.gradle`에는 없습니다 |
| 인증 | 화면: Spring Security 세션 / API: JWT (Refresh Token 로테이션) / Google 로그인 (OAuth2) |
| View | Thymeleaf, Bootstrap 5 |
| Database | MySQL 8 (개발은 개발 PC에 직접 설치, 운영은 AWS EC2 내부 설치, RDS 미사용) |
| Infra | AWS EC2 (Ubuntu, MySQL 포함), AWS S3 |
| CI/CD | GitHub Actions |
| Build | Gradle (`./gradlew bootJar`) |
| 개발 도구 | IntelliJ IDEA, Git, GitHub, Postman, MySQL Workbench |

기술 스택은 고정입니다. 바꿔야 할 이유가 생기면 [docs/DEVELOPMENT_RULES.md](docs/DEVELOPMENT_RULES.md)의 설계 변경 절차를 따릅니다.

## 문서 목록

| 문서 | 내용 |
|------|------|
| [docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md) | 프로젝트 목표, 범위, 사용자, 아키텍처 |
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) | 기능·비기능 요구사항 |
| [docs/FUNCTION_SPEC.md](docs/FUNCTION_SPEC.md) | 기능별 상세 명세(화면, 처리 흐름, 예외) |
| [docs/DATABASE_ERD.md](docs/DATABASE_ERD.md) | ERD, 테이블 정의, 제약 조건 |
| [docs/API_SPEC.md](docs/API_SPEC.md) | REST API 및 화면 URL 명세 |
| [docs/DEVELOPMENT_RULES.md](docs/DEVELOPMENT_RULES.md) | 개발 규칙, 브랜치·커밋 규칙, 보안 규칙 |
| [docs/PROGRESS.md](docs/PROGRESS.md) | 개발 진행 상황 기록 |

## 환경 변수

보안 정보는 코드나 저장소에 넣지 않고 환경 변수로 주입합니다. 전체 목록은 [docs/DEVELOPMENT_RULES.md](docs/DEVELOPMENT_RULES.md#5-보안-규칙)에 있습니다. 현재 애플리케이션이 실제로 읽는 변수는 DB 3개, JWT 3개(`JWT_SECRET`과 만료 시간 2개), `AUTH_RATE_LIMIT_HMAC_SECRET`, Google 2개(`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`)입니다. `DB_PASSWORD`, `JWT_SECRET`, `AUTH_RATE_LIMIT_HMAC_SECRET`이 없으면 기동하지 않습니다. Google 두 값은 함께 설정해야 하며, 한쪽만 있으면 기동하지 않고, 둘 다 없으면 local에서 Google 로그인만 꺼집니다.

```
DB_URL, DB_USERNAME, DB_PASSWORD
JWT_SECRET, JWT_ACCESS_TOKEN_EXPIRY_SECONDS, JWT_REFRESH_TOKEN_EXPIRY_SECONDS
AUTH_RATE_LIMIT_HMAC_SECRET
GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET
```

`AWS_REGION`, `AWS_S3_BUCKET`, `OPENROUTER_API_KEY`, `OPENROUTER_MODEL`은 계획된 변수이며 아직 애플리케이션이 읽지 않습니다.

## 실행 방법

### 준비물

- Java 21
- MySQL 8 (로컬 개발용)

### 1. 로컬 데이터베이스 준비

```bash
mysql -u root -p -e "CREATE DATABASE busan_local_food CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER 'busan'@'localhost' IDENTIFIED BY '<비밀번호>'; GRANT ALL PRIVILEGES ON busan_local_food.* TO 'busan'@'localhost';"
```

### 2. 환경변수 설정

비밀번호는 코드나 Git에 넣지 않고 환경변수로만 지정합니다. 로컬 프로필은 `DB_URL`(기본값 `jdbc:mysql://localhost:3306/busan_local_food`)과 `DB_USERNAME`(기본값 `busan`)에 기본값을 두므로 `DB_PASSWORD`만 지정하면 됩니다.

```bash
export DB_PASSWORD='<비밀번호>'
```

JWT 서명 키도 필요합니다. Base64로 인코딩한 32바이트 이상의 값이며, 없거나 형식이 잘못되면 기동이 실패합니다. 아래 명령으로 만든 값을 환경변수로만 지정하고 저장소에 넣지 않습니다.

```bash
export JWT_SECRET="$(openssl rand -base64 32)"
```

키를 바꾸면 이미 발급된 Access Token은 무효가 됩니다.

요청 제한의 HMAC 키도 같은 방식으로 필요합니다. `JWT_SECRET`과 달라야 하며, 같으면 기동이 실패합니다. 키를 바꾸는 절차는 [개발 규칙 5.8](docs/DEVELOPMENT_RULES.md)을 따릅니다.

```bash
export AUTH_RATE_LIMIT_HMAC_SECRET="$(openssl rand -base64 32)"
```

`JWT_ACCESS_TOKEN_EXPIRY_SECONDS`(기본 1800)와 `JWT_REFRESH_TOKEN_EXPIRY_SECONDS`(기본 1209600)는 선택입니다.

시간대는 애플리케이션이 `Asia/Seoul`로 고정합니다. `DB_URL`에 다른 시간대 옵션을 넣지 않습니다 ([개발 규칙 5.10](docs/DEVELOPMENT_RULES.md)).

### 2-1. 테이블 생성 (수동 SQL)

애플리케이션은 테이블을 만들지 않습니다. `db/manual/`의 SQL을 번호 순서대로 한 번씩 직접 실행합니다. 각 파일 머리말의 적용 전 확인 사항을 먼저 읽으세요.

```bash
mysql -u busan -p busan_local_food < db/manual/001_create_member_refresh_token.sql
```

```bash
mysql -u busan -p busan_local_food < db/manual/002_add_refresh_token_family_id.sql
```

```bash
mysql -u busan -p busan_local_food < db/manual/003_create_auth_rate_limit_tables.sql
```

### 3. 실행

```bash
./gradlew bootRun
```

브라우저에서 http://localhost:8080/ 에 접속합니다.

### 4. 테스트와 실행 가능한 JAR 빌드

```bash
./gradlew clean test
./gradlew bootJar
```

DB 연동 테스트는 기본으로 건너뜁니다. 실행하려면 개발 DB와 분리된 테스트 전용 데이터베이스에 SQL 001, 002, 003을 순서대로 적용하고 아래 변수를 지정합니다.

```bash
export TEST_DB_URL='jdbc:mysql://localhost:3306/<테스트 DB 이름>'
```

```bash
export TEST_DB_USERNAME='<사용자>'
```

```bash
export TEST_DB_PASSWORD='<비밀번호>'
```

JAR 실행에도 `DB_PASSWORD`, `JWT_SECRET` 등 같은 환경변수가 필요합니다.

```bash
java -jar build/libs/busan-local-food-0.0.1-SNAPSHOT.jar
```

### 5. 운영 실행 (EC2)

운영은 `prod` 프로필을 사용하고, 다음 일곱 환경 변수를 모두 서버 환경변수로 지정합니다. 기본값은 없습니다. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `AUTH_RATE_LIMIT_HMAC_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`. `JWT_SECRET`과 `AUTH_RATE_LIMIT_HMAC_SECRET`은 서로 다른 값이어야 하며, 같으면 기동이 실패합니다. `GOOGLE_CLIENT_ID`와 `GOOGLE_CLIENT_SECRET`은 함께 설정해야 하며, 한쪽만 있거나 둘 다 없으면 기동이 실패합니다. Google Cloud Console의 OAuth 클라이언트에 승인된 리디렉션 URI `https://<운영 도메인>/login/oauth2/code/google`을 등록해야 합니다. 로그인 시작 경로는 `GET /oauth2/authorization/google`입니다.

```bash
SPRING_PROFILES_ACTIVE=prod DB_URL='<서버 내부 MySQL 접속 URL>' DB_USERNAME='<사용자>' DB_PASSWORD='<비밀번호>' JWT_SECRET='<Base64 서명 키>' AUTH_RATE_LIMIT_HMAC_SECRET='<Base64 요청 제한 키>' GOOGLE_CLIENT_ID='<Google 클라이언트 ID>' GOOGLE_CLIENT_SECRET='<Google 클라이언트 시크릿>' java -jar busan-local-food.jar
```

### 문제 해결

| 증상 | 확인할 것 |
|------|-----------|
| `필수 환경변수가 설정되지 않았습니다: DB_PASSWORD` | 누락된 환경변수를 지정합니다. 목록에 표시된 변수만 확인하면 됩니다. |
| `AUTH_RATE_LIMIT_HMAC_SECRET 환경 변수가 설정되지 않았습니다` 또는 `AUTH_RATE_LIMIT_HMAC_SECRET은 JWT_SECRET과 다른 값이어야 합니다` | 요청 제한용 키를 Base64 32바이트 이상으로 지정하고, `JWT_SECRET`과 다른 값인지 확인합니다. |
| `JWT_SECRET 환경 변수가 설정되지 않았습니다` 또는 `JWT_SECRET은 Base64 형식이어야 합니다` | `JWT_SECRET`에 Base64로 인코딩한 32바이트 이상의 값을 지정합니다 (위 2번 참조). |
| `운영 환경에서는 GOOGLE_CLIENT_ID와 GOOGLE_CLIENT_SECRET을 설정해야 합니다` 또는 `GOOGLE_CLIENT_ID와 GOOGLE_CLIENT_SECRET은 함께 설정해야 합니다` | 두 값을 Google OAuth 클라이언트의 값으로 함께 지정합니다. local에서 둘 다 비우면 Google 로그인만 꺼지고 기동은 됩니다. |
| `데이터베이스 연결 또는 초기화에 실패했습니다` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`가 맞는지, MySQL 8이 실행 중인지, 사용자에게 권한이 있는지 확인합니다. 실패 원인 메시지가 함께 출력됩니다. |
