# busan-local-food
부산 지역 기반 맛집 정보 및 리뷰 공유 플랫폼

사용자가 부산의 맛집을 등록·검색하고, 리뷰와 평점을 남기며, AI가 요약한 리뷰와 AI 맛집 추천을 받을 수 있는 웹 서비스입니다.

> **현재 상태:** 프로젝트 기본 골격(Spring Boot, 설정, 공통 오류 응답, 기본 홈 화면)이 구현되었습니다. 기능 F1-01 ~ F1-09는 아직 구현 전입니다. 진행 상황은 [docs/PROGRESS.md](docs/PROGRESS.md)를 확인하세요.

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

보안 정보는 코드나 저장소에 넣지 않고 환경 변수로 주입합니다. 전체 목록은 [docs/DEVELOPMENT_RULES.md](docs/DEVELOPMENT_RULES.md#5-보안-규칙)에 있습니다. 현재 기본 골격이 실제로 읽는 변수는 DB 관련 3개입니다.

```
DB_URL, DB_USERNAME, DB_PASSWORD
JWT_SECRET, JWT_ACCESS_TOKEN_EXPIRY_SECONDS, JWT_REFRESH_TOKEN_EXPIRY_SECONDS
(이름 미정, 구현 단계 확정) 요청 제한 HMAC 비밀키
GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET
AWS_REGION, AWS_S3_BUCKET
OPENROUTER_API_KEY, OPENROUTER_MODEL
```

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

JAR 실행에도 같은 환경변수가 필요합니다.

```bash
java -jar build/libs/busan-local-food-0.0.1-SNAPSHOT.jar
```

### 5. 운영 실행 (EC2)

운영은 `prod` 프로필을 사용하고, 세 DB 변수를 모두 서버 환경변수로 지정합니다. 기본값은 없습니다.

```bash
SPRING_PROFILES_ACTIVE=prod DB_URL='<서버 내부 MySQL 접속 URL>' DB_USERNAME='<사용자>' DB_PASSWORD='<비밀번호>' java -jar busan-local-food.jar
```

### 문제 해결

| 증상 | 확인할 것 |
|------|-----------|
| `필수 환경변수가 설정되지 않았습니다: DB_PASSWORD` | 누락된 환경변수를 지정합니다. 목록에 표시된 변수만 확인하면 됩니다. |
| `데이터베이스 연결 또는 초기화에 실패했습니다` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`가 맞는지, MySQL 8이 실행 중인지, 사용자에게 권한이 있는지 확인합니다. 실패 원인 메시지가 함께 출력됩니다. |
