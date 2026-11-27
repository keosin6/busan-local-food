# 개발 규칙 (DEVELOPMENT_RULES)

이 문서는 프로젝트 전반의 개발 규칙입니다.

## 1. 원칙

1. **필수 기능은 임의로 삭제하거나 축소하지 않습니다.** F1-01 ~ F1-09는 모두 구현 대상입니다.
2. **설계를 바꾸기 전에 제안합니다.** 변경 이유, 대안, 영향 범위를 먼저 정리하고 승인을 받습니다 (4절).
3. **ERD와 API 명세를 일관되게 유지합니다.** 두 문서와 `FUNCTION_SPEC.md`는 항상 같은 커밋에서 바꿉니다.
4. **보안 정보는 코드에 직접 쓰지 않습니다.** (5절)
5. **기능은 테스트 가능한 단위로 개발합니다.** 기능 하나를 끝내면 테스트를 통과시킨 뒤 다음 기능으로 넘어갑니다.
6. **진행 상황은 `PROGRESS.md`에 기록합니다.**
7. **기존 파일은 덮어쓰기 전에 확인합니다.**

## 2. 기술 스택 (고정)

Java 21 · Spring Boot 3.x · Spring Security · Spring Data JPA · Spring AI 1.1.8(OpenAI 스타터, OpenRouter 호출, F1-05 도입) · OpenRouter 무료 모델 · Thymeleaf · Bootstrap 5 · MySQL 8 · AWS EC2(Ubuntu) · AWS S3 · GitHub Actions · Gradle

인증: 화면은 Spring Security 세션 로그인, API는 JWT, 소셜 로그인은 Google 로그인 (5.7절)

개발 도구: IntelliJ IDEA, Git, GitHub, Postman, MySQL Workbench

- 위 목록 밖의 라이브러리나 프레임워크는 설계 변경 절차(4절)를 거쳐야 추가할 수 있습니다. 사용자 승인 없이 공식 스택 밖의 기술을 추가하지 않습니다.
- Spring Boot Starter 내부에 포함되는 전이 의존성은 별도 기술 도입으로 보지 않습니다. Gradle의 Java 플러그인과 Spring Dependency Management 플러그인은 빌드 지원 도구로 유지합니다.
- 라이브러리 버전은 Spring Boot BOM과 Spring AI BOM을 따르고, 임의로 고정하지 않습니다.
- 테스트 도구는 `spring-boot-starter-test`(JUnit 5, Mockito, AssertJ, Spring MockMvc)를 기준으로 합니다. Testcontainers, H2, Docker 기반 테스트 환경은 도입하지 않습니다 (T9 확정, 7절).

## 3. 브랜치·커밋·PR 규칙

### 3.1 브랜치
- `main`에는 직접 push하지 않습니다.
- 작업은 기능 또는 문서 단위 브랜치에서 합니다. 예: `docs/architecture-decisions-q1-q6`, `feat/f1-01-auth`, `feat/f1-02-restaurant`.
- 자동화 세션에서 지정한 작업 브랜치(`claude/...`)가 있으면 그 브랜치를 사용합니다.

### 3.2 커밋
- 한 커밋은 한 가지 변경만 담습니다.
- 메시지 형식: `<type>: <요약>` (type: `docs`, `feat`, `fix`, `test`, `refactor`, `chore`).
- 예: `docs: 기초 문서 구조 추가`, `feat(F1-02): 맛집 등록 API 구현`.
- 커밋 메시지에 보안 정보와 모델 식별자를 넣지 않습니다.

### 3.3 Pull Request
- 사용자가 요청할 때만 생성합니다.
- 저장소에 PR 템플릿이 있으면 그 섹션 구조를 따릅니다.
- 기능 PR에는 해당 기능 ID(F1-0X), 변경한 문서, 테스트 결과를 적습니다.

## 4. 설계 변경 절차

설계 변경은 아래 순서로 진행합니다.

1. **변경 제안서 작성**: 무엇을 왜 바꾸는지, 대안 2개 이상, 각 대안의 장단점을 적습니다.
2. **영향 범위 확인**: `REQUIREMENTS.md`, `FUNCTION_SPEC.md`, `DATABASE_ERD.md`, `API_SPEC.md` 중 바뀌는 문서를 목록으로 만듭니다.
3. **승인**: 사용자가 승인하기 전에는 구현하지 않습니다.
4. **반영**: 관련 문서를 같은 커밋에서 함께 고칩니다.
5. **기록**: `PROGRESS.md`의 "설계 변경 이력"에 날짜와 요약을 남깁니다.

변경 제안서 예시 형식:

```
제목: 리프레시 토큰 보관 방식 변경
이유: ...
대안 A (현재): DB 해시 저장 — 장점: 강제 폐기 가능 / 단점: 조회 1회 추가
대안 B: 무상태 리프레시 토큰 — 장점: DB 부담 없음 / 단점: 강제 폐기 어려움
영향 문서: DATABASE_ERD.md, API_SPEC.md(2.3, 2.4), FUNCTION_SPEC.md(F1-01)
결정 필요: 사용자 승인
```

## 5. 보안 규칙

### 5.1 보안 정보 관리
- 다음 값은 코드, 설정 파일, 문서, 커밋, 로그에 직접 쓰지 않습니다.
  - DB 접속 정보 · JWT 서명 키 · Google OAuth 클라이언트 ID/Secret · AWS 액세스 키 · OpenRouter API 키
- 모든 보안 값은 환경 변수로 주입합니다. Spring에서는 `${ENV_NAME}` 형태로 참조합니다.
- 로컬 개발용 값은 개인 `.env` 파일 또는 IDE 실행 설정에 두고, `.gitignore`로 제외합니다.
- 문서 예시는 `<...>` 자리표시자를 씁니다.

### 5.2 환경 변수 목록

| 변수 | 용도 |
|------|------|
| `DB_URL` | MySQL JDBC URL (개발과 운영에서 값이 다름, 5.5 참조) |
| `DB_USERNAME` | DB 사용자 |
| `DB_PASSWORD` | DB 비밀번호 |
| `JWT_SECRET` | Base64로 인코딩한 JWT 서명 키. 디코딩 후 32바이트 이상. 누락, 형식 오류, 길이 부족 시 기동 실패 (5.9) |
| `JWT_ACCESS_TOKEN_EXPIRY_SECONDS` | 액세스 토큰 만료 초 (기본 1800) |
| `JWT_REFRESH_TOKEN_EXPIRY_SECONDS` | 리프레시 토큰 만료 초 (기본 1209600) |
| `AUTH_RATE_LIMIT_HMAC_SECRET` | 요청 제한 키의 HMAC-SHA256 비밀키. Base64 인코딩, 디코딩 후 32바이트 이상. 누락·형식 오류·길이 부족이면 기동 실패. `JWT_SECRET`과 달라야 하며 같으면 기동 실패. 기본값과 개발용 고정 키를 두지 않는다. EC2 환경 변수로만 주입하며 Git에 커밋하지 않는다 (R2) |
| `app.security.trusted-proxies` (설정 프로퍼티, 환경 변수 아님) | 신뢰 프록시 IP 목록. 비어 있으면 `X-Forwarded-For`를 신뢰하지 않는다. 운영 기본값을 코드에 두지 않는다 |
| `GOOGLE_CLIENT_ID` | Google OAuth 클라이언트 ID. 코드, 설정 파일, 로그에 값을 두지 않는다. `GOOGLE_CLIENT_SECRET`과 함께 설정한다. 둘 다 없으면 local에서는 Google 로그인이 꺼지고, prod에서는 기동이 실패한다. 한쪽만 있으면 항상 기동이 실패한다 (6.21) |
| `GOOGLE_CLIENT_SECRET` | Google OAuth 클라이언트 시크릿. `GOOGLE_CLIENT_ID`와 같은 규칙을 따르며, 로그와 오류 메시지에 값을 남기지 않는다 (6.21) |
| `AWS_REGION` | S3 리전 |
| `AWS_S3_BUCKET` | 이미지 버킷 이름 |
| `OPENROUTER_API_KEY` | OpenRouter API 키 |
| `OPENROUTER_MODEL` | 사용할 모델 ID (프로필별로 다른 값 가능, 5.4 참조) |

> 이 목록이 바뀌면 `README.md`의 환경 변수 절도 함께 수정합니다.

### 5.3 애플리케이션 보안
- 비밀번호는 BCrypt로 해시합니다.
- 쓰기 API는 인증과 권한 검사를 모두 거칩니다. 권한 검사는 컨트롤러가 아니라 서비스 계층에서 수행합니다.
- 관리자 권한(`ADMIN`)은 Spring Security 규칙(`/admin/**`, `/api/admin/**`)과 서비스 계층 검사로 **서버에서** 검증합니다. URL을 숨기는 것만으로는 보호하지 않습니다 (FR-09-08).
- 일반 회원이 자신의 `role`을 바꾸는 API나 입력 경로를 만들지 않습니다 (FR-09-09).
- 관리자 서비스 진입점은 `AdminAccess.requireAdmin`으로 인증 정보의 `ROLE_ADMIN`을 먼저 확인합니다. 요청의 role 값은 신뢰하지 않습니다. 권한이 없으면 `FORBIDDEN`, 인증이 없으면 `TOKEN_INVALID`입니다.
- 초기 관리자 지정 절차 (T2 A안): 관리자 화면과 API에는 역할 변경 기능이 없습니다. 운영자가 MySQL에서 직접 지정합니다.
  1. 대상 회원의 이메일을 확인합니다.
  2. 아래 SQL을 실행합니다. `실제_관리자_이메일`은 운영자가 확인한 값으로 바꿉니다. 코드, 테스트, Git에는 실제 이메일을 남기지 않습니다.
  3. 변경된 행 수가 1인지 확인하고, `SELECT id, email, role FROM member WHERE email = '실제_관리자_이메일';`로 role이 `ADMIN`인지 조회합니다.
  4. 역할은 다음 로그인 또는 토큰 재발급부터 반영됩니다.

  ```sql
  UPDATE member
  SET role = 'ADMIN'
  WHERE email = '실제_관리자_이메일'
    AND role = 'USER';
  ```
