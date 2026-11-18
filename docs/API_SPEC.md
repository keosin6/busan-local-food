# API 명세 (API_SPEC)

REST API와 Thymeleaf 화면 URL을 정의합니다. 테이블 구조는 [DATABASE_ERD.md](DATABASE_ERD.md), 처리 규칙은 [FUNCTION_SPEC.md](FUNCTION_SPEC.md)를 따릅니다.

모든 API 항목은 다음 형식으로 표기합니다: 메서드·URL, 인증 필요 여부, 접근 가능 권한, 요청 데이터, 응답 데이터, 주요 오류 상황.

> **구현 상태:** 이 문서는 설계 명세입니다. 구현된 API가 아닙니다. 실제 구현 여부는 [PROGRESS.md](PROGRESS.md)에서 확인합니다.

## 1. 공통 규칙

- Base URL: `https://<도메인>` (로컬: `http://localhost:8080`)
- JSON API 경로 접두사: `/api`
- 화면 경로: 접두사 없음 (Thymeleaf 렌더링)
- 관리자 영역: 화면 `/admin/**`, API `/api/admin/**`
- 날짜·시간: ISO 8601, 시간대는 서버 기준 `Asia/Seoul` 로컬 시각 (`2026-11-06T10:00:00`)
- 인증 수단 (2026-11-10 확정): 화면은 Spring Security 세션(`JSESSIONID`), API(`/api/**`)는 JWT로 인증합니다. 두 수단은 서로 대체하지 않습니다 (DEVELOPMENT_RULES 5.7).
- API 인증: `access_token` 쿠키 또는 `Authorization: Bearer <accessToken>` 헤더
- OAuth2 임시 상태(`state`, Authorization Request)는 기존 HTTP 세션에 저장합니다. Google 로그인과 계정 연동 흐름에서만 사용하며, `/api/**` 요청 인증에는 사용하지 않습니다 (DEVELOPMENT_RULES 5.7.1).
- CSRF: 화면 세션 쿠키 기반 상태 변경 요청과 JWT 쿠키 기반 API 상태 변경 요청(POST, PUT, PATCH, DELETE)은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 함께 보냅니다. `Authorization` 스킴이 `Bearer`인 상태 변경 요청만 CSRF 헤더를 요구하지 않습니다. Basic 등 다른 스킴의 상태 변경 요청은 CSRF 검사를 받습니다.
- 권한 표기:
  - `누구나`: 인증 없이 접근 가능
  - `회원`: 로그인한 회원(`USER` 또는 `ADMIN`)
  - `작성자`: 해당 리소스를 만든 회원 본인
  - `ADMIN`: 관리자(`role = ADMIN`)만 접근 가능. 서버에서 요청마다 검증합니다 (FR-09-08).

### 1.1 쿠키 명세

| 쿠키 | 내용 | HttpOnly | SameSite | Secure | Path | 만료 |
|------|------|----------|----------|--------|------|------|
| `JSESSIONID` | 화면 로그인 세션 (Spring Security 세션) | 예 | Lax | 운영만 | `/` | 세션 만료 또는 로그아웃까지 (만료 값은 구현 단계 설정) |
| `access_token` | API 인증용 Access Token(JWT) | 예 | Lax | 운영만 | `/api` | 30분 |
| `refresh_token` | Refresh Token 원문 | 예 | Lax | 운영만 | `/api/auth` | 14일 |
| `XSRF-TOKEN` | CSRF 토큰 | 아니오 (JS가 헤더에 실어야 함) | Lax | 운영만 | `/` | 세션 |
| 별도 쿠키 없음 | OAuth2 임시 상태는 HTTP 세션(`JSESSIONID`)에 저장 | - | - | - | - | 세션과 같음 |

