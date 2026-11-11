# 기능 명세 (FUNCTION_SPEC)

각 기능의 화면, 처리 흐름, 권한, 예외를 정의합니다. 요구사항 ID는 [REQUIREMENTS.md](REQUIREMENTS.md), 테이블은 [DATABASE_ERD.md](DATABASE_ERD.md), 엔드포인트는 [API_SPEC.md](API_SPEC.md)와 일치해야 합니다.

공통 용어
- **비회원**: 로그인하지 않은 사용자.
- **회원**: 로그인한 사용자. `role`이 `USER` 또는 `ADMIN`입니다.
- **일반 회원**: `role = USER`인 회원.
- **관리자**: `role = ADMIN`인 회원. 일반 회원 기능을 모두 쓸 수 있고, 추가로 F1-09 관리자 기능을 씁니다.
- **작성자**: 맛집 또는 리뷰를 만든 회원.
- **권한 없음**: 인증되지 않은 요청은 401, 권한이 없는 요청은 403으로 응답합니다.

---

## F1-01 회원 (인증)

인증 구조 (2026-11-10 확정): 화면은 Spring Security 세션으로, API는 JWT로 인증합니다. 화면 로그인, 화면 회원가입, Google 로그인이 성공하면 세션과 JWT를 함께 발급합니다. 세부 규칙은 DEVELOPMENT_RULES 5.7을 따릅니다.

확정 결정: T3, T7, T8, T11, T12, T13, T14 A안 (2026-11-10). 요청 제한 R1~R4 (2026-11-11, 아래 "요청 제한" 절). 구현 상태는 PROGRESS.md를 따릅니다.

### 화면
- `/signup` 회원가입 폼 (제출: `POST /signup`): 이메일, 비밀번호, 비밀번호 확인, 닉네임
- `/login` 로그인 폼 (제출: `POST /login`): 이메일, 비밀번호, "Google로 로그인" 버튼, 오류 메시지 (`error` 쿼리 값에 따라 표시)
- `/mypage` 계정 설정 (세션 로그인 필요): 내 정보, Google 계정 연동 버튼 또는 연동 해제 버튼, 로컬 비밀번호 설정 (Google 전용 회원)
- 닉네임 설정 화면 (Google 신규 가입 진행 중에만 접근): 닉네임 입력 폼 (제출 POST, CSRF 토큰 필요)
- 헤더: 로그인 상태(세션 기준)에 따라 "로그인/회원가입" 또는 "마이페이지/내 찜 목록/로그아웃" 표시. 관리자에게는 "관리자" 메뉴 추가

### 회원가입 흐름 (화면)
1. 입력값을 검증합니다 (아래 "회원가입·로그인 검증 규칙"). 실패하면 `/signup?error=validation_failed`로 이동합니다.
2. 이메일이 이미 존재하면 `/signup?error=email_duplicated`로 이동합니다. Google로만 가입된 계정과 이메일이 같은 경우도 같습니다.
   닉네임이 이미 사용 중이면 `/signup?error=nickname_duplicated`로 이동합니다. 저장 시점에 `uk_member_nickname`이 위반되면 같은 결과로 처리합니다 (ERD 2.1).
3. 비밀번호를 BCrypt로 해시하여 `member`에 저장합니다 (`role = USER`, `google_sub = NULL`).
4. 자동 로그인합니다. Spring Security 세션을 생성하고, JWT Access Token과 Refresh Token을 발급합니다 (Refresh Token의 SHA-256 해시는 `refresh_token`에 저장). 쿠키를 설정하고 메인 페이지(`/`)로 이동합니다.
5. 요청 제한: 입력값 검증 실패를 포함한 모든 제출을 회원가입 요청으로 계산합니다. IP별 최근 1시간 5회를 넘으면 `429 RATE_LIMITED`를 반환하고 회원을 만들지 않습니다 (API_SPEC 1.4, 미결정 T15: 계산 범위).

### 회원가입 흐름 (API)
1. 1~3번은 화면 회원가입과 같습니다 (`POST /api/auth/signup`). 실패는 JSON 오류로 응답합니다. 세부 코드는 400 `EMAIL_REQUIRED`·`EMAIL_INVALID`·`NICKNAME_REQUIRED`·`NICKNAME_INVALID`·`PASSWORD_REQUIRED`·`PASSWORD_INVALID`, 409 `EMAIL_DUPLICATED`·`NICKNAME_DUPLICATED`입니다.
2. 4번에서 Spring Security 세션을 만들지 않습니다. JWT 쿠키만 발급하고 201 JSON으로 응답합니다.

### 회원가입·로그인 검증 규칙 (Stage 2-2 구현 기준)

이 규칙은 `member.service`의 입력 검증에 구현되어 있습니다. 실패는 `BusinessException`으로 세부 오류 코드를 던지며, 응답 형식은 API_SPEC 1.2를 따릅니다. 화면 폼은 기존 리다이렉트 규칙을 유지합니다.

| 항목 | 규칙 | 오류 코드 |
|------|------|-----------|
| 이메일 | 필수. 앞뒤 공백 제거 후 소문자 정규화(Locale.ROOT). 공백 없이 `@`와 도메인 점을 포함하는 단순 형식. 최대 255자. 중복 불가 | `EMAIL_REQUIRED`, `EMAIL_INVALID`, `EMAIL_DUPLICATED` |
| 닉네임 | 필수. 앞뒤 공백 제거 후 검증. 2~20자. 허용 문자는 한글 완성형(가-힣), 영문(A-Z, a-z), 숫자(0-9). 특수문자와 내부 공백 불가. 중복 불가 | `NICKNAME_REQUIRED`, `NICKNAME_INVALID`, `NICKNAME_DUPLICATED` |
| 비밀번호 (가입) | 필수. 8~20자. 영문 1자 이상, 숫자 1자 이상, 특수문자 1자 이상(ASCII 문장부호·기호). 공백 불가(일반 스페이스, 탭, 줄바꿈, NBSP 등 Unicode 공백 문자 포함, 모든 코드 포인트 검사). 앞뒤 공백 제거와 소문자 변환을 하지 않는다. `passwordConfirm`과 일치. 저장은 BCrypt 해시 | `PASSWORD_REQUIRED`, `PASSWORD_INVALID` |
| 로그인 이메일 | 필수. 가입과 같은 정규화와 형식 검사 | `EMAIL_REQUIRED`, `EMAIL_INVALID` |
| 로그인 비밀번호 | 필수 여부만 확인. 미입력이면 `PASSWORD_REQUIRED`. 가입 조합 규칙 위반과 `PASSWORD_INVALID`는 적용하지 않음 | `PASSWORD_REQUIRED` |