- 쿠키 속성:
  - `JSESSIONID`(화면 세션): `HttpOnly`, `SameSite=Lax`, 운영 환경에서는 `Secure`.
  - `access_token`(API JWT): `HttpOnly`, `SameSite=Lax`, 운영 환경에서는 `Secure`. Path는 `/api`로 제한합니다.
  - `refresh_token`(API JWT): `HttpOnly`, `SameSite=Lax`, 운영 환경에서는 `Secure`. Path는 `/api/auth`로 제한합니다.
- 모든 인증 쿠키는 `Domain`을 설정하지 않습니다. 쿠키 속성은 API_SPEC 1.1을 따릅니다.
- Refresh Token 원문은 쿠키에만 두고, DB에는 SHA-256 해시만 저장합니다 (FR-01-05). 로그와 응답 본문에도 원문을 남기지 않습니다.
- CSRF: 화면 세션 쿠키 기반 상태 변경 요청과 JWT 쿠키 기반 API 상태 변경 요청 모두에 Spring Security CSRF 보호를 유지합니다. 상태 변경 요청은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보냅니다. `Authorization` 스킴이 `Bearer`인 요청은 쿠키 기반 인증이 아니므로 CSRF 헤더를 요구하지 않습니다. 그 밖의 Authorization 스킴과 쿠키 인증 요청은 CSRF를 검사합니다.
- JWT 원문(Access Token, Refresh Token)은 응답 본문과 로그에 남기지 않습니다. 쿠키로만 전달합니다.
- 요청 제한: 로그인, 회원가입, Refresh, Google OAuth2는 API_SPEC 1.4의 기준을 따릅니다. 429 응답은 JWT 쿠키를 바꾸지 않습니다.
- 로그인 제한 이벤트에는 정규화 이메일과 IP를 HMAC-SHA256 값으로만 저장합니다 (R2). 원문 이메일과 원문 IP는 저장하지 않습니다. HMAC 비밀키는 EC2 환경 변수로 주입하고 Git에 커밋하지 않습니다. 회원가입과 Google 제한의 IP 키도 같은 방식입니다 (T16).
- 클라이언트 IP는 설정된 신뢰 프록시가 전달한 `X-Forwarded-For` 값만 신뢰합니다. 신뢰 프록시 목록은 구현 단계에서 설정 값으로 지정합니다 (T16 확정).
- 신뢰 프록시가 아닌 요청의 `X-Forwarded-For`는 무시하고 Remote Address를 사용합니다.
- 판별한 IP는 HMAC-SHA256으로 변환하여 저장과 조회 키로만 사용합니다. 원본 IP는 이벤트 데이터와 일반 애플리케이션 로그에 불필요하게 남기지 않습니다.
- `family_id`(Refresh 계열 UUID)는 서버가 생성하는 가명 식별자입니다. 직접 식별 정보는 아니지만 회원과 인증 세션에 연결될 수 있으므로 개인정보 및 보안 정보에 준하여 접근을 통제합니다. 로그에 원문 값을 남기지 않는 것을 원칙으로 하며, Refresh Token 원문과 마찬가지로 외부에 노출하지 않습니다.
- 회원가입 제한, Google OAuth2 제한, 로그인 IP 보조 제한을 포함한 모든 IP 기반 식별자는 HMAC-SHA256으로 저장합니다 (T16 확정).
- Thymeleaf의 `th:text`를 사용하고, `th:utext`로 사용자 입력을 출력하지 않습니다.
- 파일 업로드는 MIME 타입과 확장자, 크기를 모두 검사합니다.
- 에러 응답에 스택 트레이스나 SQL을 노출하지 않습니다.
- 로그에 비밀번호, 토큰, API 키, 리뷰 원문 대량 출력을 남기지 않습니다.
- S3 오류를 `storage_delete_task.last_error`에 저장할 때는 자격 증명, 서명 값, 요청 헤더를 제거한 짧은 메시지만 저장합니다.

### 5.4 AI 응답 검증 및 보안 규칙 (Spring AI · OpenRouter)
- AI 호출은 서버에서만 합니다. 브라우저에는 API 키를 내려주지 않습니다.
- OpenRouter 설정은 환경 변수로만 받습니다.

  ```
  OPENROUTER_API_KEY=${OPENROUTER_API_KEY}
  OPENROUTER_MODEL=${OPENROUTER_MODEL}
  ```

- Spring AI는 `spring-ai-starter-model-openai`를 쓰고, 버전은 `spring-ai-bom`이 정합니다(현재 1.1.x, Spring Boot 3.5와 맞춤). OpenRouter의 OpenAI 호환 주소는 `app.ai.openrouter.base-url`이며, `OpenAiApi`에 제한 시간을 걸어 만듭니다. `ChatModel`은 스타터 자동 구성을 씁니다.
- 채팅 모델만 켭니다(`spring.ai.model.chat=openai`). 임베딩·이미지·음성·모더레이션은 `none`으로 끕니다. 켜 두면 키가 없을 때 기동이 실패합니다.
- AI 호출은 자동 재시도하지 않습니다(`spring.ai.retry.max-attempts=1`). 실패하면 한 번만 부르고 UNAVAILABLE로 응답합니다. 재시도로 호출량이 늘지 않게 합니다.
- **모델 ID를 Java 코드에 고정하지 않습니다.** 모델을 바꿀 때 코드 수정 없이 환경 변수만 바꿀 수 있어야 합니다 (NFR-18).
- 개발(`local`)과 운영(`prod`) 프로필은 서로 다른 `OPENROUTER_MODEL` 값을 쓸 수 있습니다.
- 특정 무료 모델 ID는 이 문서에서 확정하지 않습니다 (T10).
- 사용자 입력(취향 문장)과 리뷰 내용은 데이터로만 전달하고, 시스템 지시를 따르지 않도록 프롬프트에 명시합니다.
- AI가 반환한 맛집 ID는 항상 DB 후보 목록과 대조합니다. 목록에 없는 ID는 버립니다 (FR-07-03).
- AI 응답이 기대한 JSON 형식이 아니면 결과를 노출하지 않고 `AI_RESPONSE_INVALID`로 처리합니다.
- AI 호출은 타임아웃(기본 10초)을 둡니다. 실패가 상세 화면 전체를 막지 않도록 합니다 (FR-05-06).

### 5.5 데이터베이스 운영 규칙 (Q4)

운영 DB는 **AWS EC2 내부에 설치한 MySQL 8**입니다. RDS는 사용하지 않습니다.

- **환경 분리**
  - 개발(`local`): 개발 PC에 MySQL 8을 직접 설치합니다. Docker나 컨테이너 기반 DB는 사용하지 않습니다. 운영 DB 접속 정보를 개발 환경에 두지 않습니다.
  - 테스트: 개발 PC에 설치된 MySQL 8의 테스트 전용 데이터베이스를 사용합니다. 개발 데이터베이스와 분리합니다 (7절).
  - Testcontainers, H2 등 테스트용 DB 라이브러리는 도입하지 않습니다 (T9 확정).
  - 운영(`prod`): EC2 내부 MySQL 8. 접속 정보는 서버 환경 변수로만 주입합니다.
  - 두 환경의 DB 설정은 Spring 프로필(`local`, `prod`)로 나눕니다 (NFR-11).
- **네트워크**
  - 3306 포트는 외부 인터넷에 공개하지 않습니다. 보안 그룹에서 외부 접근을 차단합니다 (NFR-14).
  - 애플리케이션은 EC2 내부에서 MySQL에 접속합니다. 운영자의 DB 접속도 서버 내부 경로로만 합니다.
- **영속성**
  - MySQL 데이터 디렉터리는 서버나 컨테이너를 다시 만들어도 남는 영속 저장소에 둡니다 (NFR-13). 구체 구성은 T5에서 확정합니다.
- **백업**
  - 정기 백업을 수행합니다. 주기, 보관 기간, 저장 위치, 복구 절차는 T4에서 확정하고, 확정 전에는 운영 DB를 배포하지 않습니다.
- **이식성 (RDS 이전 대비, NFR-16)**
  - DB 접속 정보는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경 변수로만 받습니다. 코드에 호스트명을 쓰지 않습니다.
  - MySQL 전용 기능(예: 특정 함수, 저장 프로시저)에 기대는 코드는 리포지토리 계층에 격리합니다.
  - 추후 RDS로 옮길 때 애플리케이션 코드를 바꾸지 않고 환경 변수만 바꾸는 것을 목표로 합니다.

### 5.6 S3 삭제 규칙 (Q2)

DB와 S3는 분산 트랜잭션으로 묶지 않습니다. 아래 순서를 지킵니다.

1. DB 트랜잭션에서 행을 삭제하고, 같은 트랜잭션에서 `storage_delete_task`에 S3 키를 기록합니다. 그 후 커밋합니다.
2. 커밋 후 S3 객체를 삭제합니다.
   - 성공하면 `storage_delete_task` 행을 삭제합니다.
   - 실패하면 `retry_count`와 `last_error`를 갱신합니다. DB 삭제는 되돌리지 않습니다.
3. 재시도는 관리자 API로 합니다 (F1-09). 자동 재시도 여부는 T6에서 정합니다.
4. S3에 객체가 없으면 삭제 성공으로 처리합니다 (재시도가 중복되어도 안전해야 합니다).
5. 문서와 코드 주석, 응답 메시지에서 "DB와 S3가 함께 롤백된다" 또는 "원자적으로 처리된다"고 표현하지 않습니다.

### 5.7 인증 구조 (화면 세션 + API JWT, 2026-11-10 확정)

최종 결정: **화면은 Spring Security 세션, API는 JWT로 인증합니다.** 두 인증 수단은 서로 독립적으로 동작하며 서로 대체할 수 없습니다.

| 구분 | 화면 인증 | API 인증 |
|------|-----------|----------|
| 기술 | Spring Security 세션 (`JSESSIONID`) | JWT (`access_token` 쿠키 또는 Bearer) |
| 적용 경로 | 일반 화면 URL (`/`, `/login`, `/mypage` 등) | `/api/**` |
| 인증 정보 | 세션에 저장된 회원 정보 | JWT Access Token |
| 인증 유지 | 세션 만료 또는 화면 로그아웃까지 | Access Token 만료까지 (Refresh Token으로 재발급) |
| 로그인 방식 | 화면 로그인(`POST /login`), 화면 회원가입(`POST /signup`), Google 로그인 | API 로그인(`POST /api/auth/login`), API 회원가입(`POST /api/auth/signup`) |
| CSRF | 활성 | 쿠키 기반 상태 변경 요청에 활성 |