- `access_token`의 Path는 `/api`로 제한합니다. API 요청에서만 전송됩니다. 화면 요청은 `JSESSIONID`로 인증합니다.
- 구현 상태 (PR #19, `main` 병합 완료, merge commit `dd147e3f595825047d017ddcbb20df95f55ec40f`): API 체인(`/api/**`, `@Order(1)`, `STATELESS`)은 `Authorization: Bearer` 헤더 또는 `access_token` 쿠키의 JWT를 인증 근거로 씁니다. Bearer 헤더가 있으면 쿠키는 보지 않으며, 잘못된 Bearer 헤더는 쿠키로 대체하지 않고 401입니다. CSRF는 `Authorization: Bearer` 스킴의 상태 변경 요청에만 면제하며, 그 밖(쿠키 인증, Basic 등 다른 스킴, 헤더 없는 익명 요청)은 검증합니다. Bearer 면제는 토큰 값 검증과 별개이므로, 잘못된 Bearer는 CSRF 403이 아니라 인증 401입니다. 인증 실패는 401 `TOKEN_INVALID`입니다. 공개 경로는 2.1~2.4의 네 경로뿐입니다. API 응답에는 `XSRF-TOKEN` 쿠키가 실립니다. API 인증 HTTP(2.1~2.4, 2.8)는 PR #23(merge commit `56faff2566743c89911d4d87407fff5ac6c1732e`, 6.18)으로 `main`에 병합 완료되었습니다. 화면 로그인 리다이렉트, 화면 로그인·회원가입·로그아웃(2.9~2.11), Google OAuth2 체인은 미구현입니다. 화면 체인(`@Order(2)`)은 기존 세션 인증과 임시 허용 정책을 유지하며 JWT 필터를 넣지 않습니다.
- `refresh_token`의 Path는 `/api/auth`로 제한합니다. 재발급과 API 로그아웃 요청에서만 전송됩니다. 화면 로그아웃(`/logout`)에는 전송되지 않습니다. 화면 로그아웃은 먼저 `POST /api/auth/logout`을 호출합니다 (T12, 2.11).
- 쿠키를 만료시킬 때는 발급 당시와 같은 Path와 Domain을 사용합니다.
- 만료 시간은 환경 변수 `JWT_ACCESS_TOKEN_EXPIRY_SECONDS`, `JWT_REFRESH_TOKEN_EXPIRY_SECONDS`를 따릅니다. 위 값은 기본값입니다.
- 모든 인증 쿠키는 `Domain`을 설정하지 않습니다.
- JWT 원문(Access Token, Refresh Token)은 JSON 응답 본문에 포함하지 않습니다. 쿠키로만 전달합니다.
- 쿠키 속성(`HttpOnly`, `SameSite=Lax`, 운영에서 `Secure`)과 Path는 이 표를 따릅니다. 요청 제한 응답(429)은 쿠키를 바꾸지 않습니다 (1.4).

### 1.2 공통 응답 형식

성공:
```json
{
  "success": true,
  "data": { },
  "timestamp": "2026-11-06T10:00:00"
}
```

실패:
```json
{
  "success": false,
  "error": { "code": "VALIDATION_FAILED", "message": "입력값을 확인해 주세요.", "details": [ { "field": "rating", "reason": "1 이상이어야 합니다." } ] },
  "timestamp": "2026-11-06T10:00:00"
}
```

목록 응답의 `data` 구조:
```json
{
  "content": [ ],
  "page": 0,
  "size": 12,
  "totalElements": 40,
  "totalPages": 4
}
```

브라우저 리다이렉트 방식의 Google 흐름은 JSON이 아니라 쿼리 파라미터 `error`로 실패 사유를 전달합니다 (2.5, 2.6 참조).

### 1.3 공통 오류 코드

| HTTP | code | 의미 |
|------|------|------|
| 400 | VALIDATION_FAILED | 일반 입력값 검증 실패. 회원가입·로그인 입력 오류는 아래 세부 코드를 사용한다. 잘못된 JSON, 알 수 없는 필드, 필드 타입 오류도 이 코드를 쓴다. 지원하지 않는 Content-Type은 415다 |
| 400 | EMAIL_REQUIRED | 이메일 미입력 (회원가입·로그인) |
| 400 | EMAIL_INVALID | 이메일 형식 또는 길이(255자) 오류 (회원가입·로그인) |
| 400 | NICKNAME_REQUIRED | 닉네임 미입력 (회원가입) |
| 400 | NICKNAME_INVALID | 닉네임 길이(2~20자) 또는 허용 문자(한글·영문·숫자) 오류 (회원가입) |
| 400 | PASSWORD_REQUIRED | 비밀번호 미입력. 회원가입과 로컬 비밀번호 설정에서는 비밀번호 또는 비밀번호 확인값 미입력도 포함. 로그인에서는 비밀번호 미입력만 해당 |
| 400 | PASSWORD_INVALID | 회원가입·로컬 비밀번호 설정: 비밀번호 정책 위반 또는 확인값 불일치. 로그인: 사용하지 않음 (로그인은 비밀번호 조합 규칙을 검증하지 않음) |
| 400 | CATEGORY_NOT_FOUND | 존재하지 않는 카테고리 |
| 400 | IMAGE_TYPE_NOT_ALLOWED | 허용되지 않은 이미지 형식 |
| 400 | IMAGE_TOO_LARGE | 이미지 크기 초과 |
| 400 | IMAGE_LIMIT_EXCEEDED | 맛집당 이미지 개수 초과 |
| 401 | INVALID_CREDENTIALS | 이메일 또는 비밀번호 불일치 (재인증 실패 포함) |
| 401 | TOKEN_INVALID | 토큰 없음, 만료, 폐기 또는 위조 |
| 429 | RATE_LIMITED | 요청 제한 초과. `Retry-After` 헤더에 재시도까지 남은 초를 담는다 (1.4) |
| 403 | FORBIDDEN | 권한 없음 (작성자 아님, 또는 ADMIN 아님) |
| 404 | MEMBER_NOT_FOUND | 회원 없음 |
| 404 | RESTAURANT_NOT_FOUND | 맛집 없음 |
| 404 | REVIEW_NOT_FOUND | 리뷰 없음 |
| 404 | FAVORITE_NOT_FOUND | 찜 없음 |
| 404 | GOOGLE_NOT_LINKED | 연동된 Google 계정 없음 (화면 해제 흐름에서는 `/mypage?error=google_not_linked`로 안내, 2.7) |
| 409 | EMAIL_DUPLICATED | 이메일 중복 |
| 409 | NICKNAME_DUPLICATED | 닉네임 중복 (`uk_member_nickname` 위반 포함) |
| 409 | REVIEW_ALREADY_EXISTS | 같은 맛집에 리뷰 중복 |
| 409 | FAVORITE_ALREADY_EXISTS | 찜 중복 |
| 409 | GOOGLE_ALREADY_LINKED | 이미 다른 회원에게 연결된 Google 계정 (FR-01-11) |
| 409 | LAST_LOGIN_METHOD | 남은 로그인 수단이 없어 Google 연동을 해제할 수 없음 (FR-01-13). 화면 해제 흐름에서는 `/mypage?error=password_required`로 안내 (2.7) |
| 502 | STORAGE_ERROR | S3 오류 (업로드 등 S3 호출이 필요한 요청 실패) |
| 502 | AI_UNAVAILABLE | OpenRouter 호출 실패 또는 타임아웃 |
| 502 | AI_RESPONSE_INVALID | AI 응답 파싱 실패 |
| 500 | INTERNAL_ERROR | 서버 내부 오류 |

> S3 객체 삭제 실패는 오류 코드가 아닙니다. DB 삭제는 완료된 상태이므로 `200`과 함께 결과 필드(`storageDeleted`, `storageDeleteFailedCount`)로 알립니다 (FUNCTION_SPEC F1-02, F1-03).

---

### 1.4 요청 제한 (Rate Limit)

> **구현 상태 (2026-11-17, `main` 기준):** 서비스 계층과 HTTP 계층을 구분합니다.
>
> 서비스 계층 (`main` 병합 완료, PR #16):
> - 요청 제한 서비스 R1~R10: `member.ratelimit.AuthRateLimitService`. 로그인 두 제한과 자격 증명 확인, 회원가입, Google OAuth2, Refresh 계열 판정과 이벤트 기록.
> - Refresh 판정 연결: `RefreshTokenRateLimitGate`가 `RefreshRotationGate`를 구현합니다. 재사용 탐지 → 요청 제한 → 만료 → 회전 순서를 지킵니다. 제한 결과는 재시도 초(`RateLimited`)를 담습니다.
> - Refresh 잠금 재시도: 잠금 대기와 교착 상태는 `LockRetryTemplate`이 최대 3번 새 트랜잭션에서 다시 실행합니다. 한도를 넘으면 요청을 허용하지 않습니다(fail-closed).
> - 이벤트 정리: 생성 후 24시간 보존. 요청 시 정리와 주기 정리.
>
> HTTP 계층 (`main` 병합 완료: 공통 429와 회원가입 집계는 PR #17, 두 체인 연결은 PR #19, 로그인·재발급 HTTP 연결은 PR #23):
> - 공통 429: `RateLimitExceededException`을 `GlobalExceptionHandler`가 `429 RATE_LIMITED`로 바꿉니다. `Retry-After`에는 재시도까지 남은 초가 들어갑니다. 쿠키와 세션은 건드리지 않습니다. 본문에는 제한 기준, IP, 이메일, family_id가 없습니다.
> - 회원가입 집계: `SignupRateLimitFilter`가 `POST /signup`, `POST /api/auth/signup`을 집계합니다. 두 SecurityFilterChain(API 체인, 화면 체인) 모두에서 CSRF 검증 뒤, 본문 읽기와 파싱 이전에 실행됩니다. 집계 실패는 500 `INTERNAL_ERROR`로 fail-closed 합니다. 화면과 API 회원가입은 같은 IP 카운터를 씁니다.
>
> 로그인·재발급 HTTP 연결은 PR #23(merge commit `56faff2`, 6.18)으로 `main`에 병합 완료되었습니다 (2.2, 2.3).
>
> HTTP 계층 (남은 항목):
> - Google OAuth2 시작·연동·콜백 집계 연결: OAuth2 로그인·연동 흐름과 클라이언트 설정이 아직 없습니다. 흐름이 생기면 OAuth2 임시 상태 보존 규칙(시작 제한 시 임시 상태 미생성, 콜백 제한 시 해당 임시 상태만 제거, 화면 세션과 JWT 유지)을 지켜 연결합니다.
> - Google 신규 가입 닉네임 제출 집계: 제출 경로가 아직 확정되지 않았습니다.
> - 프런트엔드 T20 처리.
>
> **CSRF와 집계 순서 (2026-11-12 조사, 확정):** CSRF 필터는 DispatcherServlet과 사용자 정의 요청 제한 필터보다 먼저 실행됩니다. CSRF 검증에 실패한 요청(토큰 없음, 토큰 불일치)은 집계하지 않고 403으로 거부합니다. 회원가입 집계는 CSRF 검증 이후, JSON 본문 읽기 및 파싱 이전에 수행합니다. 영향 범위: 토큰 없는 대량 요청은 집계 없이 거부되어 DB 부하가 없습니다. 다만 `XSRF-TOKEN` 쿠키는 GET으로 누구나 받을 수 있으므로, 토큰을 받은 요청은 정상적으로 집계됩니다. CSRF 정책과 요청 제한 정책은 변경하지 않습니다.
>
> 모든 제한은 Rolling Window로 계산합니다. 즉 고정된 시간대가 아니라 "최근 N분(또는 N시간)"을 기준으로 셉니다. 초과한 요청은 HTTP `429`, 오류 코드 `RATE_LIMITED`로 응답하고 `Retry-After` 헤더에 재시도까지 남은 초를 담습니다. 제한 기준이나 내부 키 정보는 응답에 노출하지 않습니다.

| 구분 | 제한 기준 | 제한 초과 시점 | 적용 대상 |
|------|-----------|----------------|-----------|
| 로그인 이메일+IP | 최근 15분 실패 5회 | 6번째 요청부터 429 | 화면 `POST /login`, API `POST /api/auth/login` |
| 로그인 IP 보조 | 최근 15분 실패 20회 | 21번째 요청부터 429 | 같은 로그인 요청 |
| 회원가입 | IP별 최근 1시간 5회 | 6번째 요청부터 429 | 화면 `POST /signup`, API `POST /api/auth/signup`, Google 신규 가입 닉네임 제출 (2.5.1) |
| Refresh | Refresh Token 계열별 최근 1분 10회 | 11번째 요청부터 429 | `POST /api/auth/refresh` |
| Google OAuth2 | IP별 최근 15분 10회 | 11번째 요청부터 429 | `GET /oauth2/authorization/google` (로그인 시작), `POST <연동 시작 화면 경로>` (연동 시작, 2.6), `GET /login/oauth2/code/google` (콜백, 로그인·연동 공통, 2.6.1) |

규칙:
- 로그인: 키는 정규화한 이메일과 클라이언트 IP의 조합입니다. 실패 5회까지는 `INVALID_CREDENTIALS`를 반환합니다. 6번째 요청부터는 비밀번호를 검증하지 않고 `429`를 반환합니다. IP 보조 제한은 실패 20회까지 `INVALID_CREDENTIALS`를 반환하고 21번째부터 `429`를 반환합니다. 두 제한을 함께 적용합니다.
- 로그인 성공 시 해당 이메일+IP 조합의 실패 기록을 즉시 초기화합니다.
- 회원가입 (T15 확정): 집계는 JSON 파싱과 DTO 검증 이전에 합니다. 잘못된 입력, JSON 파싱 실패, DTO 검증 실패, 지원하지 않는 Content-Type 요청도 모두 회원가입 요청으로 집계합니다. 요청 제한을 먼저 확인하고, 초과한 요청은 `429 RATE_LIMITED`로 응답하며 이벤트를 추가하지 않습니다. 정상 요청이 입력 검증에 실패하면 기존 검증 오류 응답을 유지합니다. 화면, API, Google 신규 가입 닉네임 제출은 같은 IP 카운터를 공유합니다.
  - 처리 순서 (2026-11-12 조사, 확정): 요청 제한은 필터 또는 이에 준하는 처리 계층에서 집계합니다. (1) CSRF 필터가 먼저 실행되며, CSRF 검증에 실패한 요청은 집계되지 않고 403으로 거부됩니다. (2) 회원가입 집계는 CSRF 검증 이후, JSON 본문 읽기와 파싱 이전에 수행합니다. (3) 집계 이후에 DTO 검증을 수행합니다. CSRF 정책과 요청 제한 정책은 변경하지 않습니다.
- Refresh (T17 확정): 해시로 DB를 조회합니다. 유효한 토큰의 계열을 확인할 수 있는 요청만 `family_id` 기준으로 제한합니다. 계열을 확인할 수 없는 요청(행 없음, 위조 등)에는 별도 IP 기반 제한을 추가하지 않습니다. 잘못된 토큰의 응답은 기존 인증 실패 정책(401)을 유지합니다. 처리 순서는 2.3을 따릅니다. 재사용이 확인되면 T7을 먼저 수행하여 해당 회원의 모든 활성 토큰을 폐기하고 커밋한 뒤 401을 반환합니다. Rate Limit이 이 재사용 탐지를 가리거나 우회하지 않습니다.
- Google OAuth2 (R1, R5, T18): 집계 단위는 사용자 시작 요청 1회 또는 콜백 요청 1회입니다.
  - 로그인 시작: `GET /oauth2/authorization/google` 1회.
  - 계정 연동 시작: `POST <연동 시작 화면 경로>` 1회 (2.6).
  - 콜백: `GET /login/oauth2/code/google` 1회. 로그인 콜백과 계정 연동 콜백은 같은 엔드포인트이므로 같은 방식으로 1회 집계합니다.
  - 세 종류 모두 같은 IP 카운터(최근 15분 10회)를 공유합니다. 각 요청은 개별 이벤트로 저장하며 IP 식별자는 HMAC-SHA256 값입니다.
  - 요청 경계: 연동 시작 POST가 응답으로 보내는 Google 인증 페이지 `302`와 브라우저의 그 리다이렉트는 별도 요청으로 세지 않습니다. 서버 내부 전달이나 리다이렉트로 OAuth2 authorization endpoint가 다시 호출되더라도 같은 사용자 시작 요청의 일부로 보고 추가로 세지 않습니다.
  - 제한을 초과하면 시작 요청(POST와 GET)은 HTTP 429와 `Retry-After`를 반환하고 OAuth2 임시 상태를 만들지 않습니다. 콜백은 HTTP 429와 `Retry-After`를 반환하고 OAuth2 임시 상태만 정리합니다. 기존 화면 세션과 API JWT는 어느 경우에도 유지합니다. 초과 요청은 이벤트를 만들지 않습니다.
  - `state` 검증 등 기존 절차는 그대로 둡니다.
- 제한된 요청은 새 제한 이벤트를 만들지 않습니다.
- 429 응답의 화면과 클라이언트 처리 (T20 확정):
  - 429 응답에서는 `access_token`, `refresh_token` 쿠키를 만료시키거나 새로 설정하지 않습니다. 화면 세션과 JWT는 유지합니다.
  - `Retry-After` 값을 초 단위로 전달하며, 클라이언트는 그 값에 따라서만 재시도합니다. 무한 재시도하지 않습니다.
  - Refresh API가 429를 반환해도 자동 로그아웃이나 재로그인을 수행하지 않습니다.
  - 응답별 처리: 401 `TOKEN_INVALID`는 기존 인증 실패와 재로그인 정책을 따릅니다. 403 CSRF 실패는 CSRF 오류 처리를 따릅니다. 429 `RATE_LIMITED`는 인증 상태와 JWT 쿠키를 유지하고 재시도 안내를 합니다. 200은 기존 처리를 유지합니다. Refresh 재사용 탐지로 전체 토큰이 폐기된 경우에는 T7에 따라 401을 반환하고 JWT 쿠키를 만료시킵니다.
  - 화면 경로도 같은 상태 코드(`429`)와 `Retry-After`를 사용합니다. 화면 표시 문구는 구현 단계에서 작성합니다.
- 이 표에 없는 엔드포인트(예: 로그아웃)는 이번 정책의 제한 대상이 아닙니다.

개인정보와 저장 (R2, T16 확정):
- 이벤트 테이블에는 IP 기반 식별자를 HMAC-SHA256 값으로만 저장합니다. 적용 범위는 로그인 이메일+IP 조합, 로그인 IP 보조 제한, 회원가입 IP 제한, Google OAuth2 IP 제한입니다. 원문 이메일과 원문 IP는 이벤트 테이블에 저장하지 않습니다.
- HMAC 비밀키는 EC2 환경 변수로 주입하며 Git에 커밋하지 않습니다. 키 교체 절차는 DEVELOPMENT_RULES 5.8을 따릅니다.
- 클라이언트 IP 판별:
  1. 설정된 신뢰 프록시에서 전달한 `X-Forwarded-For` 값만 신뢰합니다. 신뢰 프록시 목록은 구현 단계에서 설정 값으로 지정합니다.
  2. 신뢰 프록시가 아닌 요청의 `X-Forwarded-For`는 신뢰하지 않으며 Remote Address를 사용합니다.
  3. 판별한 IP는 HMAC-SHA256으로 변환하여 저장과 조회 키로만 사용합니다. 일반 애플리케이션 로그에 원본 IP를 불필요하게 남기지 않습니다.

보존과 동시성 (R3, T18 확정, 설계안):
- 로그인, 회원가입, Refresh, Google OAuth2 이벤트는 모두 생성 시점으로부터 24시간 후 삭제합니다.
- 조회 범위를 벗어났다는 이유만으로 즉시 삭제하지 않습니다.
- 만료 이벤트 정리는 요청 시 정리와 Spring Scheduling 기반 주기적 정리를 병행합니다.
- 판단은 MySQL 행 잠금으로 같은 키의 요청을 직렬화하여 수행합니다. 단순 `COUNT` 후 `INSERT` 방식은 쓰지 않습니다. 자세한 구조는 DATABASE_ERD 2.10을 따릅니다.


### 1.5 Access Token(JWT) 규격 (Stage 2-3 구현 기준)

- 서명 알고리즘은 HS256으로 고정합니다. HS384, HS512, RS256, `none` 등 다른 알고리즘의 토큰은 모두 거부합니다.
- 발급자 `iss`는 `busan-local-food`이고, 대상 `aud`는 `busan-local-food-api`입니다.
- 클레임은 다음 여섯 개만 포함합니다.

| 클레임 | 값 |
|--------|----|
| `sub` | 회원 ID 문자열 (양의 정수, 선행 0 없음) |
| `iss` | `busan-local-food` |
| `aud` | `busan-local-food-api` |
| `iat` | 발급 시각 (초 단위) |
| `exp` | 만료 시각 (초 단위) |
| `role` | `USER` 또는 `ADMIN`. 발급 시점의 권한 |

- 이메일, 닉네임, 비밀번호, Refresh Token 원문과 해시, `family_id`는 토큰에 넣지 않습니다.
- 만료 시간은 기본 1800초(30분)이며 환경 변수 `JWT_ACCESS_TOKEN_EXPIRY_SECONDS`로 바꿉니다.
- 시계 오차는 60초로 고정합니다. 만료 검증과 미래 발급 시각 검증에 적용하며, 60초를 넘는 오차는 허용하지 않습니다.
- 서명 키는 환경 변수 `JWT_SECRET`에서 읽습니다. 값은 Base64로 인코딩한 것이며, 디코딩 후 32바이트 이상이어야 합니다. 값이 없거나 Base64 형식이 아니거나 길이가 부족하면 애플리케이션 기동이 실패합니다.
- 키를 교체하면 기존 Access Token은 무효화될 수 있습니다.
- 검증 실패 기준은 다음과 같습니다. 서명 위조와 변조, 알고리즘 불일치, 발급자 불일치, 대상 불일치, 만료(60초 초과), 미래 발급 시각(60초 초과), 필수 클레임 누락, `sub` 형식 오류, `role` 값 오류는 모두 검증 실패입니다.
- 검증 실패의 구체적인 원인은 외부 응답에 노출하지 않으며, 공통 인증 실패(401 `TOKEN_INVALID`)로 매핑합니다. 이 매핑은 인증 필터 단계에서 구현합니다.
- Access Token은 `access_token` 쿠키로만 전달합니다. JSON 응답 본문에는 포함하지 않습니다 (1.1절).
- 구현 상태 (2026-11-12, PR #19 병합 후 갱신): Access Token 발급·검증과 Refresh Token 발급·회전·재사용 탐지·로그아웃 폐기는 서비스 계층까지 구현되었습니다. API 인증 필터 연결과 API·화면 SecurityFilterChain 분리는 PR #19로 `main`에 병합 완료되었습니다. 2절의 API 인증 HTTP(로그인·재발급·로그아웃·내 정보)는 PR #23(merge commit `56faff2`)으로 `main`에 병합 완료되었습니다. 요청 제한은 서비스 계층(1.4), 회원가입 집계, 로그인·재발급 HTTP 연결까지 구현되었습니다. 화면 인증과 Google 요청 제한 연결은 미구현입니다.

## 2. 인증 API (F1-01)

2.1~2.8은 API 엔드포인트(JWT 인증)입니다. 2.9~2.11은 화면 폼 엔드포인트(Spring Security 세션 인증)입니다. 화면 로그인, 화면 회원가입, Google 로그인은 세션과 JWT를 함께 발급합니다. API 로그인과 API 회원가입은 JWT만 발급합니다 (FR-01-17, FR-01-18).

### 2.1 회원가입 (API)

- 구현 상태: `main` 병합 완료 (PR #23, merge commit `56faff2`, 6.18).

- 메서드·URL: `POST /api/auth/signup`
- 인증: 불필요
- 권한: 누구나
- 요청:
```json
{
  "email": "user@example.com",
  "password": "<8~20자, 영문·숫자·특수문자 각 1자 이상, 공백 불가>",
  "passwordConfirm": "<동일 값>",
  "nickname": "부산먹보"
}
```
- 응답 `201`: 가입 후 자동 로그인합니다. `access_token`, `refresh_token` 쿠키(JWT)만 설정합니다. Spring Security 세션은 만들지 않습니다. 화면 회원가입은 2.9를 사용합니다.
```json
{
  "success": true,
  "data": { "memberId": 1, "email": "user@example.com", "nickname": "부산먹보", "role": "USER" },
  "timestamp": "..."
}
```
- 주요 오류: 400 EMAIL_REQUIRED, 400 EMAIL_INVALID, 400 NICKNAME_REQUIRED, 400 NICKNAME_INVALID, 400 PASSWORD_REQUIRED, 400 PASSWORD_INVALID, 409 EMAIL_DUPLICATED, 409 NICKNAME_DUPLICATED, 429 RATE_LIMITED (1.4, 입력값 검증 실패도 계산). 세부 검증 규칙은 FUNCTION_SPEC F1-01 "회원가입·로그인 검증 규칙"을 따른다.

### 2.2 로그인 (API)

- 구현 상태: `main` 병합 완료 (PR #23, merge commit `56faff2`, 6.18).

- 메서드·URL: `POST /api/auth/login`
- 인증: 불필요
- 권한: 누구나
- 요청:
```json
{ "email": "user@example.com", "password": "<비밀번호>" }
```
- 응답 `200`: `access_token` 쿠키와 `refresh_token` 쿠키를 설정합니다 (1.1 참조). 본문:
```json
{
  "success": true,
  "data": { "memberId": 1, "nickname": "부산먹보", "role": "USER", "accessTokenExpiresIn": 1800 },
  "timestamp": "..."
}
```
- 처리: Refresh Token 원문은 쿠키로만 내려주고, DB `refresh_token`에는 SHA-256 해시를 저장합니다 (FR-01-05). Spring Security 세션은 만들지 않습니다. 화면 로그인은 2.10을 사용합니다.
- 주요 오류: 400 EMAIL_REQUIRED, 400 EMAIL_INVALID, 400 PASSWORD_REQUIRED (입력 누락·형식 오류에만 사용), 401 INVALID_CREDENTIALS, 429 RATE_LIMITED (1.4: 정규화 이메일+IP 실패 5회 뒤 6번째부터, IP 보조는 실패 20회 뒤 21번째부터). 미가입 이메일, 비밀번호 불일치, Google 전용 계정은 모두 같은 401 INVALID_CREDENTIALS로 응답한다.

### 2.3 Access Token 재발급 (Refresh Token 로테이션)

- 구현 상태: `main` 병합 완료 (PR #23, merge commit `56faff2`, 6.18).

- 메서드·URL: `POST /api/auth/refresh`
- 인증: `refresh_token` 쿠키 필요 (Access Token은 불필요)
- 권한: 유효한 Refresh Token을 가진 회원
- 요청: 본문 없음. `refresh_token` 쿠키를 전송합니다.
- 계열: 모든 Refresh Token 행은 서버가 만든 UUID `family_id`를 가집니다. 회전할 때 같은 값을 유지하고, 새 기기 로그인에서는 새 값을 만듭니다 (FR-01-36, DATABASE_ERD 2.8). `family_id`는 가명 식별자이므로 응답 본문에 포함하지 않으며, 외부 노출을 막습니다 (DEVELOPMENT_RULES 5.3).
- 처리 (FR-01-06, FR-01-24, FR-01-30, T7 A안, T14 A안):
  1. 쿠키 원문의 SHA-256 해시로 `refresh_token` 행을 잠금 없이 조회하여 회원 ID와 `family_id`를 확인합니다. 행이 없으면 `401 TOKEN_INVALID`를 반환합니다. 어떤 행도 변경하지 않으며 제한 이벤트도 만들지 않습니다.
  2. 트랜잭션을 시작하고 회원 행을 잠금 조회합니다.
  3. 같은 트랜잭션에서 `refresh_token` 행을 다시 조회하여 아래 순서로 판정합니다.
     - 폐기된 행(`revoked_at`이 있음)이면 재사용입니다. 요청 제한 판정보다 먼저 처리합니다. 같은 회원의 `revoked_at`이 없는 모든 행에 `revoked_at`을 기록하고 커밋한 뒤 `401 TOKEN_INVALID`를 반환합니다. 429 때문에 이 처리가 생략되지 않습니다.
     - 폐기되지 않은 행이면 계열별 요청 제한(1.4)을 확인합니다. 같은 `family_id`의 최근 1분 요청이 10회 이미 있으면 `429 RATE_LIMITED`를 반환합니다. 이때 토큰과 이벤트를 변경하지 않습니다.
     - 제한을 통과한 요청은 `refresh_request_event`에 한 건을 기록합니다. 그다음 만료 여부를 확인합니다. 만료되었으면 `401 TOKEN_INVALID`를 반환하고 다른 행은 변경하지 않습니다.
     - 활성이고 만료되지 않았으면 정상 갱신입니다. 기존 행의 `revoked_at`을 기록하고, 같은 `family_id`로 새 행을 INSERT한 뒤 커밋합니다.
  4. 정상 갱신일 때만 새 `access_token`, `refresh_token` 쿠키를 설정합니다.
- 응답별 처리:

| 상황 | 응답 | 쿠키 처리 |
|------|------|-----------|
| 정상 갱신 | 200 | 새 `access_token`, `refresh_token` 발급 |
| 요청 제한 초과 | 429 `RATE_LIMITED` (`Retry-After`) | 기존 쿠키 유지 |
| 유효하지 않은 토큰 (행 없음, 위조) | 401 `TOKEN_INVALID` | 두 JWT 쿠키 만료 |
| 만료된 토큰 | 401 `TOKEN_INVALID` | 두 JWT 쿠키 만료 |
| 폐기된 토큰 재사용 | 401 `TOKEN_INVALID` (회원의 활성 토큰은 T7에 따라 커밋 후 모두 폐기) | 두 JWT 쿠키 만료 |

- 응답 `200`:
```json
{
  "success": true,
  "data": { "accessTokenExpiresIn": 1800 },
  "timestamp": "..."
}
```
- 재사용 탐지 응답 본문에는 재사용 여부나 토큰 정보를 넣지 않습니다.
- 응답 유실 시: 응답이 네트워크에서 유실되면 클라이언트는 새 쿠키를 받지 못합니다. 이후 이전 토큰을 제출하면 재사용으로 판정되어 재로그인이 필요할 수 있습니다. 이는 T14 A안이 수용한 한계입니다.
- 응답 보안: Refresh Token 원문과 해시는 응답 본문에 노출하지 않습니다. 쿠키 속성은 1.1절을 따르며 이 절에서 바꾸지 않습니다.
- 화면 세션(`JSESSIONID`)은 이 API로 재발급되지 않습니다.
- 화면의 자동 재발급과 재시도 규칙은 DEVELOPMENT_RULES 5.7.2 (T13 A안)를 따릅니다. 재발급이 429를 받으면 인증 상태와 쿠키를 유지하고 자동 로그아웃이나 재로그인을 하지 않습니다 (1.4, T20 확정).
- 동시 갱신 (T14 A안): 같은 브라우저의 여러 탭은 Web Locks API로 갱신을 직렬화하고, 잠금 안에서 `GET /api/auth/me`로 인증 상태를 확인한 뒤 필요할 때만 이 API를 호출합니다 (DEVELOPMENT_RULES 5.7.2). 서버는 회원 행 잠금으로 정상 갱신과 재사용 탐지를 같은 순서로 처리합니다 (DEVELOPMENT_RULES 5.7.4).
- 재사용 탐지는 DEVELOPMENT_RULES 5.7.4 (T7 A안)를 따릅니다. 폐기된 토큰이 재사용되면 해당 회원의 모든 활성 Refresh Token을 폐기합니다.
- 계열을 확인할 수 없는 요청에는 별도 IP 기반 제한을 추가하지 않으며, 기존 인증 실패 처리(401)를 따릅니다 (T17 확정, 1.4).
- 주요 오류: 401 TOKEN_INVALID (쿠키 없음, 만료, 폐기, 위조), 429 RATE_LIMITED

### 2.4 로그아웃 (API)

- 구현 상태: `main` 병합 완료 (PR #23, merge commit `56faff2`, 6.18).

- 메서드·URL: `POST /api/auth/logout`
- 인증: 불필요 (Access Token이 만료되었어도 로그아웃할 수 있어야 하므로 요구하지 않습니다)
- 권한: 누구나
- 요청: 본문 없음. `refresh_token` 쿠키가 있으면 해당 토큰을 폐기합니다.
- 처리: 회원 행을 먼저 잠근 뒤 해당 `refresh_token` 행의 `revoked_at`을 기록하고, `access_token`과 `refresh_token` 쿠키를 만료시킵니다 (FR-01-07). 잠금 순서는 재발급과 같습니다 (DEVELOPMENT_RULES 5.7.4).
- 응답 `200`:
```json
{ "success": true, "data": null, "timestamp": "..." }
```
- 쿠키가 없거나 이미 폐기된 토큰이어도 `200`을 반환합니다.
- 요청 제한 대상이 아닙니다 (1.4 표에 없음).
- 화면 세션을 종료하지 않습니다. 화면 로그아웃은 2.11을 사용합니다.

### 2.5 Google 로그인 (시작 및 콜백)

- 메서드·URL: `GET /oauth2/authorization/google` (시작), `GET /login/oauth2/code/google` (콜백)
- 인증: 불필요
- 권한: 누구나
- 요청: 브라우저 리다이렉트. Google 인증 페이지로 이동합니다.
- 콜백 처리 (FR-01-09, Q1):
  1. OAuth2 `state`를 검증합니다. 세션의 연동 진행 상태가 있으면 2.6.1 계정 연동 콜백 절차로, 없으면 아래 일반 로그인 절차로 처리합니다. `state`가 일치하지 않으면 로그인하지 않고 `/login?error=google_failed`로 이동합니다.
  2. Google 프로필의 `sub`, `email`, `email_verified`를 확인합니다.
  3. `google_sub`가 같은 회원이 있으면 로그인합니다. Spring Security 화면 세션을 생성하고 JWT 쿠키(`access_token`, `refresh_token`)를 설정한 뒤 `/`로 리다이렉트합니다.
  4. 없고 같은 `email`의 기존 회원이 있으면 **자동 연결하지 않습니다.** `/login?error=email_account_exists`로 리다이렉트합니다. 화면에는 "기존 계정으로 로그인한 뒤 마이페이지에서 Google 계정을 연동해 주세요." 를 표시합니다.
  5. `email_verified`가 아니면 `/login?error=google_unverified`로 리다이렉트합니다.
  6. 둘 다 없으면 회원을 만들지 않습니다. 신규 가입 진행 상태(Google `sub`, `email`, 흐름 = 신규 가입)를 OAuth2 임시 세션에 저장하고, 로그인 상태 없이 닉네임 설정 화면(`GET <닉네임 설정 화면 경로>`, 경로는 구현 단계에서 확정)으로 리다이렉트합니다. 화면 세션과 JWT는 발급하지 않습니다 (T3 A안, 2.5.1).
- 실패(Google 인증 오류 등): `/login?error=google_failed`로 리다이렉트합니다.
- 콜백 처리는 사용자 정의 `AuthenticationSuccessHandler`(성공)와 `AuthenticationFailureHandler`(실패)가 담당합니다. 세션의 연동 진행 상태가 없으면 위 일반 로그인 처리를 유지합니다 (DEVELOPMENT_RULES 5.7.1 Callback 핸들러 원칙).
- 발급: 성공하면 화면 세션과 JWT를 함께 발급합니다 (FR-01-17).
- 주요 오류(리다이렉트 쿼리): `email_account_exists`, `google_unverified`, `google_failed`
- 요청 제한 (1.4, R5, T18): 로그인 시작(GET), 연동 시작(POST, 2.6), 콜백(GET)은 Google OAuth2 IP 카운터(15분 10회)를 공유합니다. 초과하면 HTTP 429와 `Retry-After`를 반환하고 OAuth2 임시 상태만 정리합니다. 기존 세션과 JWT는 유지합니다. 요청 경계는 1.4를 따릅니다.

### 2.5.1 Google 신규 가입 닉네임 설정 (화면, T3 A안)

- 메서드·URL: `GET <닉네임 설정 화면 경로>` (화면 표시), `POST <닉네임 설정 화면 경로>` (제출). 경로는 구현 단계에서 확정합니다. 화면 경로이며 `/api/**`가 아닙니다.
- 인증: 불필요. 대신 OAuth2 임시 세션에 신규 가입 진행 상태가 있어야 합니다. 없으면 `/login`으로 이동합니다.
- 권한: Google 신규 가입을 진행 중인 사용자
- CSRF: 제출(POST)에 필요
- 요청: 폼 필드 `nickname` (닉네임 규칙은 FUNCTION_SPEC F1-01 참조)
- 처리:
  1. 가입 진행 상태를 확인합니다. 없으면 `/login`으로 이동합니다.
  2. 닉네임 규칙(길이, 허용 문자, 앞뒤 공백 제거)을 검증합니다. 실패하면 `<닉네임 설정 화면 경로>?error=validation_failed`로 이동합니다.
  3. 닉네임 중복을 검사합니다. 중복이면 `<닉네임 설정 화면 경로>?error=nickname_duplicated`로 이동합니다.
  4. 회원을 생성합니다 (`role = USER`, `password_hash = NULL`, `google_sub = sub`).
  5. 화면 세션과 JWT 쿠키(`access_token`, `refresh_token`)를 발급하고, 가입 진행 상태를 제거합니다 (FR-01-17).
  6. `302` → `/`로 리다이렉트합니다.
- 닉네임 설정이 끝나기 전에는 회원을 만들지 않습니다. 흐름이 중단되면 가입 진행 상태만 제거하고, 회원은 만들지 않습니다.
- 주요 오류(리다이렉트 쿼리): `validation_failed`, `nickname_duplicated`.
- 요청 제한 (1.4): 닉네임 제출은 회원가입 요청으로 계산합니다 (IP별 1시간 5회). 초과하면 HTTP 429와 `Retry-After`를 반환하고 회원을 만들지 않습니다. 가입 진행 상태의 처리는 구현 단계에서 확인합니다.
- 참고: `nickname`에는 `uk_member_nickname` UNIQUE 제약이 있습니다. 사전 검사 후 저장 시 위반이 나면 같은 `nickname_duplicated`로 처리합니다 (ERD 2.1, DEVELOPMENT_RULES 5.7.3, 6.4).

### 2.6 Google 계정 연동 시작 (화면, 세션 기반)

- 메서드·URL: `POST <연동 시작 화면 경로>`. 경로는 구현 단계에서 확정합니다. 화면 경로이며 `/api/**`가 아닙니다.
- 인증: 화면 세션 필요 (로그인된 회원). 세션이 없으면 `/login`으로 이동합니다.
- 권한: 로그인된 회원 본인. 세션의 회원만 연동을 시작할 수 있습니다.
- CSRF: 필요. 상태를 바꾸는 POST이므로 CSRF 토큰을 포함합니다.
- 요청: 본문 없음 (CSRF 토큰만).
- 처리 (FR-01-10, FR-01-12, FR-01-15, T11 A안):
  1. 세션에서 로그인 회원 ID를 확인합니다. 없으면 연동을 거부합니다.
  2. 세션에 연동 진행 상태(연동 대상 회원 ID, 흐름 종류 = 계정 연동)를 저장합니다. 남아 있던 일반 로그인 진행 상태는 제거합니다.
  3. Spring Security의 기본 OAuth2 Authorization Request 저장 방식(`HttpSessionOAuth2AuthorizationRequestRepository`)으로 `state`와 Authorization Request를 HTTP 세션에 저장합니다.
  4. Google 인증 페이지로 `302` 리다이렉트합니다.
- 응답: `302` → Google 인증 페이지.
- 기존 로그인 세션과 API JWT는 변경하지 않습니다. 재발급하거나 교체하지 않습니다.
- 주요 오류: 세션 없음 → `/login`으로 리다이렉트.
- 요청 제한 (1.4, R5): 연동 시작 POST는 Google OAuth2 IP 카운터(15분 10회)에 포함합니다. 이 POST는 1회로 집계하며, 이어지는 Google 인증 페이지 `302`는 별도 요청으로 세지 않습니다. 초과하면 HTTP 429와 `Retry-After`를 반환하고 OAuth2 임시 상태를 만들지 않으며, 기존 세션과 JWT는 유지합니다.

### 2.6.1 Google 계정 연동 콜백 (콜백 URL `/login/oauth2/code/google`)

Redirect URI는 하나를 사용합니다. 처리 경로는 세션의 연동 진행 상태로 나눕니다 (T11 A안, DEVELOPMENT_RULES 5.7.1).

1. OAuth2 `state`를 검증합니다. Spring Security의 기존 절차를 유지합니다. 실패하면 연동 임시 상태만 정리하고, 기존 로그인 상태와 JWT는 유지한 채 `/mypage?error=google_failed`로 이동합니다 (11번 원칙).
2. 세션의 연동 진행 상태를 확인합니다. 없으면 계정 연동이 아니므로 2.5의 일반 로그인 처리로 분기합니다.
3. 세션에 저장된 연동 대상 회원 ID를 확인합니다. 없거나 회원이 존재하지 않으면 거부합니다 (`google_failed`).
4. Google 인증 코드를 교환하고 Google 사용자 정보를 조회합니다.
5. Google `sub` 식별자를 확인합니다. `email_verified`가 아니면 `/mypage?error=google_unverified`로 이동합니다.
6. 해당 `google_sub`가 다른 회원에게 이미 연결되어 있으면 거부합니다 (`/mypage?error=google_already_linked`, 409 `GOOGLE_ALREADY_LINKED`에 해당).
7. 중복되지 않으면 연동 대상 회원의 `google_sub`를 저장합니다. `uk_member_google_sub` 제약이 중복 연결을 한 번 더 막습니다.
8. 연동 완료로 처리하고 `/mypage?google=linked`로 이동합니다. 이미 대상 회원에게 연결되어 있으면 변경 없이 같은 결과로 이동합니다.
9. 연동 진행 상태와 `state`를 세션에서 제거합니다. 성공과 실패 모두 수행합니다.
10. Callback 핸들러 원칙(DEVELOPMENT_RULES 5.7.1)을 따릅니다. 연동 모드의 성공 처리는 기존 로컬 회원의 인증 상태와 세션을 보존하며, 세션 ID를 바꾸지 않고, 쿠키를 다시 설정하지 않습니다. Google 인증 결과는 `member.google_sub` 연결에만 사용합니다.
11. 연동 모드의 실패(Google 인증 실패, `state` 검증 실패, 업무 검증 실패)는 사용자 정의 실패 처리에서 연동 임시 상태를 정리하고 기존 로그인 상태와 JWT를 유지한 채 `/mypage?error=...`로 이동합니다.

계정 식별과 보호 규칙:
- 연동 대상 회원은 연동 시작 시점의 로그인 세션에 저장된 회원 ID입니다. Google 이메일이나 Callback에서 새로 만든 인증 주체로 판단하지 않습니다.
- Google 이메일이 기존 회원과 같다는 이유만으로 자동 연결하지 않습니다.
- 기존 로그인 세션을 무효화하거나 새 Google 인증 주체로 대체하지 않습니다.
- API JWT를 재발급하거나 교체하지 않습니다. 일반 로그인 성공 처리(세션 발급, JWT 발급)를 실행하지 않습니다.
- 실패하면 기존 세션과 JWT를 변경하지 않고, 임시 상태만 정리합니다.
- 주요 오류(리다이렉트 쿼리): `google_failed`, `google_unverified`, `google_already_linked`.
- 요청 제한 (1.4): 계정 연동 콜백은 Google OAuth2 카운터를 공유합니다. 초과하면 HTTP 429를 반환하고, 연동 임시 상태만 정리하며 기존 로그인 세션과 JWT는 유지합니다.

### 2.7 Google 계정 연동 해제 (화면, 세션 기반, T8 A안)

- 메서드·URL: `POST <연동 해제 화면 경로>`. 경로는 구현 단계에서 확정합니다. 화면 경로이며 `/api/**`가 아닙니다. API 엔드포인트로 제공하지 않습니다.
- 인증: 화면 세션 필요 (로그인된 회원). 세션이 없으면 `/login`으로 이동합니다.
- 권한: 로그인된 회원 본인
- CSRF: 필요
- 요청: 폼 필드 `password` (현재 비밀번호)
- 처리 (FR-01-12, FR-01-13, T8 A안):
  1. 세션에서 로그인 회원을 확인합니다.
  2. 연동된 Google 계정이 없으면 해제하지 않고 `/mypage?error=google_not_linked`로 이동합니다.
  3. 회원에게 비밀번호(`password_hash`)가 없으면 Google 전용 계정입니다. 해제하면 로그인 수단이 없어지므로 해제하지 않고 `/mypage?error=password_required`로 이동합니다. 2.7.1로 먼저 로컬 비밀번호를 설정하도록 안내합니다.
  4. 입력한 비밀번호가 `password_hash`와 BCrypt로 일치하지 않으면 해제하지 않고 `/mypage?error=invalid_credentials`로 이동합니다.
  5. 검증이 끝나면 `member.google_sub = NULL`로 저장하고 `/mypage?google=unlinked`로 이동합니다.
- 주요 오류(리다이렉트 쿼리): `google_not_linked`, `password_required`, `invalid_credentials`.

### 2.7.1 로컬 비밀번호 설정 (화면, Google 전용 회원, FR-01-22)

- 메서드·URL: `GET <비밀번호 설정 화면 경로>` (화면 표시), `POST <비밀번호 설정 화면 경로>` (제출). 경로는 구현 단계에서 확정합니다.
- 인증: 화면 세션 필요 (로그인된 회원)
- 권한: 회원 본인. `password_hash`가 없는 회원만 설정할 수 있습니다.
- CSRF: 제출(POST)에 필요
- 요청: 폼 필드 `password`, `passwordConfirm`. 비밀번호 규칙은 회원가입과 같다 (8~20자, 영문·숫자·특수문자 각 1자 이상, 공백 불가, 두 값 일치).
- 처리:
  1. 세션에서 회원을 확인합니다.
  2. `password_hash`가 이미 있으면 `/mypage?error=password_already_set`으로 이동합니다. 비밀번호 변경은 이번 명세 범위가 아닙니다.
  3. 검증에 실패하면 `<비밀번호 설정 화면 경로>?error=validation_failed`로 이동합니다.
  4. 비밀번호를 BCrypt로 해시하여 `password_hash`에 저장합니다 (FR-01-02).
  5. `/mypage?password=set`으로 이동합니다. 이후 2.7의 연동 해제를 할 수 있습니다.
- 소셜 계정 자동 연결이나 별도 인증 방식을 추가하지 않습니다.

### 2.8 내 정보 조회

- 구현 상태: `main` 병합 완료 (PR #23, merge commit `56faff2`, 6.18).

- 메서드·URL: `GET /api/auth/me`
- 인증: 필요 (`access_token`, API 인증)
- 권한: 회원 본인
- 요청: 없음
- 응답 `200`:
```json
{
  "success": true,
  "data": { "memberId": 1, "email": "user@example.com", "nickname": "부산먹보", "role": "USER", "googleLinked": false, "hasPassword": true },
  "timestamp": "..."
}
```
- 주요 오류: 401 TOKEN_INVALID

### 2.9 회원가입 (화면)

- 메서드·URL: `POST /signup` (화면 폼 제출)
- 인증: 불필요
- 권한: 누구나
- 요청: 폼 필드 `email`, `password`, `passwordConfirm`, `nickname`. CSRF 토큰 필요.
- 처리: 2.1과 같은 검증과 저장을 수행한 뒤 자동 로그인합니다. Spring Security 화면 세션을 생성하고 JWT 쿠키(`access_token`, `refresh_token`)를 발급합니다 (FR-01-17).
- 응답: 성공 `302` → `/`.
- 실패는 JSON이 아니라 리다이렉트로 응답합니다: `/signup?error=email_duplicated`, `/signup?error=nickname_duplicated`, `/signup?error=validation_failed`.
- 요청 제한 (1.4, T15 확정): JSON 파싱과 DTO 검증 이전에 회원가입 요청으로 집계합니다 (IP별 1시간 5회). 초과하면 HTTP 429와 `Retry-After`를 반환하고 회원을 만들지 않습니다. 화면 표시 문구는 구현 단계에서 작성합니다 (T20, 1.4).

### 2.10 로그인 (화면)

- 메서드·URL: `POST /login` (Spring Security 폼 로그인 처리 경로)
- 인증: 불필요
- 권한: 누구나
- 요청: 폼 필드 `email`, `password`. CSRF 토큰 필요.
- 처리: 자격 증명을 검증하고 Spring Security 세션을 생성합니다. 기본 세션 보호 정책(세션 ID 변경 등)을 적용합니다 (세부 값은 T11). 이어서 JWT Access Token과 Refresh Token을 발급하고, Refresh Token의 SHA-256 해시를 `refresh_token`에 저장합니다. 쿠키를 설정합니다 (FR-01-17).
- 응답: 성공 `302` → `/`.
- 실패: `302` → `/login?error=invalid_credentials`. Google 전용 계정도 같은 결과입니다.
- 요청 제한 (1.4): 정규화 이메일+IP 실패 5회 뒤 6번째 요청부터, IP 보조 제한은 실패 20회 뒤 21번째 요청부터 HTTP 429와 `Retry-After`를 반환합니다. 제한된 요청은 비밀번호를 검증하지 않습니다.

### 2.11 로그아웃 (화면)

- 메서드·URL: `POST /logout`
- 인증: 세션 (세션이 없어도 `/login`으로 이동)
- 권한: 누구나 (로그인 상태에서 사용)
- 요청: CSRF 토큰 필요. 본문 없음.
- 처리 (T12 A안, 순서 고정):
  1. 브라우저가 `POST /api/auth/logout`을 먼저 호출합니다 (2.4, `X-XSRF-TOKEN` 헤더 포함). 서버는 Refresh Token을 폐기하고 `access_token`, `refresh_token` 쿠키를 만료시킵니다.
  2. 1이 성공한 경우에만 브라우저가 `POST /logout`을 호출합니다.
  3. 서버는 Spring Security 세션을 무효화하고 SecurityContext를 제거하며 `JSESSIONID`를 만료시킵니다.
  4. 완료 후 `302` → `/login`
- 1이 실패하면 `POST /logout`을 호출하지 않습니다. 화면 세션을 종료하지 않고 재시도를 안내합니다.
- 1이 성공하고 2가 실패하면 API 토큰은 이미 폐기된 상태입니다. 화면 로그아웃만 재시도합니다.
- 중복 로그아웃 요청은 안전하게 처리합니다.
- 요청 제한 대상이 아닙니다.
- `refresh_token` 쿠키의 Path는 `/api/auth`를 유지하며, `/logout`에서 이 쿠키를 읽지 않습니다 (DEVELOPMENT_RULES 5.7.2).

---

## 3. 카테고리 API (F1-02, F1-08)

### 3.1 카테고리 목록

- 메서드·URL: `GET /api/categories`
- 인증: 불필요
- 권한: 누구나
- 요청: 없음
- 응답 `200`:
```json
{
  "success": true,
  "data": [
    { "id": 1, "name": "한식", "sortOrder": 1 },
    { "id": 2, "name": "중식", "sortOrder": 2 }
  ],
  "timestamp": "..."
}
```
- 주요 오류: 없음

---

## 4. 맛집 API (F1-02, F1-03)

### 4.1 맛집 목록·검색

- 메서드·URL: `GET /api/restaurants`
- 인증: 불필요
- 권한: 누구나
- 요청 (쿼리 파라미터):

| 이름 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| category | Long | - | 카테고리 ID |
| keyword | String | - | 이름·주소 검색어 (최대 50자) |
| sort | String | latest | `latest` 또는 `rating` |
| page | Integer | 0 | 0부터 시작 |
| size | Integer | 12 | 최대 50 |

- 응답 `200`: 목록 구조(1.2)의 `content` 항목
```json
{
  "id": 10,
  "name": "해운대 돼지국밥",
  "category": { "id": 1, "name": "한식" },
  "address": "부산 해운대구 ...",
  "averageRating": 4.5,
  "reviewCount": 12,
  "thumbnailUrl": "https://<bucket>.s3.<region>.amazonaws.com/restaurants/10/....webp",
  "createdAt": "2026-11-01T12:00:00"
}
```
- 주요 오류: 400 VALIDATION_FAILED (size 초과 등)

### 4.2 맛집 상세

- 메서드·URL: `GET /api/restaurants/{id}`
- 인증: 불필요
- 권한: 누구나 (`isFavorited`, `isOwner`는 회원일 때만 의미 있음)
- 요청: 경로 변수 `id`
- 응답 `200`:
```json
{
  "id": 10,
  "name": "해운대 돼지국밥",
  "category": { "id": 1, "name": "한식" },
  "address": "부산 해운대구 ...",
  "phone": "051-000-0000",
  "description": "...",
  "latitude": 35.1587,
  "longitude": 129.1604,
  "averageRating": 4.5,
  "reviewCount": 12,
  "owner": { "memberId": 1, "nickname": "부산먹보" },
  "images": [
    { "id": 100, "url": "https://...", "sortOrder": 0 }
  ],
  "favoriteCount": 3,
  "isFavorited": false,
  "isOwner": false
}
```
- `isFavorited`, `isOwner`는 비회원이면 `false`입니다.
- 주요 오류: 404 RESTAURANT_NOT_FOUND

### 4.3 맛집 등록

- 메서드·URL: `POST /api/restaurants`
- 인증: 필요
- 권한: 회원 (`USER` 또는 `ADMIN`)
- 요청:
```json
{
  "name": "해운대 돼지국밥",
  "categoryId": 1,
  "address": "부산 해운대구 ...",
  "phone": "051-000-0000",
  "description": "...",
  "latitude": 35.1587,
  "longitude": 129.1604
}
```
- `phone`, `latitude`, `longitude`는 선택 항목입니다.
- 응답 `201`: 상세 DTO (4.2 구조). 등록한 회원이 `owner`가 됩니다.
- 주요 오류: 400 VALIDATION_FAILED, 400 CATEGORY_NOT_FOUND, 401 TOKEN_INVALID

### 4.4 맛집 수정 (작성자)

- 메서드·URL: `PUT /api/restaurants/{id}`
- 인증: 필요
- 권한: 작성자 본인 (FR-02-03). 관리자는 이 API로 다른 회원의 맛집을 수정할 수 없고, 11.3을 사용합니다.
- 요청: 4.3과 동일
- 응답 `200`: 상세 DTO (4.2 구조)
- 주요 오류: 400 VALIDATION_FAILED, 400 CATEGORY_NOT_FOUND, 401 TOKEN_INVALID, 403 FORBIDDEN, 404 RESTAURANT_NOT_FOUND

### 4.5 맛집 삭제 (작성자)

- 메서드·URL: `DELETE /api/restaurants/{id}`
- 인증: 필요
- 권한: 작성자 본인 (FR-02-03). 관리자는 11.4를 사용합니다.
- 요청: 경로 변수 `id`
- 처리 (FR-02-09, Q2, FUNCTION_SPEC F1-02): 리뷰, 찜, 이미지 메타데이터, AI 요약을 DB 트랜잭션에서 삭제합니다. S3 이미지 삭제는 커밋 후 수행하며, 실패한 키는 `storage_delete_task`에 기록합니다.
- 응답 `200`:
```json
{
  "success": true,
  "data": { "restaurantId": 10, "deletedImageCount": 3, "storageDeleteFailedCount": 0 },
  "timestamp": "..."
}
```
- `storageDeleteFailedCount`가 0보다 크면 일부 S3 객체가 재시도 대기 중입니다. DB 삭제는 완료된 상태이며, 이 응답은 실패하지 않습니다.
- 주요 오류: 401 TOKEN_INVALID, 403 FORBIDDEN, 404 RESTAURANT_NOT_FOUND

---

## 5. 맛집 이미지 API (F1-03)

### 5.1 이미지 업로드 (작성자)

- 메서드·URL: `POST /api/restaurants/{id}/images`
- 인증: 필요
- 권한: 작성자 본인. 관리자는 11.5를 사용합니다.
- 요청: `Content-Type: multipart/form-data`, 필드 `file` (파일 1개, JPEG·PNG·WEBP, 5MB 이하)
- 처리: S3에 업로드한 뒤 DB에 메타데이터를 저장합니다. DB 저장이 실패하면 업로드한 객체를 삭제하고, 그 삭제도 실패하면 `storage_delete_task`에 기록합니다 (FUNCTION_SPEC F1-03).
- 응답 `201`:
```json
{
  "success": true,
  "data": { "id": 100, "url": "https://...", "sortOrder": 0 },
  "timestamp": "..."
}
```
- 주요 오류: 400 IMAGE_TYPE_NOT_ALLOWED, 400 IMAGE_TOO_LARGE, 400 IMAGE_LIMIT_EXCEEDED, 401 TOKEN_INVALID, 403 FORBIDDEN, 404 RESTAURANT_NOT_FOUND, 502 STORAGE_ERROR

### 5.2 이미지 삭제 (작성자)

- 메서드·URL: `DELETE /api/restaurants/{id}/images/{imageId}`
- 인증: 필요
- 권한: 작성자 본인. 관리자는 11.6을 사용합니다.
- 처리 (FUNCTION_SPEC F1-03): 이미지 메타데이터를 삭제하고, 같은 트랜잭션에서 `storage_delete_task`를 기록한 뒤 커밋합니다. 그 후 S3 객체를 삭제합니다.
- 응답 `200`:
```json
{
  "success": true,
  "data": { "imageId": 100, "storageDeleted": true },
  "timestamp": "..."
}
```
- `storageDeleted`가 `false`이면 DB 삭제는 완료되었고 S3 객체는 재시도 대기 중입니다.
- 주요 오류: 401 TOKEN_INVALID, 403 FORBIDDEN, 404 RESTAURANT_NOT_FOUND (맛집 또는 이미지 없음)

---

## 6. 리뷰 API (F1-04)

### 6.1 리뷰 목록

- 메서드·URL: `GET /api/restaurants/{id}/reviews`
- 인증: 불필요
- 권한: 누구나 (`isMine`은 회원일 때만 의미 있음)
- 요청 (쿼리): `page` (기본 0), `size` (기본 10, 최대 50). 정렬은 최신순 고정.
- 응답 `200`: 목록 구조(1.2)의 `content` 항목
```json
{
  "id": 200,
  "rating": 5,
  "content": "국물이 진하고 좋아요.",
  "author": { "memberId": 2, "nickname": "맛객" },
  "isMine": false,
  "createdAt": "2026-11-02T18:30:00",
  "updatedAt": "2026-11-02T18:30:00"
}
```
- 주요 오류: 404 RESTAURANT_NOT_FOUND

### 6.2 리뷰 작성

- 메서드·URL: `POST /api/restaurants/{id}/reviews`
- 인증: 필요
- 권한: 회원 (`USER` 또는 `ADMIN`)
- 요청:
```json
{ "rating": 5, "content": "국물이 진하고 좋아요." }
```
- 응답 `201`:
```json
{
  "success": true,
  "data": { "id": 200, "rating": 5, "content": "국물이 진하고 좋아요.", "restaurantAverageRating": 4.7, "restaurantReviewCount": 13 },
  "timestamp": "..."
}
```
- 부작용: 맛집 평균 평점·리뷰 수 갱신, AI 요약 캐시 삭제 (F1-05).
- 주요 오류: 400 VALIDATION_FAILED, 401 TOKEN_INVALID, 404 RESTAURANT_NOT_FOUND, 409 REVIEW_ALREADY_EXISTS

### 6.3 리뷰 삭제 (작성자)

- 메서드·URL: `DELETE /api/reviews/{reviewId}`
- 인증: 필요
- 권한: 리뷰 작성자 본인 (FR-04-03). 관리자는 11.8을 사용합니다.
- 응답 `200`:
```json
{
  "success": true,
  "data": { "restaurantAverageRating": 4.5, "restaurantReviewCount": 12 },
  "timestamp": "..."
}
```
- 부작용: 평균 평점·리뷰 수 갱신, AI 요약 캐시 삭제.
- 주요 오류: 401 TOKEN_INVALID, 403 FORBIDDEN, 404 REVIEW_NOT_FOUND

---

## 7. AI 리뷰 요약 API (F1-05)

### 7.1 맛집 AI 요약 조회

- 메서드·URL: `GET /api/restaurants/{id}/summary`
- 인증: 불필요
- 권한: 누구나
- 요청: 경로 변수 `id`
- 처리: 리뷰 3개 미만이면 AI를 호출하지 않습니다. 캐시가 있으면 캐시를 반환하고, 없으면 OpenRouter를 호출해 저장한 뒤 반환합니다. 모델은 `OPENROUTER_MODEL`을 사용합니다 (FR-05-02).

응답 `200` — 리뷰 3개 미만:
```json
{
  "success": true,
  "data": { "status": "NOT_ENOUGH_REVIEWS", "summary": null, "reviewCount": 2, "message": "리뷰 3개 이상이 되면 AI 요약을 볼 수 있습니다." },
  "timestamp": "..."
}
```

응답 `200` — 요약 제공 (캐시 또는 신규 생성):
```json
{
  "success": true,
  "data": { "status": "READY", "summary": "진한 국물과 넉넉한 고기 양이 특징입니다. ...", "reviewCount": 12, "cached": true, "createdAt": "2026-11-03T09:00:00" },
  "timestamp": "..."
}
```

응답 `200` — AI 실패 (상세 화면은 정상 표시):
```json
{
  "success": true,
  "data": { "status": "UNAVAILABLE", "summary": null, "reviewCount": 12, "message": "요약을 불러올 수 없습니다. 잠시 후 다시 시도해 주세요." },
  "timestamp": "..."
}
```

- `status` 값: `NOT_ENOUGH_REVIEWS`, `READY`, `UNAVAILABLE`
- 주요 오류: 404 RESTAURANT_NOT_FOUND. AI 실패는 오류가 아니라 `status: UNAVAILABLE`로 응답합니다.

---

## 8. 찜 API (F1-06)

### 8.1 찜하기

- 메서드·URL: `POST /api/restaurants/{id}/favorite`
- 인증: 필요
- 권한: 회원 (`USER` 또는 `ADMIN`)
- 요청: 경로 변수 `id`
- 응답 `201`: `{ "success": true, "data": { "restaurantId": 10, "favorited": true, "favoriteCount": 4 }, "timestamp": "..." }`
- 주요 오류: 401 TOKEN_INVALID, 404 RESTAURANT_NOT_FOUND, 409 FAVORITE_ALREADY_EXISTS

### 8.2 찜 취소

- 메서드·URL: `DELETE /api/restaurants/{id}/favorite`
- 인증: 필요
- 권한: 회원 (본인 찜만 취소)
- 요청: 경로 변수 `id`
- 응답 `200`: `{ "success": true, "data": { "restaurantId": 10, "favorited": false, "favoriteCount": 3 }, "timestamp": "..." }`
- 주요 오류: 401 TOKEN_INVALID, 404 FAVORITE_NOT_FOUND

### 8.3 내 찜 목록

- 메서드·URL: `GET /api/me/favorites`
- 인증: 필요
- 권한: 회원 본인
- 요청 (쿼리): `page` (기본 0), `size` (기본 12, 최대 50). 정렬: 찜한 시각 내림차순.
- 응답 `200`: 목록 구조(1.2), `content` 항목은 맛집 목록 항목(4.1)과 같음
- 주요 오류: 401 TOKEN_INVALID

---

## 9. AI 맛집 추천 API (F1-07)

### 9.1 추천 요청

- 메서드·URL: `POST /api/recommendations`
- 인증: 필요
- 권한: 회원 (`USER` 또는 `ADMIN`)
- 요청:
```json
{ "preference": "2명이서 조용히 먹을 매운 국물 요리" }
```
- 처리: 후보는 DB에 등록된 맛집만 사용합니다 (FR-07-02). 모델은 `OPENROUTER_MODEL`을 사용합니다. AI가 반환한 `restaurantId`가 후보에 없으면 제외합니다 (FR-07-03).
- 응답 `200`:
```json
{
  "success": true,
  "data": {
    "recommendations": [
      { "restaurantId": 10, "name": "해운대 돼지국밥", "category": "한식", "averageRating": 4.5, "reason": "진한 국물과 조용한 분위기가 취향에 맞습니다." }
    ],
    "candidateCount": 42,
    "message": null
  },
  "timestamp": "..."
}
```
- 최대 5개까지 반환합니다.
- 후보가 없으면 `recommendations: []`, `message: "등록된 맛집이 없어 추천할 수 없습니다."`. AI는 호출하지 않습니다 (FR-07-05).
- 주요 오류: 400 VALIDATION_FAILED (1~200자), 401 TOKEN_INVALID, 502 AI_UNAVAILABLE, 502 AI_RESPONSE_INVALID

---

## 10. 화면 URL (Thymeleaf)

| 경로 | 메서드 | 인증 | 권한 | 설명 | 관련 기능 |
|------|--------|------|------|------|-----------|
| `/` | GET | 불필요 | 누구나 | 메인 페이지 (카테고리, 최신 맛집, 검색창) | F1-08 |
| `/login` | GET | 불필요 | 누구나 | 로그인 폼, Google 로그인 버튼, 오류 메시지(`error` 쿼리) | F1-01 |
| `/signup` | GET | 불필요 | 누구나 | 회원가입 폼 | F1-01 |
| `/signup` | POST | 불필요 | 누구나 | 회원가입 제출 (2.9) | F1-01 |
| `/login` | POST | 불필요 | 누구나 | 로그인 제출 (2.10) | F1-01 |
| `/logout` | POST | 세션 | 누구나 | 화면 로그아웃 (2.11) | F1-01 |
| `/mypage` | GET | 세션 필요 | 회원 본인 | 계정 설정: Google 계정 연동·해제, 로컬 비밀번호 설정(Google 전용 회원), 내 정보 | F1-01 |
| `/restaurants` | GET | 불필요 | 누구나 | 맛집 목록·검색 (`category`, `keyword`, `sort`, `page`) | F1-02 |
| `/restaurants/{id}` | GET | 불필요 | 누구나 | 맛집 상세 (이미지, 리뷰, AI 요약, 찜 버튼). 작성자에게는 수정·삭제 버튼 표시 | F1-02, F1-04, F1-05, F1-06 |
| `/restaurants/new` | GET | 세션 필요 | 회원 | 맛집 등록 폼 | F1-02 |
| `/restaurants/{id}/edit` | GET | 세션 필요 | 작성자 | 맛집 수정 폼 및 이미지 관리 | F1-02, F1-03 |
| `/favorites` | GET | 세션 필요 | 회원 본인 | 내 찜 목록 | F1-06 |
| `/recommend` | GET | 세션 필요 | 회원 | AI 추천 입력 및 결과 | F1-07 |
| `/admin` | GET | 세션 필요 | ADMIN | 관리자 대시보드 | F1-09 |
| `/admin/restaurants` | GET | 세션 필요 | ADMIN | 전체 맛집 목록, 수정·삭제 | F1-09 |
| `/admin/reviews` | GET | 세션 필요 | ADMIN | 전체 리뷰 목록, 삭제 | F1-09 |
| `/admin/members` | GET | 세션 필요 | ADMIN | 회원 목록·상세 | F1-09 |
| `/admin/storage-deletions` | GET | 세션 필요 | ADMIN | S3 삭제 재시도 대기 목록, 재시도 | F1-09 |

- 권한 없는 `/admin/**` 화면 요청은 403 화면을 보여 줍니다. 로그인하지 않았다면 `/login`으로 이동합니다.
- 화면 폼 제출(등록, 수정, 리뷰, 찜)은 위 REST API를 `fetch`로 호출하거나 일반 폼 POST(Spring MVC 컨트롤러)로 처리합니다. 구현 방식은 `DEVELOPMENT_RULES.md`의 6.3절을 따릅니다.

---

## 11. 관리자 API (F1-09)

모든 관리자 API는 `ADMIN` 권한이 필요합니다 (FR-09-07, FR-09-08). 권한 없는 요청은 403 `FORBIDDEN`, 로그인하지 않은 요청은 401 `TOKEN_INVALID`를 반환합니다.

관리자는 자신이 작성한 맛집과 리뷰에 대해서는 일반 회원과 같은 API(4.x, 5.x, 6.x)를 사용합니다. 다른 회원의 콘텐츠는 이 절의 API로만 관리합니다.

### 11.1 관리자 대시보드

- 메서드·URL: `GET /api/admin/dashboard`
- 인증: 필요
- 권한: ADMIN
- 요청: 없음
- 응답 `200`:
```json
{
  "success": true,
  "data": {
    "memberCount": 120,
    "restaurantCount": 45,
    "reviewCount": 310,
    "newRestaurantsLast7Days": 6,
    "newReviewsLast7Days": 21,
    "pendingStorageDeletionCount": 2
  },
  "timestamp": "..."
}
```
- 주요 오류: 401 TOKEN_INVALID, 403 FORBIDDEN

### 11.2 전체 맛집 목록

- 메서드·URL: `GET /api/admin/restaurants`
- 인증: 필요
- 권한: ADMIN
- 요청 (쿼리): `keyword` (이름·주소, 선택), `categoryId` (선택), `sort` (`latest` 기본, `rating`), `page` (기본 0), `size` (기본 20, 최대 50)
- 응답 `200`: 목록 구조(1.2)의 `content` 항목
```json
{
  "id": 10,
  "name": "해운대 돼지국밥",
  "category": { "id": 1, "name": "한식" },
  "address": "부산 해운대구 ...",
  "averageRating": 4.5,
  "reviewCount": 12,
  "imageCount": 3,
  "owner": { "memberId": 1, "nickname": "부산먹보", "email": "user@example.com" },
  "createdAt": "2026-11-01T12:00:00"
}
```
- 주요 오류: 401 TOKEN_INVALID, 403 FORBIDDEN

### 11.3 맛집 수정 (관리자)

- 메서드·URL: `PUT /api/admin/restaurants/{id}`
- 인증: 필요
- 권한: ADMIN
- 요청: 4.3과 같은 필드. 작성자(`owner`)는 바꾸지 않습니다.
- 응답 `200`: 상세 DTO (4.2 구조)
- 주요 오류: 400 VALIDATION_FAILED, 400 CATEGORY_NOT_FOUND, 401 TOKEN_INVALID, 403 FORBIDDEN, 404 RESTAURANT_NOT_FOUND

### 11.4 맛집 삭제 (관리자)

- 메서드·URL: `DELETE /api/admin/restaurants/{id}`
- 인증: 필요
- 권한: ADMIN
- 처리: 4.5와 같은 삭제 정책을 따릅니다 (FR-09-02, FR-02-09).
- 응답 `200`: 4.5와 같은 구조 (`restaurantId`, `deletedImageCount`, `storageDeleteFailedCount`)
- 주요 오류: 401 TOKEN_INVALID, 403 FORBIDDEN, 404 RESTAURANT_NOT_FOUND

### 11.5 이미지 업로드 (관리자)

- 메서드·URL: `POST /api/admin/restaurants/{id}/images`
- 인증: 필요
- 권한: ADMIN
- 요청·응답·처리: 5.1과 같습니다.
- 주요 오류: 400 IMAGE_TYPE_NOT_ALLOWED, 400 IMAGE_TOO_LARGE, 400 IMAGE_LIMIT_EXCEEDED, 401 TOKEN_INVALID, 403 FORBIDDEN, 404 RESTAURANT_NOT_FOUND, 502 STORAGE_ERROR

### 11.6 이미지 삭제 (관리자)

- 메서드·URL: `DELETE /api/admin/restaurants/{id}/images/{imageId}`
- 인증: 필요
- 권한: ADMIN
- 요청·응답·처리: 5.2와 같습니다.
- 주요 오류: 401 TOKEN_INVALID, 403 FORBIDDEN, 404 RESTAURANT_NOT_FOUND

### 11.7 전체 리뷰 목록

- 메서드·URL: `GET /api/admin/reviews`
- 인증: 필요
- 권한: ADMIN
- 요청 (쿼리): `restaurantId` (선택), `memberId` (선택), `page` (기본 0), `size` (기본 20, 최대 50). 정렬: 최신순.
- 응답 `200`: 목록 구조(1.2)의 `content` 항목
```json
{
  "id": 200,
  "restaurant": { "id": 10, "name": "해운대 돼지국밥" },
  "author": { "memberId": 2, "nickname": "맛객", "email": "foodie@example.com" },
  "rating": 5,
  "content": "국물이 진하고 좋아요.",
  "createdAt": "2026-11-02T18:30:00"
}
```
- 주요 오류: 401 TOKEN_INVALID, 403 FORBIDDEN

### 11.8 리뷰 삭제 (관리자)

- 메서드·URL: `DELETE /api/admin/reviews/{reviewId}`
- 인증: 필요
- 권한: ADMIN
- 처리: 6.3과 같이 맛집 평균·리뷰 수를 갱신하고 AI 요약 캐시를 삭제합니다 (FR-09-03).
- 응답 `200`: 6.3과 같은 구조 (`restaurantAverageRating`, `restaurantReviewCount`)
- 주요 오류: 401 TOKEN_INVALID, 403 FORBIDDEN, 404 REVIEW_NOT_FOUND

### 11.9 회원 목록

- 메서드·URL: `GET /api/admin/members`
- 인증: 필요
- 권한: ADMIN
- 요청 (쿼리): `keyword` (이메일·닉네임, 선택), `page` (기본 0), `size` (기본 20, 최대 50)
- 응답 `200`: 목록 구조(1.2)의 `content` 항목
```json
{
  "memberId": 1,
  "email": "user@example.com",
  "nickname": "부산먹보",
  "role": "USER",
  "googleLinked": false,
  "restaurantCount": 3,
  "reviewCount": 7,
  "createdAt": "2026-10-01T09:00:00"
}
```
- 회원 정지, 탈퇴, 권한 변경 기능은 이 명세에 포함하지 않습니다 (T1, T2).
- 주요 오류: 401 TOKEN_INVALID, 403 FORBIDDEN

### 11.10 회원 상세

- 메서드·URL: `GET /api/admin/members/{memberId}`
- 인증: 필요
- 권한: ADMIN
- 요청: 경로 변수 `memberId`
- 응답 `200`: 11.9의 항목과 같은 구조
- 주요 오류: 401 TOKEN_INVALID, 403 FORBIDDEN, 404 MEMBER_NOT_FOUND

### 11.11 S3 삭제 재시도 대기 목록

- 메서드·URL: `GET /api/admin/storage-deletions`
- 인증: 필요
- 권한: ADMIN
- 요청 (쿼리): `page` (기본 0), `size` (기본 20, 최대 50). 정렬: 생성 시각 오래된 순.
- 응답 `200`: 목록 구조(1.2)의 `content` 항목
```json
{
  "id": 5,
  "s3Key": "restaurants/10/3f2a....webp",
  "retryCount": 2,
  "lastError": "S3 timeout",
  "createdAt": "2026-11-04T10:00:00",
  "updatedAt": "2026-11-05T10:00:00"
}
```
- `lastError`는 비밀 정보를 포함하지 않습니다 (DEVELOPMENT_RULES 5.3).
- 주요 오류: 401 TOKEN_INVALID, 403 FORBIDDEN

### 11.12 S3 삭제 재시도

- 메서드·URL: `POST /api/admin/storage-deletions/retry`
- 인증: 필요
- 권한: ADMIN
- 요청: 본문 없음
- 처리: `storage_delete_task`의 행을 하나씩 S3에서 삭제합니다. 성공하면 행을 삭제하고, 실패하면 `retry_count`를 올리고 `last_error`를 기록합니다. S3에 객체가 이미 없으면 성공으로 처리합니다.
- 응답 `200`:
```json
{
  "success": true,
  "data": { "processed": 2, "succeeded": 1, "failed": 1 },
  "timestamp": "..."
}
```
- 개별 객체 실패는 오류가 아니므로 `200`으로 응답합니다.
- 주요 오류: 401 TOKEN_INVALID, 403 FORBIDDEN

---

## 12. 엔드포인트 요약

| 메서드 | 경로 | 인증 | 권한 | 기능 |
|--------|------|------|------|------|
| POST | /api/auth/signup | 불필요 | 누구나 | F1-01 |
| POST | /api/auth/login | 불필요 | 누구나 | F1-01 |
| POST | /api/auth/refresh | Refresh 쿠키 | 유효한 Refresh Token | F1-01 |
| POST | /api/auth/logout | 불필요 | 누구나 | F1-01 |
| GET | /oauth2/authorization/google | 불필요 | 누구나 | F1-01 |
| GET | /login/oauth2/code/google | 불필요 (OAuth 콜백) | 누구나 | F1-01 |
| POST | /signup | 불필요 | 누구나 | F1-01 (화면, 2.9) |
| POST | /login | 불필요 | 누구나 | F1-01 (화면, 2.10) |
| POST | /logout | 세션 | 누구나 | F1-01 (화면, 2.11) |
| POST | <연동 시작 화면 경로, 구현 단계 확정> | 세션 | 로그인 회원 본인 | F1-01 (화면, 2.6) |
| POST | <연동 해제 화면 경로, 구현 단계 확정> | 세션 | 로그인 회원 본인 (비밀번호 재확인) | F1-01 (화면, 2.7) |
| POST | <비밀번호 설정 화면 경로, 구현 단계 확정> | 세션 | Google 전용 회원 본인 | F1-01 (화면, 2.7.1) |
| GET, POST | <닉네임 설정 화면 경로, 구현 단계 확정> | 가입 진행 상태 | Google 신규 가입 진행자 | F1-01 (화면, 2.5.1) |
| GET | /api/auth/me | 필요 | 회원 본인 | F1-01 |
| GET | /api/categories | 불필요 | 누구나 | F1-02, F1-08 |
| GET | /api/restaurants | 불필요 | 누구나 | F1-02 |
| GET | /api/restaurants/{id} | 불필요 | 누구나 | F1-02 |
| POST | /api/restaurants | 필요 | 회원 | F1-02 |
| PUT | /api/restaurants/{id} | 필요 | 작성자 | F1-02 |
| DELETE | /api/restaurants/{id} | 필요 | 작성자 | F1-02 |
| POST | /api/restaurants/{id}/images | 필요 | 작성자 | F1-03 |
| DELETE | /api/restaurants/{id}/images/{imageId} | 필요 | 작성자 | F1-03 |
| GET | /api/restaurants/{id}/reviews | 불필요 | 누구나 | F1-04 |
| POST | /api/restaurants/{id}/reviews | 필요 | 회원 | F1-04 |
| DELETE | /api/reviews/{reviewId} | 필요 | 작성자 | F1-04 |
| GET | /api/restaurants/{id}/summary | 불필요 | 누구나 | F1-05 |
| POST | /api/restaurants/{id}/favorite | 필요 | 회원 | F1-06 |
| DELETE | /api/restaurants/{id}/favorite | 필요 | 회원 | F1-06 |
| GET | /api/me/favorites | 필요 | 회원 본인 | F1-06 |
| POST | /api/recommendations | 필요 | 회원 | F1-07 |
| GET | /api/admin/dashboard | 필요 | ADMIN | F1-09 |
| GET | /api/admin/restaurants | 필요 | ADMIN | F1-09 |
| PUT | /api/admin/restaurants/{id} | 필요 | ADMIN | F1-09 |
| DELETE | /api/admin/restaurants/{id} | 필요 | ADMIN | F1-09 |
| POST | /api/admin/restaurants/{id}/images | 필요 | ADMIN | F1-09 |
| DELETE | /api/admin/restaurants/{id}/images/{imageId} | 필요 | ADMIN | F1-09 |
| GET | /api/admin/reviews | 필요 | ADMIN | F1-09 |
| DELETE | /api/admin/reviews/{reviewId} | 필요 | ADMIN | F1-09 |
| GET | /api/admin/members | 필요 | ADMIN | F1-09 |
| GET | /api/admin/members/{memberId} | 필요 | ADMIN | F1-09 |
| GET | /api/admin/storage-deletions | 필요 | ADMIN | F1-09 |
| POST | /api/admin/storage-deletions/retry | 필요 | ADMIN | F1-09 |