검증 순서와 중복 처리:
1. 이메일, 닉네임, 비밀번호 순서로 형식을 검증합니다. 이메일과 닉네임의 중복 검사는 형식 검증 후에 수행합니다.
2. 이메일 중복을 먼저 확인하고, 이어서 닉네임 중복을 확인합니다. 둘 다 중복이면 `EMAIL_DUPLICATED`를 반환합니다.
3. 사전 검사 후 저장 시점에 DB UNIQUE 제약(`uk_member_email`, `uk_member_nickname`)이 위반되면 제약 이름으로 구분하여 `EMAIL_DUPLICATED` 또는 `NICKNAME_DUPLICATED`로 바꿉니다. 제약 이름을 알 수 없는 무결성 오류는 중복으로 바꾸지 않고 그대로 전파합니다.
4. 로그인 실패(미가입, 비밀번호 불일치, Google 전용 계정)는 모두 `INVALID_CREDENTIALS`(401)이며, 외부 응답 메시지와 코드가 같습니다. 모든 경로에서 BCrypt 검증을 정확히 한 번 수행합니다. 비밀번호 해시가 없는 경우(미가입, Google 전용)에는 더미 해시와 비교하여 계정 유형에 따른 검증 횟수 차이를 없앱니다.

### 로그인 흐름 (화면)
1. `POST /login`으로 이메일과 비밀번호를 검증합니다. 없거나, 비밀번호가 틀리거나, `password_hash`가 없는(Google 전용) 경우 같은 결과(`/login?error=invalid_credentials`)를 반환합니다. 계정 존재 여부와 가입 방식을 노출하지 않습니다.
   - 요청 제한: 정규화 이메일+IP 실패 5회 뒤 6번째 요청부터, IP 보조 제한은 실패 20회 뒤 21번째부터 `429`를 반환합니다. 제한된 요청은 비밀번호를 검증하지 않습니다 (API_SPEC 1.4).
2. 성공하면 Spring Security 세션을 생성합니다. 세션 ID 변경 등 Spring Security의 기본 세션 보호 정책을 적용합니다 (세부 값은 T11).
3. JWT Access Token을 발급하여 `access_token` 쿠키(HttpOnly, Path `/api`)에 담습니다. 기본 만료 30분.
4. Refresh Token 원문을 발급하여 `refresh_token` 쿠키(HttpOnly, Path `/api/auth`)에 담고, SHA-256 해시를 `refresh_token` 테이블에 저장합니다 (FR-01-05). 기본 만료 14일.
5. 메인 페이지(`/`)로 이동합니다.

### 로그인 흐름 (API)
1. `POST /api/auth/login`으로 이메일과 비밀번호를 검증합니다. 실패는 401 `INVALID_CREDENTIALS`입니다.
2. JWT Access Token과 Refresh Token을 발급합니다. 쿠키를 설정하고 JSON으로 응답합니다.
3. Spring Security 세션을 생성하지 않습니다. 세션이 있더라도 API 인증을 대신하지 않습니다.

### 재발급 흐름 (API, Refresh Token 로테이션, FR-01-06, FR-01-24, FR-01-30, FR-01-36, T14 A안)
1. 요청의 `refresh_token` 쿠키 원문을 SHA-256으로 해시하고, 잠금 없이 조회하여 회원과 `family_id`를 확인합니다. 행이 없으면 `TOKEN_INVALID`(401)를 반환합니다. 어떤 행도 변경하지 않습니다.
2. 트랜잭션을 시작하고 회원 행을 잠금 조회합니다. 같은 트랜잭션에서 `refresh_token` 행을 다시 조회합니다.
3. 재조회한 행으로 아래 순서로 판정합니다.
   - 폐기된 행이면 재사용입니다. 요청 제한보다 먼저 처리합니다. 해당 회원의 모든 활성 Refresh Token에 `revoked_at`을 기록하고 커밋한 뒤, `TOKEN_INVALID`(401)를 반환하고 두 JWT 쿠키를 제거합니다. 회원은 다시 로그인해야 합니다 (T7 A안).
   - 폐기되지 않았으면 같은 `family_id`의 최근 1분 요청 수를 확인합니다. 이미 10회가 기록되어 있으면 이번이 11번째 요청이므로 `RATE_LIMITED`(429)를 반환합니다. 쿠키는 그대로 두고, 토큰과 이벤트도 변경하지 않습니다.
   - 제한을 통과하면 계열 요청 이벤트를 한 건 기록합니다. 만료되었으면 `TOKEN_INVALID`(401)를 반환하고 두 JWT 쿠키를 제거합니다.
   - 활성이고 만료되지 않았으면 기존 행을 폐기하고, 같은 `family_id`로 새 Refresh Token 행을 만들어 커밋합니다. 새 Refresh Token 원문은 해시로만 저장합니다.
4. 정상 갱신일 때만 새 쿠키를 설정합니다.
- 재사용 탐지 결과는 예외로 던지지 않습니다. 커밋한 뒤 응답합니다. 그래야 응답 전에 롤백되지 않습니다.
- 폐기된 행은 재사용 탐지를 위해 삭제하지 않습니다.
- 새 기기 로그인에서만 새 `family_id`를 만듭니다. 회전에서는 값을 유지합니다.
- 화면 세션은 재발급 대상이 아닙니다. 화면 세션의 만료 정책은 T11에서 정합니다.
- 응답이 네트워크에서 유실되면 클라이언트는 새 쿠키를 받지 못합니다. 이후 이전 토큰이 재사용으로 판정될 수 있으며, 이는 T14 A안이 수용한 한계입니다 (재로그인 필요).
- 계열을 확인할 수 없는 요청은 계열별 제한 대상이 아닙니다 (T17 권장안, 미결정).

### 로그아웃 흐름 (화면, FR-01-07, FR-01-25, T12 A안)
1. 브라우저가 `POST /api/auth/logout`을 먼저 호출합니다. 서버는 Refresh Token 행을 폐기하고 JWT 쿠키를 만료시킵니다.
2. 1이 성공한 경우에만 `POST /logout`을 호출합니다 (CSRF 토큰 필요). Spring Security 세션을 무효화하고 SecurityContext를 제거하며 `JSESSIONID`를 만료시킵니다.
3. 로그인 화면(`/login`)으로 이동합니다.
- 1이 실패하면 2를 호출하지 않습니다. 화면 세션을 종료하지 않고 재시도를 안내합니다.
- 1이 성공하고 2가 실패하면 API 토큰은 이미 폐기된 상태입니다. 화면 로그아웃만 재시도할 수 있습니다. 중복 요청은 안전하게 처리합니다.
- `refresh_token` 쿠키의 Path는 `/api/auth`이므로 `/logout` 요청에서는 전송되지 않습니다. 그래서 API 로그아웃을 먼저 호출합니다.