원칙:
1. 화면 요청은 유효한 Spring Security 세션으로만 인증합니다. JWT만으로 화면 로그인 상태를 인정하지 않습니다.
2. API 요청(`/api/**`)은 JWT 검증을 통과해야 합니다. 세션만으로 API 로그인 상태를 인정하지 않습니다 (FR-01-16, FR-01-18).
3. 화면 로그인, 화면 회원가입 후 자동 로그인, Google 로그인이 성공하면 세션과 JWT를 동시에 발급합니다 (FR-01-17).
4. API 로그인과 API 회원가입은 JWT만 발급합니다. Spring Security 세션을 만들지 않습니다.
5. 화면 로그인으로 발급된 JWT는 API 인증에 사용할 수 있습니다.
6. Google 계정 연동은 기존 로그인 세션의 회원을 대상으로 합니다. 연동 대상 회원 ID는 연동 시작 시점의 로그인 세션에서 확보합니다 (5.7.1, T11 A안).
7. OAuth2 임시 세션은 Google 인증 진행 상태(`state`, Authorization Request, 흐름 종류, 연동 요청 회원 ID 또는 신규 가입 진행 상태)만 담습니다. 로그인 상태(회원 권한)는 담지 않습니다. 흐름이 끝나면(성공이든 실패든) 제거하며, `state`는 한 번만 사용합니다 (FR-01-14).
8. 화면 체인은 HTTP 세션을 사용하고, `/api/**` 체인은 STATELESS입니다. 세션 만료 시간 등 운영 설정 값은 구현 단계에서 정합니다 (T11 A안). 세션 정책을 전체적으로 Stateless로 고정하지 않습니다.

SecurityFilterChain 분리 (구현 기준):
- **API 체인**: RequestMatcher `/api/**`. JWT 인증 필터를 적용합니다. 세션 인증을 사용하지 않고 `STATELESS` 정책을 씁니다. 인증 실패는 JSON 오류 응답(API_SPEC 1.2)으로 반환합니다.
- **화면 체인**: 나머지 화면 URL. Spring Security 세션 인증을 사용합니다. 화면 로그인(`/login`), 화면 로그아웃, Google OAuth2 로그인과 콜백(`/oauth2/**`, `/login/oauth2/**`)을 처리합니다. 인증 실패는 로그인 페이지로 리다이렉트합니다.
- `/oauth2/**`와 `/login/oauth2/**`는 API 체인에 포함하지 않습니다.
- 두 체인의 RequestMatcher가 겹치지 않아야 합니다. API 인증 실패가 로그인 리다이렉트로, 화면 인증 실패가 JSON으로 처리되지 않아야 합니다.
- 구현 상태 (6.16, 이 브랜치 병합 전): API 체인과 JWT 인증 필터, 화면 체인 분리는 구현되었습니다. 화면 체인의 로그인 리다이렉트와 OAuth2 체인은 아직 구현되지 않았습니다.
- 인증 수단 선택 (API 체인, API_SPEC 1.0): `Authorization` 헤더가 있으면 쿠키는 보지 않습니다. 헤더의 스킴이 `Bearer`(대소문자 무시)이고 토큰이 유효하면 인증합니다. 헤더가 있는데 스킴이 `Bearer`가 아니거나(예: Basic) 토큰이 비었거나 잘못되었으면 쿠키로 대체하지 않고 401입니다. 헤더 자체가 없으면 `access_token` 쿠키를 봅니다.
- CSRF 적용 (API 체인): 상태 변경 메서드(POST, PUT, PATCH, DELETE)는 검증합니다. 면제는 `Authorization` 헤더의 스킴이 `Bearer`(대소문자 무시)인 요청에만 적용합니다. 헤더 존재 자체로는 면제하지 않습니다. Basic 등 다른 스킴, 쿠키 인증 요청, 헤더 없는 익명 요청은 검증합니다. 면제 판단은 스킴만 보며 토큰 값 검증과 분리합니다. 잘못된 Bearer 토큰은 CSRF 면제 후 인증 단계에서 401입니다. CSRF 필터는 JWT 필터보다 먼저 실행되고 인증 결과에 의존하지 않습니다. CSRF 전체 비활성화는 하지 않습니다.
- API 체인의 CSRF 토큰 저장소는 `XSRF-TOKEN` 쿠키(`CookieCsrfTokenRepository`)입니다. 세션을 만들지 않고도 CSRF를 검증하기 위해서입니다. 쿠키 속성은 API_SPEC 1.1과 같습니다: HttpOnly 없음, SameSite=Lax, Path=/, 운영 프로필에서만 Secure. 화면 체인은 세션 기반 저장소를 그대로 씁니다.
- API 체인의 인증 없이 허용하는 경로는 API_SPEC 2.1~2.4의 POST `/api/auth/signup`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout` 네 개뿐입니다. 공개 조회 API는 구현 시 경로를 명시해서 추가합니다. `/api/**` 전체를 공개하지 않습니다.
- JWT 인증 필터와 회원가입 집계 필터는 서블릿 빈으로 등록하지 않고 체인에 직접 추가합니다. 매 요청 새 SecurityContext를 만들고, 이전 인증 정보를 이어받지 않습니다.

쿠키와 로그아웃:
- 쿠키 속성과 Path는 API_SPEC 1.1절을 따릅니다. `JSESSIONID`는 화면 세션, `access_token`은 Path `/api`, `refresh_token`은 Path `/api/auth`입니다.
- 화면 로그아웃(`POST /logout`)은 세션을 무효화하고 JWT 쿠키를 만료시킵니다. 화면 로그아웃은 API 로그아웃을 먼저 성공시킨 뒤에만 실행합니다 (5.7.2, T12 A안).
- API 로그아웃(`POST /api/auth/logout`)은 Refresh Token을 폐기하고 JWT 쿠키를 만료시킵니다. 화면 세션은 종료하지 않습니다.

### 5.7.1 OAuth2 임시 상태와 Google 계정 연동 (T11 A안 확정)

구현 확정 (PR B, 6.22):
- 연동 시작은 `POST /mypage/google/link`에서 Authorization Request를 만든다. Spring의 GET 시작 경로를 거치지 않는다. 그 경로는 일반 로그인 시작으로도 집계되기 때문이다.
- 연동 상태(회원 ID, state, 15분)는 세션에 두며, 만료되었으면 일반 로그인으로 처리하지 않고 `/mypage?error=google_failed`로 끝낸다.
- 연동 모드에서는 세션 ID를 바꾸지 않는다. 일반 로그인 시작은 남아 있던 연동 상태를 지운다.

확정 원칙:
1. `/api/**` 체인은 기존 정책대로 JWT 인증만 허용합니다. Google 계정 연동 요청을 API JWT 전용 체인에 세션 인증으로 허용하지 않습니다.
2. Google 계정 연동 시작은 화면 전용 URL에서 처리합니다. 화면 체인은 HTTP 세션으로 인증합니다. 연동 시작 요청은 CSRF 토큰을 포함한 POST입니다. 정확한 경로는 구현 단계에서 확정합니다.
3. OAuth2 임시 인증 상태(`state`, Authorization Request)는 기존 HTTP 세션에 저장합니다. 기본 구현은 Spring Security의 `HttpSessionOAuth2AuthorizationRequestRepository`를 사용합니다. OAuth2 `state` 검증은 Spring Security의 기존 절차를 유지합니다.
4. 연동 대상 회원 ID와 연동 진행 상태는 연동 시작 시점의 로그인 세션에서 확보하여 같은 세션에 저장합니다.
5. Google 일반 로그인과 Google 계정 연동은 처리 경로를 분리합니다. 두 기능은 같은 콜백 URL을 사용하지만, 세션에 연동 진행 상태가 있을 때만 계정 연동 처리를 수행합니다. 계정 연동 처리는 일반 로그인 성공 처리(세션 발급, JWT 발급)를 호출하지 않습니다.
6. 계정 연동 과정에서 기존 로그인 세션과 API JWT를 유지합니다. 세션 무효화, JWT 교체, Refresh Token 재발급을 하지 않습니다.
7. 임시 상태는 흐름이 끝나면(성공이든 실패든) 제거합니다. `state`는 한 번만 사용합니다 (FR-01-14).
8. 일반 로그인을 시작하면 남아 있는 연동 진행 상태를 제거합니다. 연동을 시작하면 남아 있는 일반 로그인 진행 상태를 제거합니다.

계정 연동 콜백 처리 (기존 세션 기준):
1. OAuth2 `state`를 검증합니다.
2. 세션의 연동 진행 상태를 확인합니다. 없으면 계정 연동이 아니므로 일반 로그인 처리로 분기합니다.
3. 세션에 저장된 연동 대상 회원 ID를 확인합니다. 확인할 수 없으면 연동을 거부합니다.
4. Google 인증 코드를 교환하고 Google 사용자 정보를 조회합니다.
5. Google `sub` 식별자를 확인합니다.
6. 해당 `google_sub`가 다른 회원에게 이미 연결되어 있는지 확인합니다.
7. 중복되지 않으면 연동 대상 회원의 `google_sub`를 저장합니다. `uk_member_google_sub` 제약이 중복 연결을 한 번 더 막습니다.
8. 연동 완료로 처리합니다.
9. 연동 관련 임시 세션 정보(연동 진행 상태, `state`)를 정리합니다.

계정 연동 원칙:
- 연동 대상 회원은 연동 시작 시점의 로그인 세션에 저장된 회원 ID로 정합니다. Callback 시점의 Google 이메일이나 새로 생성된 인증 주체로 판단하지 않습니다.
- Google 이메일이 기존 회원과 같다는 이유만으로 자동 연결하지 않습니다.
- 기존 세션을 새 Google 사용자 인증으로 대체하지 않습니다.
- 실패하면 기존 세션과 JWT를 변경하지 않고, 임시 상태만 정리합니다.

Google 일반 로그인 처리 (계정 연동과 분리):
- Google `sub`로 회원을 조회합니다. 있으면 로그인합니다.
- 없고 같은 이메일의 기존 회원이 있으면 자동 연결하지 않고 `email_account_exists`로 거부합니다 (FR-01-09).
- 둘 다 없으면 회원을 만들지 않고 신규 가입 절차로 진입합니다 (5.7.3, T3 A안).
- 로그인 성공 시 화면 세션과 API JWT를 함께 발급합니다 (FR-01-17).

Callback 핸들러 원칙 (구현 필수 요구, T11 A안의 보존 조건):
1. 일반 Google 로그인과 계정 연동은 같은 Callback URL을 사용하지만, 사용자 정의 `AuthenticationSuccessHandler`에서 세션의 연동 진행 상태로 처리 경로를 구분합니다.
2. 연동 진행 상태가 없으면 일반 Google 로그인입니다. 기존 성공 처리 흐름을 유지하여 화면 세션과 API JWT를 발급합니다 (FR-01-17).
3. 연동 진행 상태가 있으면 계정 연동입니다. Spring Security 기본 Google 로그인 성공 처리가 기존 로컬 회원의 `SecurityContext`를 대체하지 않도록 합니다. 기존 로컬 회원의 인증 상태와 세션을 보존하고, Google 인증 결과는 `member.google_sub` 연결에만 사용합니다.
4. Spring Security의 OAuth2 로그인 필터는 인증 성공 시 Google 인증 객체를 SecurityContext에 먼저 설정할 수 있습니다. 계정 연동 모드에서는 이를 기존 로컬 회원의 인증 상태로 되돌리거나 저장을 막아야 합니다. 되돌리는 방법은 구현 단계에서 정하되, 결과 조건(기존 인증 상태 유지)은 반드시 지켜야 합니다.
5. 계정 연동 모드에서는 기존 세션 ID를 바꾸는 세션 보호 처리를 호출하지 않습니다. 기존 Access Token과 Refresh Token을 재발급하거나 교체하지 않습니다. 쿠키도 다시 설정하지 않습니다.
6. 사용자 정의 `AuthenticationFailureHandler`는 Google 인증 실패와 Callback 검증 실패를 처리합니다. 연동 모드에서는 연동 임시 상태(진행 상태, `state`)를 정리하고 기존 로그인 상태와 JWT를 유지한 채 `/mypage?error=...`로 이동합니다. Spring Security 필터가 인증 실패 시 SecurityContext를 비울 수 있으므로, 연동 모드 실패 처리는 기존 로그인 상태를 복원합니다.
7. OAuth2 `state` 검증은 Spring Security의 기존 검증 절차를 그대로 사용합니다. 연동 모드의 업무 검증(연동 대상 회원 확인, `email_verified`, `google_sub` 중복)은 성공 핸들러 안에서 수행하고, 실패하면 `/mypage?error=...`로 이동하며 연동 임시 상태를 정리합니다.

### 5.7.2 로그아웃과 토큰 재발급 (T12·T13 A안 확정)

화면 로그아웃 (T12 A안):
1. 브라우저가 `POST /api/auth/logout`을 먼저 호출합니다. 쿠키 기반 상태 변경 요청이므로 `X-XSRF-TOKEN` 헤더를 포함합니다.
2. 서버는 `refresh_token` 쿠키를 확인하고 DB에서 해당 Refresh Token 행을 폐기(`revoked_at` 기록)합니다.
3. 서버는 `access_token`, `refresh_token` 쿠키를 만료시킵니다.
4. 1이 성공한 경우에만 브라우저가 `POST /logout`을 호출합니다 (CSRF 토큰 필요).
5. 화면 서버는 Spring Security 세션을 무효화하고 SecurityContext를 제거하며 `JSESSIONID`를 만료시킵니다.
6. 로그아웃이 끝나면 로그인 화면(`/login`)으로 이동합니다.

예외:
- 1이 실패하면 화면 세션을 종료하지 않습니다. 로그인 상태를 유지하고 재시도를 안내합니다. 오류 종류(네트워크, 서버)와 관계없이 같은 처리를 합니다.
- 1은 성공했지만 4가 실패하면 API 토큰은 이미 폐기된 상태입니다. 화면 로그아웃만 재시도할 수 있게 합니다.
- 중복 로그아웃 요청은 안전하게 처리합니다. 이미 폐기된 토큰의 1은 `200`으로 응답하고, 4는 세션이 없어도 `/login`으로 이동합니다.
- `refresh_token` 쿠키의 Path는 `/api/auth`를 유지합니다. `/logout`에서 해당 쿠키를 직접 읽도록 변경하지 않습니다.
- 이유: `refresh_token` 쿠키는 `/api/auth` 경로에서만 전송되므로, 화면 로그아웃 요청(`/logout`)에서는 Refresh Token 행을 폐기할 수 없습니다. 그래서 API 로그아웃을 선행합니다.
- 이 순서는 브라우저의 화면 스크립트가 수행합니다.

Access Token 만료 시 자동 재발급 (T13 A안):
1. 화면에서 API를 호출했는데 Access Token 만료로 `401 TOKEN_INVALID`를 받습니다.
2. 브라우저가 `POST /api/auth/refresh`를 호출합니다. HttpOnly `refresh_token` 쿠키가 자동 전송되며, `X-XSRF-TOKEN` 헤더를 포함합니다.
3. 서버는 Refresh Token을 검증하고 로테이션을 수행합니다 (5.7.4).
4. 서버가 새 `access_token`, `refresh_token` 쿠키를 설정합니다.
5. 재발급이 성공하면 처음 실패한 API 요청을 한 번 다시 보냅니다.

세부 규칙:
- 재시도는 원래 요청당 최대 1회입니다. 재시도한 요청도 401이면 다시 재발급하지 않습니다.
- 같은 브라우저의 여러 탭 사이 갱신은 Web Locks API로 조정합니다 (T14 A안, 아래 "동시 갱신 절차").
- 재발급 API(`/api/auth/refresh`)가 401을 반환하면 재발급 API를 다시 호출하지 않습니다.
- 여러 API 요청이 동시에 401을 받아도 재발급 요청은 한 번만 실행합니다. 나머지 요청은 그 결과를 기다린 뒤 같은 결과를 사용합니다.
- 재발급이 실패하면 재로그인을 안내합니다. 재발급 실패 원인이 Refresh Token 재사용 탐지(5.7.4)로 전체 토큰이 폐기된 경우에도 같은 안내를 합니다.
- 재발급 요청이 429를 받으면 쿠키를 유지하고, 자동 로그아웃이나 재로그인을 하지 않습니다. 사용자에게 `Retry-After` 기준의 재시도를 안내하며 무한 재시도하지 않습니다 (T20 확정).
- Refresh Token 원문은 JavaScript에서 읽거나 저장하지 않습니다. `refresh_token`은 HttpOnly 쿠키로만 관리하며, 스크립트는 쿠키가 전송되도록 요청만 합니다.
- 화면 Spring Security 세션은 API 토큰 재발급으로 갱신하지 않습니다.

구현 확정 (T13, 6.24):
- 공통 클라이언트 `static/js/api-client.js`는 동일 출처의 `/api/**`만 처리합니다. 가입, 로그인, 재발급, 로그아웃은 자동 갱신 대상이 아닙니다.
- 재발급이 401을 주거나 재시도가 다시 401이면 `auth:reauth-required`를 냅니다. 네트워크 오류, 5xx, 403(CSRF)은 `auth:refresh-failed`를 냅니다. 두 경우 모두 쿠키를 바꾸거나 로그아웃하지 않습니다. 이벤트에는 사유 코드만 넣습니다.
- 같은 탭에서 갱신이 끝난 뒤 늦게 도착한 401은 재발급 없이 한 번 재시도합니다. 갱신 세대(generation)로 판단합니다.
- `XSRF-TOKEN` 쿠키가 없으면 `GET /api/auth/me`로 한 번 받습니다. 그래도 없으면 재발급 없이 실패합니다. 토큰 값을 임의로 만들지 않습니다.
- 본문을 한 번만 읽을 수 있는 요청은 재시도하지 않습니다.
- Web Locks를 지원하지 않는 환경에서는 같은 탭 안의 공유만 보장합니다. 여러 탭의 동기화는 보장하지 않습니다.

구현 확정 (T20, 6.25):
- 429는 인증 만료가 아닙니다. 클라이언트는 429 응답에 쿠키나 세션을 바꾸지 않고, 로그인 페이지로 보내거나 로그아웃하지 않습니다.
- 같은 `/api` 응답이 호출자에게 429로 돌아갈 때 안내를 한 번 보냅니다. 같은 응답 객체는 두 번 알리지 않습니다.
- 429 응답은 자동으로 다시 보내지 않습니다. 재시도는 사용자가 직접 합니다.
- `Retry-After`는 정수 초만 해석합니다. 누락이나 잘못된 값은 대기 시간 없는 안내를 씁니다.
- 안내 요소는 화면에 하나만 둡니다. 다시 오면 메시지만 갱신합니다.

동시 갱신 절차 (T14 A안, 화면 스크립트):
1. API 요청이 `401 TOKEN_INVALID`를 받으면 갱신 절차를 시작합니다.
2. 같은 탭에서 이미 진행 중인 갱신이 있으면 새로 시작하지 않고 그 결과를 기다립니다 (T13 single-flight).
3. Web Locks API로 고정된 잠금 이름(`busan-auth-refresh`)을 얻습니다. 같은 브라우저의 모든 탭이 같은 이름을 사용합니다.
4. 잠금 안에서 `GET /api/auth/me`로 현재 인증 상태를 확인합니다. HttpOnly 쿠키를 JavaScript로 읽지 않으며, 보호된 API 호출의 결과로만 판단합니다.
5. 확인 결과가 `200`이면 다른 탭이 이미 갱신한 것입니다. Refresh 요청을 보내지 않고 원래 요청을 재시도합니다.
6. 확인 결과가 `401`이면 `POST /api/auth/refresh`를 한 번 호출합니다. 이때만 Refresh Token 쿠키가 전송됩니다.
7. 갱신에 성공하면 잠금을 해제하고 원래 요청을 한 번 재시도합니다. 실패하면 재로그인을 안내합니다.
8. 재시도한 요청도 401이면 추가 갱신을 하지 않고 재로그인을 안내합니다.
- 잠금을 쥔 탭이 닫히면 브라우저가 잠금을 해제하므로 잠금이 영구히 남지 않습니다.
- Web Locks API는 보안 컨텍스트에서만 사용할 수 있습니다. 운영 환경은 HTTPS를 전제로 하고, 로컬 개발은 `localhost`(`http://localhost`)를 사용할 수 있습니다.
- 보안 컨텍스트가 아니거나 Web Locks API를 지원하지 않는 환경에서는 잠금 없이 같은 탭의 single-flight만 수행합니다 (fallback). 이때 여러 탭의 동시 갱신은 완전히 방지하지 못합니다. fallback은 T14 A안의 정책과 보안 수준을 바꾸지 않습니다.
- 외부 라이브러리를 추가하지 않습니다. Web Locks API는 브라우저 기본 기능입니다.

### 5.7.3 Google 신규 가입과 닉네임 설정 (T3 A안 확정)

확정 원칙:
- Google 프로필 이름을 닉네임으로 자동 사용하지 않습니다.
- 신규 회원은 별도의 닉네임 설정 화면(`GET /signup/google/nickname`, 제출은 `POST /signup/google/nickname`)에서 닉네임을 직접 입력합니다.
- 닉네임은 2~20자이며 중복되지 않아야 합니다. 허용 문자와 검증 규칙은 FUNCTION_SPEC F1-01 "회원가입·로그인 검증 규칙"을 따릅니다. 유효하지 않거나 중복된 닉네임은 다시 입력하도록 합니다.
- 닉네임 검증이 끝난 뒤에만 회원 레코드를 생성합니다.
- 닉네임 설정이 끝나기 전에는 정식 로그인 회원으로 취급하지 않습니다. 화면 세션과 JWT를 발급하지 않고, Google 인증 객체를 SecurityContext에 남기지 않습니다.
- OAuth2 임시 세션은 가입 진행 상태만 보관합니다 (Google `sub`, 이메일, 흐름 종류 = 신규 가입).

신규 가입 흐름:
1. Google 로그인 콜백에서 `state`를 검증합니다 (5.7.1 계정 연동 콜백이 아닌 일반 로그인 경로).
2. `google_sub`로 회원을 조회합니다. 있으면 일반 로그인으로 처리합니다.
3. 없고 같은 이메일의 기존 회원이 있으면 `/login?error=email_account_exists`로 거부합니다 (FR-01-09).
4. `email_verified`가 아니면 `/login?error=google_unverified`로 거부합니다.
5. 둘 다 없으면 임시 세션에 가입 진행 상태를 저장하고, 로그인 상태 없이 닉네임 설정 화면으로 이동합니다.
6. 닉네임을 제출하면(`POST`, CSRF 토큰 필요) 가입 진행 상태가 있는지 확인합니다. 없으면 `/login`으로 이동합니다.
7. 닉네임 규칙(길이, 허용 문자, 앞뒤 공백 제거)을 검증합니다 (FUNCTION_SPEC F1-01). 실패하면 같은 화면에 `error=validation_failed`를 표시합니다.
8. 닉네임 중복을 검사합니다. 중복이면 같은 화면에 `error=nickname_duplicated`를 표시합니다.
9. 검증이 끝나면 회원을 생성합니다 (`role = USER`, `password_hash = NULL`, `google_sub = sub`).
10. 회원 생성이 성공하면 화면 세션과 API JWT를 함께 발급하고 `/`로 이동합니다. 가입 진행 상태를 제거합니다.
11. 흐름이 중단되거나 실패하면 회원을 만들지 않고 가입 진행 상태를 제거합니다.

주의:
- 닉네임 중복 방지는 두 단계입니다. 8번의 사전 검사로 사용자에게 빠르게 안내합니다. 최종 방어는 `uk_member_nickname` UNIQUE 제약입니다. 사전 검사만으로는 동시 가입 요청을 막을 수 없습니다. 9번 저장 시 제약 위반이 나면 6.4 규칙에 따라 `nickname_duplicated`로 처리합니다 (ERD 2.1).

구현 상태 (6.21, PR #28 `main` 병합 완료, merge commit `37df8e0`):
- 신규 가입 대상은 `GoogleSignupPending`(Google sub, 검증된 이메일, 만료 15분)만 세션 속성에 둔다. 정식 인증 객체를 만들지 않는다.
- Spring `oauth2Login`이 콜백에서 OAuth2 인증을 SecurityContext에 넣으므로, 정식 로그인이 아닌 결과(신규 가입 진행, 오류)는 콜백 전의 인증 상태를 복원한다. 기존 로그인 세션을 바꾸지 않기 위해서다.
- 정식 로그인(sub 연결 회원)만 `ScreenSession.establish`로 화면 세션을 만들고 JWT 쿠키를 발급한다.
- 닉네임 제출은 회원 생성과 토큰 발급을 한 트랜잭션에서 한다. 토큰 발급이 실패하면 회원도 만들어지지 않는다.
- 요청 제한: 닉네임 제출은 `SignupRateLimitFilter`가 회원가입과 같은 IP 카운터(1시간 5회)로 집계한다. Google 로그인 시작과 콜백은 같은 IP의 15분 10회 카운터를 쓴다.
- 환경 설정: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`은 함께 설정한다(`application.yml`의 `app.oauth2.google`). 한쪽만 설정되면 어떤 프로필에서도 기동하지 않는다. 둘 다 없으면 local에서는 Google 로그인이 꺼지고, prod에서는 기동하지 않는다.
- 구현 완료 범위: Google 로그인 시작·콜백, 연결 회원 로그인, 같은 이메일 자동 연결 금지, 미검증 이메일 거부, 신규 가입 닉네임 설정과 가입 완료.
- PR B 구현 범위(6.22, PR #30으로 `main` 병합 완료, merge commit `05778895c03da90ad56570e5ddae3485acc97ba9`): Google 계정 연동 시작·콜백(5.7.1, 2.6·2.6.1), Google 계정 연동 해제(5.7.5, 2.7), Google 전용 회원의 로컬 비밀번호 설정(5.7.5, 2.7.1), 연동 요청 제한 연결. 화면 보호 페이지는 `/mypage`만 구현했다.

### 5.7.4 Refresh Token 재사용 탐지 (T7 A안 확정)

확정 원칙:
- Refresh Token은 로테이션 방식으로 관리합니다. 정상 재발급 시 기존 Refresh Token을 폐기하고 새 Refresh Token을 발급합니다.
- 폐기된 Refresh Token이 다시 사용되면 토큰 재사용으로 판단합니다. 로그아웃으로 폐기된 토큰도 같은 규칙을 따릅니다.
- 재사용이 탐지되면 해당 회원의 모든 활성 Refresh Token을 폐기합니다. 해당 회원은 모든 기기에서 Refresh Token으로 재발급할 수 없으며 다시 로그인해야 합니다.
- DB에는 Refresh Token 원문이 아닌 SHA-256 해시만 저장합니다.
- 토큰 계열 UUID(`family_id`)를 행마다 저장합니다. 회전할 때 같은 값을 유지하고, 새 기기 로그인에서만 새 값을 만듭니다 (FR-01-36). `family_id`는 가명 식별자로 취급합니다 (5.3).
- 폐기된 토큰의 해시 행은 재사용 탐지를 위해 보존합니다. 삭제하지 않습니다.
- 재사용 탐지와 활성 토큰 일괄 폐기는 같은 트랜잭션에서 처리합니다.
- 동시 재발급과 경쟁 상태를 고려합니다. 중복 발급이나 일부 토큰이 살아남는 상태가 없도록 설계합니다.
- 알 수 없는 토큰과 만료된 토큰은 기존 오류 응답(`401 TOKEN_INVALID`)과 같게 응답합니다. 재사용 탐지와 구분하는 기준은 서버 내부에서만 사용하며 응답에 노출하지 않습니다.
- API 응답에 토큰 원문이나 내부 판단 정보를 노출하지 않습니다. 로그에는 회원 ID와 이벤트 종류만 남기고 토큰 원문은 남기지 않습니다.

재발급 처리 기준 (`POST /api/auth/refresh`, API_SPEC 2.3, T14 A안):
1. 쿠키 원문의 SHA-256 해시로 `refresh_token` 행을 잠금 없이 조회하여 회원 ID를 확인합니다. 행이 없으면 `401 TOKEN_INVALID`를 반환합니다. 어떤 행도 변경하지 않습니다.
2. 트랜잭션을 시작하고 회원 행을 잠금 조회합니다 (`SELECT ... FOR UPDATE`).
3. 같은 트랜잭션에서 해당 `refresh_token` 행을 다시 조회합니다. 잠금 전의 조회 결과로 판단하지 않습니다.
4. 재조회한 행으로 판정합니다.
   - 폐기되지 않았고 만료되지 않았으면 정상 갱신입니다. 기존 행의 `revoked_at`을 기록하고 새 행을 INSERT한 뒤 커밋합니다. 커밋 후 새 `access_token`, `refresh_token` 쿠키를 설정합니다.
   - 폐기되지 않았지만 만료되었으면 `401 TOKEN_INVALID`를 반환합니다. 다른 행은 변경하지 않습니다.
   - `revoked_at`이 있으면 재사용 탐지입니다. 같은 트랜잭션에서 해당 회원의 `revoked_at`이 없는 모든 행에 `revoked_at`을 기록하고 커밋합니다. 커밋 후 `401 TOKEN_INVALID`를 반환하고 두 JWT 쿠키를 제거합니다.

처리 원칙 (T14 A안):
- 잠금 순서: 모든 Refresh Token 상태 변경 경로(재발급, 재사용 탐지, API 로그아웃)는 회원 행 → `refresh_token` 행 순서로 잠급니다. 같은 순서를 지켜 교착 상태를 피합니다.
- 정상 갱신과 재사용 탐지는 같은 회원 잠금 구간 안에서 판정합니다. 잠금 없이 전체 폐기를 수행하지 않습니다.
- 재사용 탐지 결과를 예외로 던지지 않습니다. 예외가 전파되면 트랜잭션이 롤백되어 전체 폐기가 사라집니다. 서비스는 결과 값을 반환하고, 트랜잭션이 커밋된 뒤 컨트롤러가 `401`을 응답합니다.
- HTTP 응답 전송과 DB 커밋은 별개입니다. 커밋이 끝난 뒤에 응답을 보내므로, 응답이 유실되어도 폐기 결과는 DB에 남습니다.
- 잠금 구간은 짧게 유지합니다. 외부 호출(Google, S3, OpenRouter)을 잠금 안에서 하지 않습니다.
- 요청 제한은 재사용 탐지 이후, 토큰 회전 이전에 판정합니다. 재사용 탐지 결과는 429 때문에 생략되지 않습니다. 제한된 요청은 토큰을 바꾸지 않고 이벤트도 만들지 않습니다 (API_SPEC 2.3, R4).

한계와 범위:
- Access Token은 이미 발급된 JWT이므로 Refresh Token 폐기만으로 즉시 무효화되지 않을 수 있습니다. 이 결정은 모든 활성 Refresh Token 폐기 정책입니다. Access Token의 즉시 무효화 체계를 새로 도입하지 않습니다.
- 토큰 블랙리스트나 추가 라이브러리를 도입하지 않습니다.

#### 동시 갱신 및 재사용 처리 (T14 A안 확정)

결정명: Refresh Token 동시 갱신 및 재사용 처리
결정: 기존 T7의 엄격한 재사용 탐지 정책을 유지합니다. 브라우저의 여러 탭이 동시에 갱신하는 문제는 Web Locks API로 조정합니다. 정상 중복 요청을 구별하기 위한 유예 창이나 토큰 저장 구조 변경은 도입하지 않습니다.

검토한 대안과 미채택 이유:
- 유예 창과 후속 토큰 참조: 폐기 직후의 재사용을 전체 폐기 없이 401로만 처리합니다. T7을 완화하고 `replaced_by` 같은 저장 구조 변경이 필요하므로 미채택합니다.
- 기기 정보 기반 토큰 패밀리: IP나 User-Agent로 정상 중복을 구별합니다. 정확도가 낮고 개인정보를 추가로 저장하므로 미채택합니다.

서버 정책:
1. 정상 갱신 시 기존 Refresh Token을 폐기하고 새 Refresh Token을 발급합니다.
2. 이미 폐기된 Refresh Token이 제출되면 T7에 따라 해당 회원의 모든 활성 Refresh Token을 폐기합니다.
3. 폐기된 토큰의 SHA-256 해시 행은 재사용 탐지를 위해 보존합니다.
4. Refresh Token 원문은 DB에 저장하지 않습니다.
5. Refresh Token 원문은 HttpOnly 쿠키로만 관리합니다.
6. 정상 갱신과 재사용 탐지는 같은 회원 단위 잠금 정책을 사용합니다.
7. 회원 단위 잠금은 트랜잭션 안에서 수행합니다.
8. 잠금을 얻은 뒤 토큰 상태를 다시 조회하여 판단합니다.
9. 정상 갱신과 재사용 탐지의 잠금 순서를 같게 유지합니다.
10. 교착 상태가 생기지 않도록 잠금 순서를 고정합니다 (위 "처리 원칙" 참조).

브라우저 정책 (Web Locks API): 아래 DEVELOPMENT_RULES 5.7.2의 "동시 갱신 절차"를 따릅니다.

시나리오별 처리:

| 시나리오 | 처리 | 한계 |
|----------|------|------|
| S1. 같은 탭의 여러 API 요청이 동시에 401 | T13 single-flight로 Refresh 요청을 하나만 실행하고 나머지는 결과를 기다림 | 없음 |
| S2. 같은 브라우저의 여러 탭에서 동시에 401 | Web Locks API로 갱신을 직렬화하고, 잠금 안에서 `GET /api/auth/me`로 인증 상태를 확인. 이미 갱신되었으면 Refresh 요청을 보내지 않음 | Web Locks 미지원 브라우저에서는 보장되지 않음 (아래 제한) |
| S3. Refresh 응답이 네트워크 오류로 유실 | 서버는 이미 회전했을 수 있으나 브라우저는 새 쿠키를 받지 못함. 이전 토큰을 다시 제출하면 T7에 따라 재사용으로 판단하여 전체 폐기 | 이번 A안에서 해결하지 않음. 사용자는 재로그인해야 할 수 있음 |
| S4. 다른 기기에서 이전 Refresh Token을 재사용 | Web Locks는 같은 브라우저 출처 안의 탭만 조정하므로 적용되지 않음. 폐기된 토큰이면 T7에 따라 회원의 모든 활성 토큰을 폐기 | 다른 기기의 정상 사용자도 재로그인해야 함 |
| S5. 실제 탈취 토큰 재사용 | 재사용 탐지로 전체 폐기하고 재로그인을 요구. 폐기된 토큰의 재사용은 보안 이벤트로 기록 (회원 ID와 이벤트 종류만, 토큰 원문 제외) | 서버는 정상 사용자와 공격자를 확실히 구별할 수 없음 |

A안의 한계 (반드시 유지할 설명):
- 네트워크 응답 유실(S3)은 해결되지 않습니다. 이 경우 정상 사용자도 재로그인해야 할 수 있습니다. 이는 T7의 엄격함을 유지하는 대가로 수용한 한계입니다.
- Web Locks API를 쓸 수 없는 환경(미지원 브라우저, 보안 컨텍스트가 아닌 출처)에서는 여러 탭의 동시 갱신을 완전히 막을 수 없습니다. 같은 탭의 single-flight만 보장합니다. 이 환경에서는 S2의 오탐이 발생할 수 있습니다.
- Web Locks는 같은 출처의 탭 사이에서만 동작합니다. 다른 기기에는 적용되지 않습니다.
- Access Token은 이미 발급된 JWT이므로 Refresh Token 폐기만으로 즉시 무효화되지 않을 수 있습니다. 이 결정은 Access Token의 즉시 무효화 체계를 도입하지 않습니다.

### 5.7.5 Google 계정 연동 해제와 로컬 비밀번호 설정 (T8 A안 확정)

구현 상태: PR #30(6.22)으로 `main` 병합 완료(merge commit `05778895c03da90ad56570e5ddae3485acc97ba9`). 경로는 `POST /mypage/google/link`(5.7.1), `POST /mypage/google/unlink`, `GET·POST /mypage/password`이다.

Google 계정 연동 (확정):
- 로그인된 본인의 Spring Security 세션으로 시작합니다 (5.7.1, API_SPEC 2.6).
- 연동 시작 요청은 CSRF 보호가 적용된 화면 POST입니다.
- OAuth2 `state` 검증을 수행합니다.
- 연동 대상 회원 ID는 연동 시작 시점의 로그인 세션에서 확보합니다.
- Google 이메일을 기준으로 기존 회원과 자동 연결하지 않습니다.
- 연동 과정에서 기존 로그인 세션과 API JWT를 교체하거나 재발급하지 않습니다.

Google 계정 해제 (확정):
- 로그인된 본인 세션에서만 가능합니다. 해제는 화면 POST(`/mypage/google/unlink`)로 처리하며 API 엔드포인트로 제공하지 않습니다.
- 해제 요청 시 현재 비밀번호를 다시 입력해야 합니다.
- 서버는 기존 비밀번호 해시와 입력값을 BCrypt로 검증한 뒤 해제합니다. 검증에 실패하면 해제하지 않습니다.
- 연동된 Google 계정이 없으면 해제하지 않고 `/mypage?error=google_not_linked`로 이동합니다.
- 회원에게 남은 로그인 수단이 없어지는 해제 요청은 거부합니다.
- Google 전용 회원은 `password_hash`가 `NULL`이므로 비밀번호를 다시 입력할 수 없습니다. 이 회원은 먼저 로컬 비밀번호를 설정해야 하며, 비밀번호 설정이 끝난 뒤에만 해제할 수 있습니다 (FR-01-22).
- 별도의 인증 방식이나 소셜 계정 자동 연결을 추가하지 않습니다.

로컬 비밀번호 설정 (Google 전용 회원, FR-01-22):
- 로그인된 본인 세션에서 화면 `GET·POST /mypage/password`로 처리합니다. CSRF 토큰이 필요합니다.
- `password_hash`가 `NULL`인 회원만 설정할 수 있습니다. 이미 비밀번호가 있는 회원은 이 경로로 바꿀 수 없습니다 (비밀번호 변경 기능은 이번 명세 범위가 아닙니다).
- 비밀번호는 FUNCTION_SPEC F1-01의 가입 규칙(8~20자, 영문·숫자·특수문자 각 1자 이상, 공백 불가)을 따르며, `password`와 `passwordConfirm`이 일치해야 합니다. 저장 시 BCrypt로 해시합니다 (FR-01-02).
- 설정이 끝나면 `/mypage`로 이동합니다. 설정 중 오류는 같은 화면에 `error=validation_failed`를 표시합니다.

### 5.8 요청 제한 HMAC 키 관리

구현 설정 (2026-11-12):
- 환경 변수 `AUTH_RATE_LIMIT_HMAC_SECRET`을 `auth.rate-limit.hmac-secret`에 바인딩합니다. 키는 `AuthRateLimitHasher.keyFromBase64`가 검증합니다. 이중 키 조회는 구현하지 않습니다.
- 키 값과 디코딩 결과는 로그, 예외 메시지, `toString()`, 테스트 실패 메시지에 나타나지 않도록 합니다. `AuthRateLimitProperties.toString()`은 값을 가립니다.
- 이벤트 정리: 모든 이벤트는 생성 후 24시간 뒤 삭제합니다. 요청 시 정리(한 번에 `REQUEST_PURGE_BATCH`=100건)와 주기 정리를 병행합니다. 주기 정리는 `auth.rate-limit.cleanup-interval-ms`(기본 600000), 첫 실행 지연은 `cleanup-initial-delay-ms`(기본 60000), 배치 크기는 `cleanup-batch-size`(기본 1000)입니다. 정리 실패는 로그에 예외 종류만 남기고 요청 결과를 바꾸지 않습니다.
- `auth_rate_limit_counter` 행은 자동으로 지우지 않습니다.
- 신뢰 프록시: `app.security.trusted-proxies`. Remote Address가 목록에 없으면 `X-Forwarded-For`를 무시합니다. 체인은 오른쪽부터 보고 신뢰 프록시가 아닌 첫 항목을 클라이언트로 씁니다.

HMAC 키 교체 절차 (구현 기준):
1. 교체 시점을 정하고, 애플리케이션을 중지합니다. 중지 중에는 요청 제한 판정이 없으므로 교체 전후 카운터 초기화 위험을 운영자가 감수합니다.
2. HMAC 기반 이벤트와 카운터를 삭제합니다. Refresh 계열(`refresh_request_event`)은 HMAC을 쓰지 않으므로 대상이 아닙니다.
   `DELETE FROM login_failure_event; DELETE FROM signup_request_event; DELETE FROM google_oauth_request_event; DELETE FROM auth_rate_limit_counter;`
3. 새 키를 `AUTH_RATE_LIMIT_HMAC_SECRET`에만 설정하고 애플리케이션을 시작합니다. 새 키는 기존 키와 달라야 하며, `openssl rand -base64 32`로 만듭니다.
4. 로그인 실패 요청 한 건으로 이벤트가 새 키 기반으로 기록되는지 확인합니다. 이전 키 값은 운영 환경과 운영 기록에서 제거합니다.
5. 교체 날짜, 정리 방식, 담당자를 운영 기록에 남깁니다.


적용 대상: 요청 제한 식별자를 만들 때 쓰는 HMAC-SHA256 비밀키 (R2). 로그인 이메일+IP 키, 로그인 IP 보조 키, 회원가입 IP 키, Google OAuth2 IP 키가 모두 여기에 해당합니다 (T16 확정). Refresh 계열 키(`family_id`)는 HMAC을 쓰지 않으므로 이 절의 영향을 받지 않습니다.

정책:
1. 식별자 생성에는 서버 환경 변수로 관리하는 HMAC-SHA256 비밀키를 사용합니다. 변수 이름은 구현 단계에서 확정합니다 (5.2).
2. 비밀키는 Git 저장소, 설정 파일, 로그, 응답 본문, 테스트 코드에 두지 않습니다.
3. 초기 운영은 단일 키를 사용합니다.
4. 구·신 키를 동시에 지원하는 이중 키 버전 관리는 초기 구현에 도입하지 않습니다.
5. 키를 바꾸면 기존 키로 만든 식별자는 새 키로 조회되지 않습니다. 따라서 교체 전에 기존 요청 제한 이벤트와 카운터를 정리해야 합니다.

교체 절차:
1. 교체 시점을 정합니다. 요청 제한 판정이 가장 적게 일어나는 시간대를 고르는 것을 권장합니다.
2. 새 키를 준비합니다. 기존 키와 달라야 하며, 256비트 이상의 난수를 사용하는 것을 운영 권장으로 둡니다. 새 키는 EC2 환경 변수에만 보관합니다.
3. 기존 키로 만든 요청 제한 데이터를 정리합니다.
   - 권장 방식은 삭제입니다. 로그인, 회원가입, Google OAuth2의 HMAC 기반 이벤트 행과 `auth_rate_limit_counter`의 HMAC 기반 행을 삭제합니다. Refresh 계열 이벤트는 삭제 대상이 아닙니다.
   - 만료를 기다리는 방식도 가능하지만, 이벤트 보관 기간(24시간) 동안 기존 키로 새 이벤트를 쓰지 않도록 요청 제한 기능을 멈춰야 합니다. 운영상 현실적이지 않아 권장하지 않습니다.
   - 삭제 방식을 쓰면 교체 직후 카운터가 초기화됩니다. 이 기간 동안 제한이 느슨해지는 위험을 운영자가 감수해야 합니다.
4. 정리가 끝나면 환경 변수를 새 키로 바꾸고 애플리케이션을 재시작합니다. 재시작 뒤의 요청부터 새 키로 식별자를 만듭니다.
5. 교체 후 확인: 로그인 실패 요청 한 건을 테스트해 이벤트가 새 키 기반으로 기록되는지 확인합니다. 이전 키 값은 운영 환경과 운영 기록에서 모두 제거합니다.
6. 교체 날짜, 정리 방식, 담당자를 운영 기록에 남깁니다.

운영상 주의:
- 키가 유출되었을 것으로 의심되면 같은 절차를 따르되, 3단계에서 삭제 방식을 씁니다. 기다리는 방식은 쓸 수 없습니다.
- DB 백업에는 교체 이전 식별자가 남을 수 있습니다. 백업 보관 기간은 T4에서 정하므로 그때 함께 확인합니다.
- 키 값은 로그, 오류 메시지, 디버그 출력에 나타나지 않도록 합니다.


HTTP 적용 규칙 (요청 제한, 2026-11-17):
- 요청 제한 집계 필터는 서블릿 필터 빈으로 등록하지 않습니다. `SecurityConfig`가 Spring Security 체인에 직접 추가합니다. 서블릿 컨테이너 자동 등록과 겹치면 집계가 두 번 일어납니다.
- 회원가입 집계 필터는 `CsrfFilter` 뒤에 둡니다. CSRF 실패 요청은 집계하지 않습니다. 본문을 읽지 않으므로 Controller가 본문을 그대로 읽습니다.
- 집계 대상은 정확히 일치하는 경로와 메서드만 씁니다. 접두사 일치나 와일드카드로 넓히지 않습니다.
- 제한 초과는 `RateLimitExceededException`으로 바꿔 공통 오류 응답(429, `Retry-After`)으로 보냅니다. 쿠키를 바꾸거나 세션을 무효화하지 않습니다.
- 요청 제한 판정 실패는 요청을 허용하지 않습니다(fail-closed). 일반 내부 오류(500 `INTERNAL_ERROR`)로 응답합니다.

### 5.9 JWT 발급·검증 규칙 (Stage 2-3 구현 기준)

- 구현 위치는 `member.jwt` 패키지입니다. 발급은 `AccessTokenIssuer`, 검증은 `AccessTokenVerifier`, 설정은 `JwtProperties`, 검증 규칙은 `JwtValidators`, 키 로딩은 `JwtSigningKeys`가 담당합니다.
- 서명은 HS256으로 고정합니다. 기존 `spring-security-oauth2-jose`를 사용하며 라이브러리를 추가하지 않습니다.
- `JWT_SECRET`은 다음 규칙을 따릅니다.
  - 생성: 암호학적 난수 32바이트 이상을 Base64로 인코딩합니다. 예: `openssl rand -base64 32`.
  - 보관: EC2 환경 변수로만 주입합니다. Git, 문서, 테스트 결과, 커밋, 로그에 키 원문이나 디코딩 결과를 남기지 않습니다.
  - 코드와 설정: 기본 키나 개발용 고정 키를 코드에 두지 않습니다. `application.yml`의 기본값은 빈 값입니다.
  - 시작 검증: 누락, Base64 형식 오류, 디코딩 후 32바이트 미만이면 기동을 실패시킵니다. 오류 메시지에는 키 값이나 원인 예외를 포함하지 않습니다.
  - 교체: 키를 바꾸면 기존 Access Token은 무효화될 수 있습니다. 교체 시점과 절차는 운영 단계에서 정하며, Refresh Token 처리와 함께 확인합니다.
- Access Token 만료 시간은 환경 변수 `JWT_ACCESS_TOKEN_EXPIRY_SECONDS`로 설정합니다. 기본값은 1800초입니다. 코드에 같은 값의 상수를 중복 정의하지 않습니다.
- 시계 오차 60초는 Spring 기본값에 의존하지 않고 `JwtSpec.CLOCK_SKEW`로 명시합니다.
- 토큰에는 1.5절의 클레임만 넣습니다. `family_id`와 개인 식별 정보는 넣지 않습니다.
- 검증 실패의 내부 사유는 내부 예외 메시지에만 남기고 외부 응답에 노출하지 않습니다. 토큰 원문은 예외 메시지, 로그, 응답 본문 어디에도 남기지 않습니다.
- 구현 상태: 발급과 검증, 설정과 시작 검증은 구현되었습니다. Refresh Token, 인증 필터 연결, 로그인과 회원가입 컨트롤러는 아직 구현하지 않았습니다.

### 5.10 시간 처리 규칙

- 서비스 시간대는 `Asia/Seoul`입니다. 애플리케이션(JVM), JDBC 변환, DB 세션이 모두 이 시간대를 씁니다. API의 날짜·시간 표기(API_SPEC 1.1)와 같은 기준입니다.
- JVM 기본 시간대는 기동 시 `TimeConfig.applyDefaultTimeZone()`이 고정합니다. 서버의 OS 시간대 설정에 의존하지 않습니다.
- DB 세션 시간대는 `application.yml`의 `spring.datasource.hikari.connection-init-sql`(`SET time_zone = '+09:00'`)로 고정합니다. `NOW()`, `CURRENT_TIMESTAMP` 기본값이 앱이 저장하는 시각과 같은 기준이 됩니다.
- `DB_URL`에 서비스 시간대와 다른 시간대 옵션(`serverTimezone`, `connectionTimeZone` 등)을 넣지 않습니다. 셋이 다르면 앱이 저장한 시각과 DB가 만든 시각이 어긋납니다.
- 현재 시각이 필요한 빈은 `TimeConfig`의 공용 `Clock` 빈을 주입받습니다. `Clock` 빈을 추가로 등록하지 않으며, 서비스 코드에서 `Clock.systemUTC()`나 `Clock.systemDefaultZone()`을 직접 만들지 않습니다.
- 테스트 JVM은 `build.gradle`의 `user.timezone` 설정으로 같은 시간대에서 실행됩니다. 시간대 일치는 `ApplicationContextDbTest`가 확인합니다.

## 6. 코드 구조 규칙 (구현 단계)

### 6.1 패키지 구조 (기능 단위)

```
com.busanlocalfood
├─ global        # 공통 설정, 예외, 응답 형식, 보안 설정
├─ member        # F1-01 회원·인증 (Google 연동 포함)
├─ restaurant    # F1-02 맛집, F1-08 메인 조회 보조
├─ image         # F1-03 S3 업로드·삭제, storage_delete_task 처리
├─ review        # F1-04 리뷰
├─ summary       # F1-05 AI 리뷰 요약·캐싱
├─ favorite      # F1-06 찜
├─ recommend     # F1-07 AI 추천
├─ admin         # F1-09 관리자 기능 (관리자 API, 대시보드, 재시도)
└─ web           # Thymeleaf 컨트롤러
```

- 각 기능 패키지는 `controller`, `service`, `repository`, `domain`, `dto`로 나눕니다.
- 패키지 간 직접 참조는 서비스 계층을 통해서만 합니다. 다른 기능의 Repository를 직접 주입하지 않습니다.
- `admin` 패키지는 다른 기능의 서비스를 호출해 재사용합니다 (예: 맛집 삭제는 `restaurant` 서비스, 이미지 삭제는 `image` 서비스). 관리자 전용 로직을 복제하지 않습니다.

### 6.2 네이밍
- 엔티티: 단수형 (`Restaurant`, `Review`). 테이블명은 ERD의 snake_case를 따릅니다.
- DTO: 요청 `XxxRequest`, 응답 `XxxResponse`.
- 오류 코드: `API_SPEC.md`의 `code` 값과 동일한 enum 상수를 사용합니다.
- 환경 변수 접근은 `@ConfigurationProperties`로 감쌉니다.

### 6.3 API·화면 구현 방식
- JSON API는 `@RestController`, 화면은 `@Controller` + Thymeleaf로 구현합니다.
- 목록의 정렬·페이지네이션은 Spring Data `Pageable`을 사용합니다.
- 화면에서 AJAX가 필요한 동작(찜, 리뷰 작성·삭제, 추천)은 REST API를 호출합니다. 나머지 폼은 일반 POST로 처리합니다. (결정 사항: 구현 첫 기능 PR에서 확정하고 이 절에 반영)

### 6.4 예외 처리
- 도메인 예외는 `BusinessException(ErrorCode)`를 상속한 클래스로 던집니다.
- DB 제약 위반 중 `uk_member_nickname`은 서비스 계층에서 `NICKNAME_DUPLICATED`(API 409) 또는 화면 `nickname_duplicated`로 변환합니다. 다른 제약 위반은 원인을 응답에 노출하지 않고 `INTERNAL_ERROR`로 처리합니다.
- `@RestControllerAdvice`에서 `API_SPEC.md` 형식으로 변환합니다.
- Google 리다이렉트 흐름의 실패는 JSON이 아니라 `error` 쿼리 파라미터로 전달합니다 (API_SPEC 2.5, 2.6).

## 7. 테스트 규칙

### 테스트 환경 (T9 확정, 2026-11-09)

- 테스트 의존성은 `spring-boot-starter-test`입니다. JUnit 5, Mockito, AssertJ, Spring MockMvc를 사용합니다.
- Testcontainers, H2, Docker 기반 테스트 환경은 도입하지 않습니다. Docker Desktop 설치를 요구하지 않습니다.
- DB 연동 테스트는 개발 PC에 직접 설치한 MySQL 8의 테스트 전용 데이터베이스를 사용합니다. 개발 데이터베이스와 분리합니다. 테스트가 개발 데이터베이스의 데이터를 삭제하거나 변경하지 않도록 합니다.
- DB 연동 테스트 준비 절차:
  1. 테스트 전용 데이터베이스에 `db/manual/001_create_member_refresh_token.sql`, `db/manual/002_add_refresh_token_family_id.sql`, `db/manual/003_create_auth_rate_limit_tables.sql`을 이 순서로 한 번씩 적용합니다. 003이 없으면 `AuthRateLimitDbTest`가 실패합니다.
  2. 환경 변수 `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`를 설정하고 `./gradlew clean test`를 실행합니다. 값은 저장소에 커밋하지 않습니다.
  3. 변수가 없으면 DB 테스트는 건너뜁니다. 건너뛴 테스트는 통과로 보고하지 않습니다.
- 외부 서비스(Google, AWS S3, OpenRouter)는 모킹하거나 가짜 구현체를 사용합니다 (아래 마지막 항목 참조).
- 위 목록 밖의 테스트 라이브러리가 필요하면 이유와 대안을 제안하고 사용자 승인을 받은 뒤 추가합니다.
- CI 테스트는 기존 GitHub Actions 계획을 따릅니다. 이번 결정으로 새 테스트 인프라를 추가하지 않습니다.

### 테스트 작성 기준

- 기능마다 최소한 다음 테스트를 작성합니다.
  - 서비스 단위 테스트 (Mockito): 정상 흐름, 권한 없음, 중복, 없는 대상
  - 리포지토리·통합 테스트: JPA 제약 조건(UNIQUE, FK, CHECK), 집계 갱신
  - 웹 계층 테스트 (MockMvc): 응답 형식과 상태 코드가 `API_SPEC.md`와 일치하는지 확인
- F1-01은 다음을 테스트합니다.
  - Refresh Token 재발급 시 기존 토큰이 폐기되고 새 토큰이 발급되는지 (로테이션)
  - 로그아웃 후 기존 Refresh Token이 거부되는지
  - DB에 원문이 없고 해시만 저장되는지
  - 동일 이메일 기존 계정이 있을 때 Google 로그인이 자동 연결하지 않는지
  - 이미 다른 회원에게 연결된 Google 계정을 연동할 수 없는지
  - 비밀번호가 없는 회원이 로컬 비밀번호를 설정하기 전에는 Google 연동 해제가 거부되는지
  - `state`가 없거나 다르면 Google 계정이 연결되지 않는지, 같은 `state`를 두 번 쓸 수 없는지
  - 임시 세션이 없거나 만료되었으면 연결되지 않는지
  - 연동 시작 시 로그인하지 않았으면 연동이 거부되는지
  - 콜백이 Access Token 없이 세션의 연동 대상 회원 ID 기준으로만 연결하는지
  - Google 이메일이 기존 회원과 같아도 자동 연결되지 않는지
  - 계정 연동 콜백 후 기존 세션과 API JWT가 교체되지 않는지
  - 연동 실패 시 기존 세션과 API JWT가 변경되지 않는지
  - 일반 Google 로그인 처리가 계정 연동 콜백에서 실행되지 않는지
  - 신규 Google 가입에서 닉네임 확정 전에는 회원이 만들어지지 않고 세션과 JWT가 발급되지 않는지
  - 닉네임 중복·길이 오류 시 회원이 만들어지지 않는지
  - 폐기된 Refresh Token 재사용 시 회원의 모든 활성 Refresh Token이 폐기되는지
  - 동시 재발급 요청에서 활성 Refresh Token이 둘 이상 남지 않는지
  - 화면 로그아웃에서 API 로그아웃이 실패하면 `/logout`이 호출되지 않는지
  - 동시 401 응답에서 재발급이 한 번만 실행되고 원래 요청은 한 번만 재시도되는지
  - 화면 세션 쿠키 기반 연동 해제가 비밀번호 불일치 시 해제하지 않는지
  - 로그인 6번째 요청이 429이고 비밀번호를 검증하지 않는지, 성공 시 이메일+IP 실패 기록이 초기화되는지
  - 429 응답이 JWT 쿠키를 바꾸지 않는지
  - Refresh 재사용 탐지가 요청 제한과 겹쳐도 회원의 모든 활성 토큰 폐기가 커밋되는지
  - 제한된 요청이 새 이벤트를 만들지 않는지
  - 로그인 제한 이벤트에 원문 이메일과 원문 IP가 저장되지 않는지
  - 회원가입 요청이 JSON 파싱과 DTO 검증 이전에 집계되고, 잘못된 입력과 Content-Type 오류도 횟수에 포함되는지
  - 계열을 확인할 수 없는 Refresh 요청에 IP 기반 제한이 추가되지 않는지
  - Google OAuth2 이벤트가 생성 시점으로부터 24시간 후 삭제되는지
  - 429 응답에서 JWT 쿠키가 유지되고, Refresh 429에서 자동 로그아웃이나 재로그인이 일어나지 않는지
  - 신뢰 프록시가 아닌 요청의 `X-Forwarded-For`가 무시되는지
  - `/api/**` 요청이 임시 세션만으로는 인증되지 않는지
  - 화면 요청이 JWT만으로 인증되지 않고, API 요청이 세션만으로 인증되지 않는지
  - API 로그인과 API 회원가입이 세션을 만들지 않고, 화면 로그인·화면 회원가입·Google 로그인이 세션과 JWT를 모두 발급하는지
- F1-02, F1-03은 다음을 테스트합니다.
  - 맛집 삭제 시 리뷰, 찜, 이미지, AI 요약이 함께 삭제되는지
  - S3 삭제 실패 시 `storage_delete_task`에 키가 남고, 재시도 후 행이 삭제되는지
  - 작성자가 아닌 회원의 수정·삭제가 403인지
- F1-05는 "리뷰 3개 미만 → 호출 없음", "캐시 히트 → 호출 없음", "리뷰 변경 → 캐시 삭제"를 테스트합니다.
- F1-07은 "후보 0개 → 호출 없음", "DB에 없는 ID 제외", "형식이 깨진 응답 → `AI_RESPONSE_INVALID`"를 테스트합니다.
- F1-09는 다음을 테스트합니다.
  - `USER`가 `/api/admin/**`에 접근하면 403인지
  - 비로그인 요청이 401인지
  - 일반 회원 API로 `role`을 바꾸는 경로가 없는지
  - 관리자 리뷰 삭제 후 평균 평점과 AI 요약 캐시가 갱신되는지
- 외부 서비스(S3, OpenRouter, Google)는 테스트에서 모킹하거나 가짜 구현체를 사용합니다. 실제 키를 테스트에 넣지 않습니다.
- CI(GitHub Actions)에서 `./gradlew test`가 통과해야 합니다.

## 8. 문서 규칙

- 기능을 구현하거나 바꾸면 `FUNCTION_SPEC.md`, `API_SPEC.md`, `DATABASE_ERD.md` 중 해당되는 문서를 같이 고칩니다.
- 기능 ID, 테이블명, 엔드포인트 경로, 오류 코드는 문서 전체에서 같은 표기를 씁니다.
- 미확정 사항이 해결되면 `REQUIREMENTS.md`의 4.2절(TODO)에서 빼고, 4.1절(확정된 결정)에 결정 내용을 적습니다.

## 9. 진행 기록 규칙

- 기능 작업이 끝날 때마다 `PROGRESS.md`의 해당 기능 상태와 작업 로그를 갱신합니다.
- 상태 값: `미착수`, `진행 중`, `테스트 중`, `완료`, `보류`.
- 완료 기준: 구현, 단위 테스트, 관련 문서 일치, CI 통과.