### Access Token 만료 대응 (화면, T13 A안, T14 A안)

화면 세션이 유효해도 Access Token은 만료될 수 있습니다 (DEVELOPMENT_RULES 5.7.2).
1. API 요청이 `401 TOKEN_INVALID`를 받으면 갱신 절차를 시작합니다.
2. 같은 탭에 진행 중인 갱신이 있으면 새로 시작하지 않고 그 결과를 기다립니다 (S1, single-flight).
3. Web Locks API로 고정 이름(`busan-auth-refresh`)의 잠금을 얻습니다. 같은 브라우저의 탭들이 같은 이름을 씁니다 (S2).
4. 잠금 안에서 `GET /api/auth/me`로 인증 상태를 확인합니다. HttpOnly 쿠키를 JavaScript로 읽지 않습니다.
5. `200`이면 다른 탭이 이미 갱신한 것입니다. Refresh 요청 없이 원래 요청을 재시도합니다.
6. `401`이면 `POST /api/auth/refresh`를 한 번 호출합니다. 성공하면 잠금을 해제하고 원래 요청을 한 번 재시도합니다.
7. 재발급이 실패하면(재사용 탐지로 전체 폐기된 경우 포함) 재로그인을 안내합니다. 화면 세션은 갱신하지 않습니다.
8. 재시도한 요청도 401이면 추가 갱신을 하지 않고 재로그인을 안내합니다. 재발급 API가 401을 반환해도 재호출하지 않습니다.
9. Web Locks API는 보안 컨텍스트에서만 사용할 수 있습니다. 운영 환경은 HTTPS를 전제로 하고, 로컬 개발은 `localhost`를 사용할 수 있습니다. 보안 컨텍스트가 아니거나 Web Locks API를 지원하지 않으면 같은 탭의 single-flight만 보장합니다. 이때 여러 탭의 동시 갱신은 완전히 방지하지 못하며 S2 오탐이 생길 수 있습니다. 이 fallback은 T14 A안의 정책과 보안 수준을 바꾸지 않습니다. 외부 라이브러리는 추가하지 않습니다.
10. Refresh Token 원문은 JavaScript에서 읽거나 저장하지 않습니다.
11. 재발급이 `429`를 받으면 쿠키는 그대로 둡니다. 화면 처리(재시도 안내 등)는 T20에서 정합니다. 재로그인 안내를 기계적으로 적용하지 않습니다.

### 동시 갱신과 재사용 처리 (T14 A안 확정)

| 시나리오 | 처리 | 한계 |
|----------|------|------|
| S1. 같은 탭의 여러 요청이 동시에 401 | single-flight로 Refresh 1회 | 없음 |
| S2. 여러 탭에서 동시에 401 | Web Locks 직렬화 후 `/api/auth/me`로 재확인, 이미 갱신되었으면 Refresh 생략 | Web Locks 미지원 브라우저에서는 보장되지 않음 |
| S3. Refresh 응답 네트워크 유실 | 이전 토큰이 재사용으로 판정되어 전체 폐기 가능 | 이번 A안에서 해결하지 않음. 재로그인 필요 |
| S4. 다른 기기의 이전 토큰 재사용 | Web Locks 미적용. T7에 따라 회원의 모든 활성 토큰 폐기 | 다른 기기도 재로그인 필요 |
| S5. 실제 탈취 토큰 재사용 | 재사용 탐지, 전체 폐기, 재로그인 요구. 보안 이벤트로 기록 (토큰 원문 제외) | 서버는 정상 사용자와 공격자를 확실히 구별할 수 없음 |

### Google 신규 가입 흐름 (FR-01-23, T3 A안)

1. 콜백에서 `state`와 `google_sub`로 회원을 조회했을 때 기존 회원이 없고, 같은 이메일의 기존 회원도 없으며, `email_verified`가 참이면 신규 가입 흐름으로 진입합니다.
2. 회원을 만들지 않습니다. OAuth2 임시 세션에 가입 진행 상태(Google `sub`, 이메일)를 저장합니다.
3. Spring Security 세션과 JWT를 발급하지 않습니다. Google 인증 객체를 로그인 상태로 남기지 않습니다. 닉네임 설정 화면으로 이동합니다.
4. 닉네임 설정 화면에서 닉네임을 입력하고 제출합니다 (CSRF 토큰 필요). Google 프로필 이름을 닉네임으로 자동 사용하지 않습니다.
5. 닉네임 규칙(길이, 허용 문자)을 벗어나면 `validation_failed`, 중복이면 `nickname_duplicated`로 같은 화면에 다시 입력하도록 합니다. 회원은 만들지 않습니다. 닉네임 규칙은 회원가입과 같습니다.
6. 검증이 끝나면 회원을 만들고(`role = USER`, `password_hash = NULL`, `google_sub = sub`), 화면 세션과 JWT를 함께 발급한 뒤 메인 페이지(`/`)로 이동합니다. 가입 진행 상태를 제거합니다.
7. 닉네임 설정 전에 이탈하면 회원은 만들어지지 않습니다. 가입 진행 상태는 흐름이 끝나면 제거합니다.
- `nickname`에는 `uk_member_nickname` UNIQUE 제약이 있습니다. 중복 검사는 저장 직전에 수행하고, 저장 시 위반은 `nickname_duplicated`로 처리합니다 (DEVELOPMENT_RULES 5.7.3, 6.4).

### 화면 세션, API JWT, OAuth2 임시 세션의 관계

| 구분 | 역할 | 수명 |
|------|------|------|
| 화면 세션 (Spring Security) | 일반 화면 요청의 회원 식별 | 세션 만료 또는 화면 로그아웃까지 (세부 값 T11) |
| API JWT | `/api/**` 요청의 회원 식별. Access Token(쿠키 또는 Bearer), Refresh Token(쿠키, DB에는 해시) | Access Token 30분, Refresh Token 14일 |
| OAuth2 임시 상태 (HTTP 세션 내) | Google 인증 진행 상태만 보관: `state`, Authorization Request, 흐름 종류, 연동 대상 회원 ID(연동 시) 또는 가입 진행 상태(신규 가입 시: Google `sub`, 이메일) | 인증 흐름이 끝날 때까지 (T11 A안) |

### Google 계정 연동 흐름 (FR-01-10, FR-01-11, FR-01-12, FR-01-15, T11 A안)

연동 시작 (화면):
1. 로그인된 회원이 `/mypage`에서 "Google 계정 연동"을 누릅니다. 연동 시작 요청은 CSRF 토큰을 포함한 화면 POST입니다. 정확한 경로는 구현 단계에서 확정합니다.
2. 세션에서 로그인 회원 ID를 확인합니다. 로그인되지 않았으면 `/login`으로 이동합니다.
3. 세션에 연동 진행 상태(연동 대상 회원 ID, 흐름 = 계정 연동)를 저장하고, 남아 있던 일반 로그인 진행 상태는 제거합니다.
4. Spring Security의 기본 OAuth2 Authorization Request 저장 방식으로 `state`와 Authorization Request를 HTTP 세션에 저장한 뒤 Google 인증 페이지로 이동합니다.
5. 기존 로그인 세션과 API JWT는 변경하지 않습니다.

콜백 (API_SPEC 2.6.1과 같은 절차):
1. OAuth2 `state`를 검증합니다. 실패하면 아무것도 바꾸지 않고 `/mypage?error=google_failed`로 이동합니다.
2. 세션의 연동 진행 상태가 있을 때만 계정 연동으로 처리합니다. 없으면 일반 로그인 처리입니다.
3. 세션에 저장된 연동 대상 회원 ID를 확인합니다. 없거나 회원이 존재하지 않으면 거부합니다.
4. Google 인증 코드를 교환하고 Google 사용자 정보를 조회합니다. `email_verified`가 아니면 `/mypage?error=google_unverified`로 이동합니다.
5. Google `sub`가 다른 회원에게 이미 연결되어 있으면 `/mypage?error=google_already_linked`로 이동합니다 (FR-01-11, 409 `GOOGLE_ALREADY_LINKED`에 해당).
6. 중복이 없으면 연동 대상 회원의 `google_sub`에 `sub`를 저장하고 `/mypage?google=linked`로 이동합니다. 이미 대상 회원에게 연결되어 있으면 변경 없이 같은 결과로 이동합니다.
7. 연동 진행 상태와 `state`를 세션에서 제거합니다. 성공과 실패 모두 수행합니다.

원칙:
- 연동 대상 회원은 연동 시작 시점의 로그인 세션에 저장된 회원 ID입니다. Callback 시점의 Google 이메일이나 새로 만든 인증 주체로 판단하지 않습니다.
- Google 이메일이 기존 회원과 같다는 이유만으로 자동 연결하지 않습니다.
- 연동 과정에서 기존 로그인 세션을 무효화하거나 Google 인증 주체로 대체하지 않습니다. API JWT를 재발급하거나 교체하지 않습니다.
- 연동 실패 시 기존 세션과 JWT를 변경하지 않고, 임시 상태만 정리합니다.
- 연동은 로그인된 본인 세션에서 시작한 흐름만 허용합니다. 다른 회원의 계정에 연동할 수 없습니다.
- 본인 확인은 로그인된 본인 세션, CSRF 보호 POST, OAuth2 `state` 검증으로 수행합니다 (T8 A안). 추가 인증 수단은 도입하지 않습니다.

### Google 계정 연동 해제 흐름 (FR-01-12, FR-01-13, FR-01-22, T8 A안)
1. `/mypage`에서 "Google 계정 연동 해제"를 누르고 현재 비밀번호를 입력합니다. 요청은 로그인된 본인 세션에서 CSRF 토큰을 포함한 화면 POST입니다 (정확한 경로는 구현 단계에서 확정, API_SPEC 2.7).
2. 연동된 Google 계정이 없으면 `/mypage?error=google_not_linked`로 이동합니다.
3. 비밀번호가 없는 회원(Google 전용 계정)이면 해제하지 않고 `/mypage?error=password_required`로 이동합니다. 이 경우 먼저 "로컬 비밀번호 설정"을 완료해야 합니다.
4. 비밀번호가 틀리면 해제하지 않고 `/mypage?error=invalid_credentials`로 이동합니다.
5. 성공하면 `google_sub`를 `NULL`로 저장합니다. 이후 Google 로그인은 자동 연결 없이 `email_account_exists` 흐름이 됩니다.

### 로컬 비밀번호 설정 흐름 (Google 전용 회원, FR-01-22)
1. `/mypage`에서 "비밀번호 설정"을 선택합니다 (Google 전용 회원에게만 표시). 화면 세션과 CSRF 토큰이 필요합니다.
2. 비밀번호와 비밀번호 확인을 입력합니다. 회원가입과 같은 규칙(8~20자, 영문·숫자·특수문자 각 1자 이상, 공백 불가)을 적용합니다. 검증에 실패하면 같은 화면에 오류를 표시합니다.
3. 비밀번호를 BCrypt로 해시하여 `password_hash`에 저장합니다. 이미 비밀번호가 있으면 설정하지 않습니다 (비밀번호 변경은 이번 명세 범위가 아닙니다).
4. 설정이 끝나면 `/mypage?password=set`으로 이동합니다. 이후 연동 해제를 할 수 있습니다.

### 요청 제한 (Rate Limit, FR-01-28~FR-01-36)

기준의 원본은 API_SPEC 1.4입니다. 이 절은 기능별 동작을 요약합니다. 모든 제한은 Rolling Window로 계산하며, 초과 응답은 `429`, `RATE_LIMITED`, `Retry-After`입니다.

| 대상 | 제한 | 초과 시 |
|------|------|---------|
| 로그인 | 정규화 이메일+IP 최근 15분 실패 5회, IP 보조 최근 15분 실패 20회 | 429. 비밀번호를 검증하지 않음 |
| 회원가입 (화면, API, Google 신규 가입 닉네임 제출 공통) | IP별 최근 1시간 5회. 입력값 검증 실패도 포함 | 429. 회원을 만들지 않음 |
| Refresh | Refresh Token 계열별 최근 1분 10회 | 429. 쿠키 유지. 재사용 탐지는 생략되지 않음 |
| Google OAuth2 (시작, 콜백, 계정 연동 콜백) | IP별 최근 15분 10회 (시작과 콜백 공유) | 429. OAuth2 임시 상태만 정리하고 세션과 JWT 유지 |

- 로그인 성공 시 해당 이메일+IP의 실패 기록을 초기화합니다.
- 제한된 요청은 새 이벤트를 만들지 않습니다.
- 429 응답은 JWT 쿠키를 바꾸지 않습니다.
- 로그인 이벤트에는 정규화 이메일과 IP의 HMAC-SHA256 값만 저장합니다 (R2). 원문 이메일과 원문 IP는 저장하지 않습니다.
- 이벤트는 로그인, 회원가입, Refresh 모두 24시간 보관 후 삭제합니다 (R3). Google 이벤트 보존은 T18 권장안입니다.
- 미결정 항목: 회원가입 요청 계산 범위 (T15, 권장: JSON 파싱과 DTO 검증 이전에 계산), IP 키 HMAC 처리 (T16), 계열을 확인할 수 없는 Refresh 요청 (T17), Google 이벤트 보존 (T18), 계정 연동 시작 POST 포함 여부 (T19), 429 응답의 화면 처리와 Refresh 429의 자동 재발급 처리 (T20).


### 인증 처리 규칙
- JWT 서명 키는 환경 변수 `JWT_SECRET`에서 읽습니다.
- API 인증 필터는 `/api/**`에만 적용합니다. `access_token` 쿠키 또는 `Authorization: Bearer` 헤더를 읽습니다. 세션만으로 인증하지 않습니다.
- 화면 인증은 Spring Security 세션으로 처리합니다. 인증이 필요한 화면은 세션이 없으면 `/login`으로 리다이렉트합니다.
- CSRF 보호는 화면 세션 쿠키 기반 상태 변경 요청과 JWT 쿠키 기반 API 상태 변경 요청(POST, PUT, DELETE)에 적용합니다 (NFR-04). Bearer 헤더 요청은 CSRF 헤더를 요구하지 않습니다.
- 쿠키 속성과 Path는 API_SPEC 1.1절을 따릅니다. 운영 환경에서는 모든 인증 쿠키에 Secure를 설정합니다.
- 토큰이 만료되거나 위조되면 API는 401 JSON으로 응답합니다. 화면은 로그인 페이지로 리다이렉트합니다. 화면 인증 실패가 JSON으로, API 인증 실패가 리다이렉트로 처리되지 않습니다.
- 로그에 비밀번호, 토큰 원문, Refresh Token 원문을 남기지 않습니다 (NFR-10).

### 예외
| 상황 | 응답 |
|------|------|
| 이메일 중복 (API) | 409 `EMAIL_DUPLICATED` |
| 닉네임 중복 (API) | 409 `NICKNAME_DUPLICATED` |
| 닉네임 중복 (화면 회원가입) | `/signup?error=nickname_duplicated` |
| 이메일 중복 (화면) | `/signup?error=email_duplicated` |
| 이메일 미입력 (API) | 400 `EMAIL_REQUIRED` |
| 이메일 형식·길이 오류 (API) | 400 `EMAIL_INVALID` |
| 닉네임 미입력 (API) | 400 `NICKNAME_REQUIRED` |
| 닉네임 규칙 위반 (API) | 400 `NICKNAME_INVALID` |
| 회원가입·로컬 비밀번호 설정 시 비밀번호 또는 확인값 미입력 (API) | 400 `PASSWORD_REQUIRED` |
| 로그인 비밀번호 미입력 (API) | 400 `PASSWORD_REQUIRED` |
| 회원가입·로컬 비밀번호 설정 시 비밀번호 정책 위반 또는 확인값 불일치 (API) | 400 `PASSWORD_INVALID` (로그인에는 적용하지 않음) |
| 닉네임 중복 (API) | 409 `NICKNAME_DUPLICATED` |
| 로그인 입력 누락 (API) | 400 `EMAIL_REQUIRED`·`PASSWORD_REQUIRED`, 형식 오류 400 `EMAIL_INVALID` |
| 입력값 검증 실패 (화면) | `/signup?error=validation_failed` (화면은 세부 코드를 쿼리로 노출하지 않음) |
| 자격 증명 불일치 (API, Google 전용 계정 포함) | 401 `INVALID_CREDENTIALS` |
| 자격 증명 불일치 (화면, Google 전용 계정 포함) | `/login?error=invalid_credentials` |
| 토큰 없음·만료·폐기·위조 (API) | 401 `TOKEN_INVALID` |
| 요청 제한 초과 (로그인, 회원가입, Refresh, Google OAuth2) | 429 `RATE_LIMITED` (`Retry-After`). 쿠키·세션·JWT 유지 (Google은 OAuth2 임시 상태만 정리) |
| 폐기된 Refresh Token 재사용 | 401 `TOKEN_INVALID`. 회원의 모든 활성 Refresh Token 폐기 (429보다 먼저 처리) |
| 세션 없음 (화면 인증 필요 페이지) | `/login`으로 리다이렉트 |
| 재인증 실패 (연동 해제) | `/mypage?error=invalid_credentials` (해제하지 않음) |
| 비밀번호 없는 회원의 연동 해제 | `/mypage?error=password_required` (해제하지 않음) |
| 닉네임 형식 오류 (신규 가입) | 닉네임 설정 화면 `?error=validation_failed` (회원 미생성) |
| 닉네임 중복 (신규 가입) | 닉네임 설정 화면 `?error=nickname_duplicated` (회원 미생성) |
| 폐기된 Refresh Token 재사용 (API) | 401 `TOKEN_INVALID`, 해당 회원의 모든 활성 Refresh Token 폐기 |
| Google 인증 실패 | `/login?error=google_failed` |
| 기존 이메일 계정과 충돌 (자동 연결 없음) | `/login?error=email_account_exists` |
| 검증되지 않은 Google 계정 | `/login?error=google_unverified` 또는 `/mypage?error=google_unverified` |
| 다른 회원에게 연결된 Google 계정 | `/mypage?error=google_already_linked` (409 `GOOGLE_ALREADY_LINKED`) |
| 연동되지 않은 상태에서 해제 | `/mypage?error=google_not_linked` |

---

## F1-02 맛집 관리

### 화면
- `/` 및 `/restaurants`: 맛집 목록 (카테고리 필터, 검색어, 페이지네이션)
- `/restaurants/{id}`: 맛집 상세 (정보, 이미지 갤러리, 평균 평점, 리뷰 목록, 찜 버튼, AI 요약). 작성자에게만 "수정", "삭제" 버튼을 표시합니다.
- `/restaurants/new`: 맛집 등록 폼 (회원 전용)
- `/restaurants/{id}/edit`: 맛집 수정 폼 (작성자 전용)

### 등록 흐름
1. 회원이 폼을 제출합니다. 필수 항목: 이름(1~50자), 카테고리, 주소(1~200자), 설명(1~2000자). 선택: 전화번호(숫자와 하이픈), 위도·경도.
2. 카테고리 ID가 존재하는지 확인합니다.
3. `restaurant`에 저장하고, `member_id`를 현재 회원으로 설정합니다.
4. 상세 페이지로 이동합니다. 이미지는 F1-03에서 추가합니다.

### 수정 흐름
1. 작성자 본인인지 확인합니다. 아니면 403 `FORBIDDEN`. 관리자는 F1-09의 관리자 수정 API를 사용합니다.
2. 등록과 같은 검증을 적용합니다.
3. 변경 내용을 저장하고 `updated_at`을 갱신합니다. AI 요약 캐시는 맛집 정보 변경만으로는 무효화하지 않습니다 (리뷰 변경 시에만 무효화).

### 삭제 흐름 (FR-02-09, Q2)
맛집 삭제는 완전 삭제입니다. 소프트 삭제를 쓰지 않습니다.

1. 작성자 본인인지 확인합니다. 아니면 403. 관리자는 F1-09의 관리자 삭제 API를 사용합니다.
2. 확인 대화상자를 거친 뒤 삭제 요청을 보냅니다.
3. 서버는 다음을 **하나의 DB 트랜잭션**에서 수행합니다.
   1. `restaurant`을 조회합니다. 없으면 404 `RESTAURANT_NOT_FOUND`.
   2. `restaurant_image`에서 맛집의 `s3_key` 목록을 조회합니다.
   3. 각 `s3_key`를 `storage_delete_task`에 기록합니다.
   4. `ai_review_summary`, `favorite`, `review`, `restaurant_image`, `restaurant` 행을 차례로 삭제합니다.
   5. 커밋합니다.
4. 커밋 후 DB 트랜잭션 밖에서 각 `s3_key`의 S3 객체를 삭제합니다.
   - 성공하면 해당 `storage_delete_task` 행을 삭제합니다.
   - 실패하면 `retry_count`를 올리고 `last_error`를 기록합니다. 삭제 자체는 되돌리지 않습니다.
5. 응답은 `200`과 함께 `deletedImageCount`, `storageDeleteFailedCount`를 반환합니다 (API_SPEC 4.5).
6. 목록 페이지로 이동합니다. 실패 건이 있으면 작성자에게는 "일부 이미지 삭제가 지연되고 있습니다" 안내를 표시하고, 실제 재시도는 관리자가 처리합니다 (F1-09).

규칙:
- DB 삭제와 S3 삭제는 하나의 트랜잭션으로 묶지 않습니다. 두 저장소의 원자성을 보장한다고 표현하지 않습니다 (FR-03-07, NFR-17).
- S3에 객체가 이미 없으면 성공으로 처리합니다.
- 커밋 후 프로세스가 중단되더라도 `storage_delete_task` 행이 DB에 남으므로 키가 유실되지 않습니다.

### 목록·검색 흐름
- 파라미터: `category`(카테고리 ID, 선택), `keyword`(선택), `page`(기본 0), `size`(기본 12, 최대 50), `sort`(`latest` 기본, `rating`).
- 키워드는 이름 또는 주소에 대해 부분 일치(`LIKE %keyword%`)로 검색합니다.
- 결과는 맛집 카드(이름, 카테고리, 평균 평점, 리뷰 수, 대표 이미지)로 보여 줍니다.

### 카테고리 규칙
- 카테고리는 DB 시드 데이터로 제공합니다: 한식, 중식, 일식, 양식, 분식, 카페·디저트, 해산물, 고기·구이, 술집, 기타.
- 카테고리 추가·수정은 이번 범위 밖입니다 (DB 직접 관리).

### 예외
| 상황 | 응답 |
|------|------|
| 존재하지 않는 맛집 | 404 `RESTAURANT_NOT_FOUND` |
| 작성자가 아님 | 403 `FORBIDDEN` |
| 존재하지 않는 카테고리 | 400 `CATEGORY_NOT_FOUND` |
| 입력값 검증 실패 | 400 `VALIDATION_FAILED` |
| 삭제 중 DB 오류 | 500 `INTERNAL_ERROR`. 트랜잭션은 롤백되고 S3는 건드리지 않음 |
| 삭제 후 S3 오류 | 200. `storageDeleteFailedCount`로 알림. 재시도는 F1-09 |

---

## F1-03 맛집 이미지 (S3)

### 화면
- 맛집 수정 화면의 이미지 영역: 파일 선택, 업로드 목록, 삭제 버튼 (작성자 전용)
- 상세 화면: 이미지 갤러리 (없으면 기본 이미지)

### 업로드 흐름
1. 작성자가 파일을 선택합니다. 클라이언트에서 형식과 크기를 미리 검사합니다.
2. 서버에서 다시 검사합니다: MIME 타입(`image/jpeg`, `image/png`, `image/webp`), 확장자, 파일 크기(5MB 이하), 맛집당 이미지 수(5장 이하).
3. 객체 키를 `restaurants/{restaurantId}/{uuid}.{ext}` 형식으로 생성합니다. 원본 파일명은 DB에만 저장합니다.
4. S3에 업로드합니다 (버킷은 환경 변수 `AWS_S3_BUCKET`).
5. DB에 `restaurant_image` 메타데이터를 저장합니다.
6. 5번이 실패하면 업로드한 객체를 삭제합니다. 이 삭제도 실패하면 `storage_delete_task`에 기록합니다. 사용자에게는 "이미지 업로드에 실패했습니다"를 표시합니다.
7. S3 업로드 자체가 실패하면 DB에 아무것도 저장하지 않고 502 `STORAGE_ERROR`를 반환합니다.

### 삭제 흐름 (FR-03-04, FR-03-06, FR-03-07, Q2)
1. 작성자 본인인지 확인합니다. 관리자는 F1-09의 관리자 이미지 삭제 API를 사용합니다.
2. 하나의 DB 트랜잭션에서 `restaurant_image` 행을 삭제하고, 같은 `s3_key`를 `storage_delete_task`에 기록한 뒤 커밋합니다.
3. 커밋 후 S3 객체를 삭제합니다.
   - 성공하면 `storage_delete_task` 행을 삭제합니다.
   - 실패하면 `retry_count`를 올리고 `last_error`를 기록합니다.
4. 응답은 `200`과 함께 `storageDeleted`(true/false)를 반환합니다 (API_SPEC 5.2). DB 삭제는 완료된 상태입니다.
5. 재시도는 관리자가 F1-09 화면이나 API로 수행합니다.

### 규칙
- AWS 자격 증명은 환경 변수 또는 EC2 인스턴스 역할로 제공합니다. 코드에 직접 쓰지 않습니다.
- 이미지 공개 URL은 S3 객체 키와 리전·버킷 정보로 조합합니다. 버킷 정책은 구현 단계에서 확정합니다.
- S3 오류 메시지를 `last_error`에 저장할 때 자격 증명이나 서명 정보가 포함되지 않도록 걸러냅니다.

### 예외
| 상황 | 응답 |
|------|------|
| 허용되지 않은 형식 | 400 `IMAGE_TYPE_NOT_ALLOWED` |
| 파일 크기 초과 | 400 `IMAGE_TOO_LARGE` |
| 이미지 개수 초과 | 400 `IMAGE_LIMIT_EXCEEDED` |
| 업로드 시 S3 오류 | 502 `STORAGE_ERROR` |
| 삭제 시 S3 오류 | 200, `storageDeleted: false`. 재시도 대기 |

---

## F1-04 리뷰

### 화면
- 맛집 상세 화면 하단: 리뷰 목록(작성자 닉네임, 평점, 내용, 작성일), 리뷰 작성 폼(회원 전용)
- 본인 리뷰에는 삭제 버튼 표시

### 작성 흐름
1. 회원이 평점(1~5 정수)과 내용(1~500자)을 입력합니다.
2. 같은 회원이 같은 맛집에 이미 리뷰를 작성했는지 확인합니다. 있으면 409 `REVIEW_ALREADY_EXISTS`.
3. `review`에 저장합니다.
4. 맛집의 `review_count`와 `average_rating`을 갱신합니다.
5. F1-05의 AI 요약 캐시를 무효화합니다.

### 삭제 흐름 (FR-04-03)
1. 리뷰 작성자 본인인지 확인합니다. 아니면 403. 관리자는 F1-09의 관리자 리뷰 삭제 API를 사용합니다.
2. `review`를 삭제합니다.
3. `review_count`와 `average_rating`을 갱신합니다.
4. AI 요약 캐시를 무효화합니다.

### 평균 평점 규칙
- `average_rating` = 해당 맛집 리뷰 평점의 평균, 소수점 첫째 자리 반올림.
- 리뷰가 없으면 `average_rating`은 0.0, `review_count`는 0입니다.
- 평균과 리뷰 수는 리뷰 변경 트랜잭션 안에서 함께 갱신합니다 (동시성 문제는 구현 단계에서 비관적 락 또는 원자적 UPDATE로 처리).

### 예외
| 상황 | 응답 |
|------|------|
| 중복 리뷰 | 409 `REVIEW_ALREADY_EXISTS` |
| 평점 범위 오류 | 400 `VALIDATION_FAILED` |
| 작성자가 아님 | 403 `FORBIDDEN` |
| 존재하지 않는 리뷰 | 404 `REVIEW_NOT_FOUND` |

---

## F1-05 AI 리뷰 요약 (캐싱)

### 동작 규칙
- 맛집의 리뷰 수가 3 미만이면 요약을 생성하지 않고 "리뷰 3개 이상이 되면 AI 요약을 볼 수 있습니다." 문구를 표시합니다.
- 리뷰 수가 3 이상이면 다음 순서로 처리합니다.
  1. `ai_review_summary`에 해당 맛집 행이 있으면 캐시된 요약을 반환합니다.
  2. 없으면 리뷰 내용을 모아 OpenRouter에 요약을 요청합니다.
  3. 응답을 `ai_review_summary`에 저장하고 반환합니다.
- 모델 ID는 `OPENROUTER_MODEL` 환경 변수에서 읽습니다. 코드에 고정하지 않습니다 (FR-05-02, NFR-18).

### 캐시 무효화
- 리뷰가 작성되거나 삭제되면(회원 삭제, 관리자 삭제 모두 포함) 해당 맛집의 `ai_review_summary` 행을 삭제합니다.
- 맛집이 삭제되면 `ai_review_summary` 행도 함께 삭제됩니다 (F1-02 삭제 흐름).
- 다음 상세 페이지 요청 시 재생성합니다.

### 프롬프트 규칙
- 입력: 리뷰 내용과 평점 목록. 리뷰 작성자 닉네임은 포함하지 않습니다.
- 출력 요구: 한국어 3~5문장, 맛·분위기·추천 대상 중심.
- 리뷰 내용은 시스템 지시가 아닌 데이터로 전달하고, 프롬프트 인젝션 문구를 무시하도록 시스템 메시지에 명시합니다.

### 실패 처리
- OpenRouter 호출이 실패하거나 타임아웃(기본 10초)되면 요약 영역에 "요약을 불러올 수 없습니다. 잠시 후 다시 시도해 주세요." 표시.
- 실패한 결과는 캐시하지 않습니다. 상세 페이지의 나머지 정보는 정상 표시합니다 (FR-05-06).

### 예외
| 상황 | 응답 |
|------|------|
| 리뷰 3개 미만 | 200, `status: NOT_ENOUGH_REVIEWS`, `summary: null` |
| AI 호출 실패 | 200, `status: UNAVAILABLE`, `summary: null` (상세 화면은 정상) |

---

## F1-06 찜

### 화면
- 맛집 카드·상세 화면의 찜 버튼 (채워진 하트 / 빈 하트)
- `/favorites`: 내 찜 목록 (회원 전용)

### 찜하기 흐름
1. 회원이 찜 버튼을 누릅니다.
2. `favorite`에 (회원, 맛집) 쌍을 저장합니다. 이미 있으면 409 `FAVORITE_ALREADY_EXISTS`.
3. 버튼 상태를 찜됨으로 바꿉니다.

### 찜 취소 흐름
1. 회원이 찜 버튼을 다시 누릅니다.
2. 해당 `favorite` 행을 삭제합니다. 없으면 404 `FAVORITE_NOT_FOUND`.

### 찜 목록 규칙
- 최근 찜한 순으로 정렬합니다. 페이지 크기 기본 12.
- 맛집이 삭제되면 찜 행도 함께 삭제되므로 목록에 나타나지 않습니다 (F1-02 삭제 흐름).

---

## F1-07 AI 맛집 추천

### 화면
- `/recommend`: 취향 입력 텍스트 영역 + "추천 받기" 버튼 (회원 전용)
- 결과: 추천 맛집 카드 목록 (이름, 카테고리, 평균 평점, 추천 이유, 상세 링크)

### 처리 흐름
1. 회원이 취향 문장을 입력합니다 (1~200자).
2. 서버가 DB의 맛집 목록을 후보로 가져옵니다. 후보는 다음 최대 개수로 제한합니다: 최근 등록 순 최대 100개 (토큰 비용 제한).
3. 후보가 0개이면 AI를 호출하지 않고 "등록된 맛집이 없어 추천할 수 없습니다." 를 반환합니다 (FR-07-05).
4. 프롬프트에 후보 목록(ID, 이름, 카테고리, 주소, 평균 평점)과 취향 문장을 담아 OpenRouter에 요청합니다. 모델 ID는 `OPENROUTER_MODEL`에서 읽습니다. 응답 형식은 JSON 배열 `[{ "restaurantId": number, "reason": string }]`로 지정합니다.
5. 응답을 파싱하고, 맛집 ID가 후보 목록에 있는 것만 남깁니다 (FR-07-03). 없는 ID는 조용히 제외합니다.
6. 최대 5개까지 반환합니다.

### 규칙
- 추천은 DB에 등록된 맛집만 대상으로 합니다. AI가 임의의 식당 이름을 만들어도 결과에 포함하지 않습니다.
- 추천 결과는 저장하지 않습니다 (이번 범위).
- 취향 문장도 사용자 입력이므로 데이터로만 전달하고, 시스템 지시를 무시하도록 프롬프트에 명시합니다.
- AI 응답 검증: 응답은 JSON 배열인지 확인하고, 각 항목의 `restaurantId`가 후보에 있는지 확인합니다. 검증에 실패하면 `AI_RESPONSE_INVALID`로 처리하고 결과를 노출하지 않습니다.

### 예외
| 상황 | 응답 |
|------|------|
| 취향 입력 누락·길이 초과 | 400 `VALIDATION_FAILED` |
| 후보 맛집 없음 | 200, `recommendations: []`, `message` 포함 |
| AI 응답 파싱·검증 실패 | 502 `AI_RESPONSE_INVALID`, 화면에는 재시도 안내 |
| AI 호출 실패·타임아웃 | 502 `AI_UNAVAILABLE` |

---

## F1-08 메인 페이지

### 화면 구성 (`/`)
1. 상단: 헤더 내비게이션, 검색창 (입력 시 `/restaurants?keyword=...`로 이동)
2. 카테고리 영역: 카테고리 카드 10개 (클릭 시 `/restaurants?category={id}`)
3. 최신 맛집 영역: 최근 등록 맛집 8개 카드
4. 하단: 푸터

### 규칙
- 카테고리 목록과 최신 맛집 목록은 비회원도 볼 수 있습니다.
- 최신 맛집은 `created_at` 내림차순입니다.
- 맛집이 하나도 없으면 "첫 번째 맛집을 등록해 보세요." 안내와 등록 버튼을 표시합니다.

---

## F1-09 관리자 기능

관리자 기능은 필수 범위입니다 (Q5). 일반 회원 기능과 화면·API 영역을 분리합니다.

### 권한 모델
- 권한은 `member.role`로 구분합니다: `USER`(일반 회원), `ADMIN`(관리자). 별도 관리자 테이블은 없습니다.
- 관리자 화면 `/admin/**`과 관리자 API `/api/admin/**`은 Spring Security에서 `ADMIN` 권한을 요구합니다.
- 권한 검증은 서버에서 요청마다 수행합니다. 관리자 URL을 숨기는 것만으로 보호한다고 보지 않습니다 (FR-09-08).
- 일반 회원은 자신의 `role`을 바꾸는 API나 입력 경로가 없습니다. 권한 부여·변경 정책은 T2에서 정합니다 (FR-09-09).
- 로그인하지 않은 요청은 401, `USER` 요청은 403입니다.

### 화면
- `/admin`: 대시보드 (회원 수, 맛집 수, 리뷰 수, 최근 7일 신규 등록, S3 삭제 재시도 대기 수)
- `/admin/restaurants`: 전체 맛집 목록. 검색, 수정, 삭제
- `/admin/reviews`: 전체 리뷰 목록. 작성자·맛집 필터, 삭제
- `/admin/members`: 회원 목록과 상세. 조회만 제공 (정지·탈퇴·권한 변경은 T1, T2)
- `/admin/storage-deletions`: S3 삭제 재시도 대기 목록과 "재시도" 버튼

### 맛집 관리 흐름 (FR-09-02)
1. 관리자가 전체 맛집 목록을 조회합니다 (`GET /api/admin/restaurants`). 작성자 정보가 함께 표시됩니다.
2. 수정은 `PUT /api/admin/restaurants/{id}`로 합니다. 작성자는 바뀌지 않습니다.
3. 삭제는 `DELETE /api/admin/restaurants/{id}`로 합니다. F1-02의 삭제 흐름(DB 트랜잭션 → 커밋 → S3 삭제, 실패 키 추적)과 같습니다.
4. 이미지 추가·삭제도 관리자 API(`/api/admin/restaurants/{id}/images`)로 할 수 있습니다. 처리 방식은 F1-03과 같습니다.

### 리뷰 관리 흐름 (FR-09-03)
1. 관리자가 전체 리뷰를 조회합니다 (`GET /api/admin/reviews`). 맛집이나 회원으로 필터링할 수 있습니다.
2. 삭제는 `DELETE /api/admin/reviews/{reviewId}`로 합니다.
3. 삭제 후 맛집의 `review_count`와 `average_rating`을 갱신하고, AI 요약 캐시를 삭제합니다 (F1-04 삭제 흐름과 같음).

### 회원 관리 흐름 (FR-09-04)
- 목록: `GET /api/admin/members`. 이메일·닉네임 검색 가능. 역할, Google 연동 여부, 맛집 수, 리뷰 수를 표시합니다.
- 상세: `GET /api/admin/members/{memberId}`.
- 정지, 탈퇴, 권한 변경 기능은 이번 명세에 없습니다. T1, T2에서 정책을 정한 뒤 추가합니다.

### 대시보드 흐름 (FR-09-05)
- `GET /api/admin/dashboard`가 기존 테이블에 대한 집계를 반환합니다.
- 최근 7일 기준은 서버 시각 `Asia/Seoul`입니다.

### S3 삭제 재시도 흐름 (FR-03-06, FR-09-06)
1. 관리자가 `/admin/storage-deletions`에서 대기 목록(`GET /api/admin/storage-deletions`)을 확인합니다.
2. "재시도"를 누르면 `POST /api/admin/storage-deletions/retry`가 대기 행을 하나씩 처리합니다.
3. 성공하면 행을 삭제합니다. 실패하면 `retry_count`를 올리고 `last_error`를 기록합니다.
4. 결과(`processed`, `succeeded`, `failed`)를 화면에 표시합니다.
- 주기적 자동 재시도 여부는 T6에서 정합니다. 현재는 수동 재시도만 제공합니다.

### 예외
| 상황 | 응답 |
|------|------|
| 로그인하지 않음 | 401 `TOKEN_INVALID` (화면은 `/login`으로 이동) |
| `USER` 권한으로 접근 | 403 `FORBIDDEN` (화면은 403 페이지) |
| 대상 맛집·리뷰·회원 없음 | 404 `RESTAURANT_NOT_FOUND`, `REVIEW_NOT_FOUND`, `MEMBER_NOT_FOUND` |
| 맛집 수정 입력 오류 | 400 `VALIDATION_FAILED`, `CATEGORY_NOT_FOUND` |
| S3 삭제 재시도 개별 실패 | 200, 결과의 `failed`에 집계 (오류 아님) |

---

## 공통 오류 응답 형식

모든 REST API 오류는 아래 형식을 따릅니다 (상세는 [API_SPEC.md](API_SPEC.md#12-공통-응답-형식)).

```json
{
  "success": false,
  "error": { "code": "RESTAURANT_NOT_FOUND", "message": "맛집을 찾을 수 없습니다." },
  "timestamp": "2026-11-06T10:00:00"
}
```
