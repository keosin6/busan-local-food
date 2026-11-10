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
- OAuth2 임시 세션은 Google 로그인과 계정 연동 흐름에서만 사용합니다. `/api/**` 요청 인증에는 사용하지 않습니다 (DEVELOPMENT_RULES 5.7).
- CSRF: 화면 세션 쿠키 기반 상태 변경 요청과 JWT 쿠키 기반 API 상태 변경 요청(POST, PUT, DELETE)은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 함께 보냅니다. Bearer 헤더 요청은 CSRF 헤더를 요구하지 않습니다.
- 권한 표기:
  - `누구나`: 인증 없이 접근 가능
  - `회원`: 로그인한 회원(`USER` 또는 `ADMIN`)
  - `작성자`: 해당 리소스를 만든 회원 본인
  - `ADMIN`: 관리자(`role = ADMIN`)만 접근 가능. 서버에서 요청마다 검증합니다 (FR-09-08).

### 1.1 쿠키 명세

| 쿠키 | 내용 | HttpOnly | SameSite | Secure | Path | 만료 |
|------|------|----------|----------|--------|------|------|
| `JSESSIONID` | 화면 로그인 세션 (Spring Security 세션) | 예 | Lax | 운영만 | `/` | 세션 만료 또는 로그아웃까지 (T11) |
| `access_token` | API 인증용 Access Token(JWT) | 예 | Lax | 운영만 | `/api` | 30분 |
| `refresh_token` | Refresh Token 원문 | 예 | Lax | 운영만 | `/api/auth` | 14일 |
| `XSRF-TOKEN` | CSRF 토큰 | 아니오 (JS가 헤더에 실어야 함) | Lax | 운영만 | `/` | 세션 |
| OAuth2 임시 세션 쿠키 (이름은 구현 단계에서 확정) | Google 인증 흐름의 상태 식별자. API 인증에 사용하지 않음 | 예 | Lax | 운영만 | `/` | 인증 흐름 종료까지 (T11) |

- `access_token`의 Path는 `/api`로 제한합니다. API 요청에서만 전송됩니다. 화면 요청은 `JSESSIONID`로 인증합니다.
- `refresh_token`의 Path는 `/api/auth`로 제한합니다. 재발급과 API 로그아웃 요청에서만 전송됩니다. 화면 로그아웃(`/logout`)에는 전송되지 않습니다 (T12).
- 쿠키를 만료시킬 때는 발급 당시와 같은 Path와 Domain을 사용합니다.
- 만료 시간은 환경 변수 `JWT_ACCESS_TOKEN_EXPIRY_SECONDS`, `JWT_REFRESH_TOKEN_EXPIRY_SECONDS`를 따릅니다. 위 값은 기본값입니다.

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
| 400 | VALIDATION_FAILED | 입력값 검증 실패 |
| 400 | CATEGORY_NOT_FOUND | 존재하지 않는 카테고리 |
| 400 | IMAGE_TYPE_NOT_ALLOWED | 허용되지 않은 이미지 형식 |
| 400 | IMAGE_TOO_LARGE | 이미지 크기 초과 |
| 400 | IMAGE_LIMIT_EXCEEDED | 맛집당 이미지 개수 초과 |
| 401 | INVALID_CREDENTIALS | 이메일 또는 비밀번호 불일치 (재인증 실패 포함) |
| 401 | TOKEN_INVALID | 토큰 없음, 만료, 폐기 또는 위조 |
| 403 | FORBIDDEN | 권한 없음 (작성자 아님, 또는 ADMIN 아님) |
| 404 | MEMBER_NOT_FOUND | 회원 없음 |
| 404 | RESTAURANT_NOT_FOUND | 맛집 없음 |
| 404 | REVIEW_NOT_FOUND | 리뷰 없음 |
| 404 | FAVORITE_NOT_FOUND | 찜 없음 |
| 404 | GOOGLE_NOT_LINKED | 연동된 Google 계정 없음 |
| 409 | EMAIL_DUPLICATED | 이메일 중복 |
| 409 | REVIEW_ALREADY_EXISTS | 같은 맛집에 리뷰 중복 |
| 409 | FAVORITE_ALREADY_EXISTS | 찜 중복 |
| 409 | GOOGLE_ALREADY_LINKED | 이미 다른 회원에게 연결된 Google 계정 (FR-01-11) |
| 409 | LAST_LOGIN_METHOD | 남은 로그인 수단이 없어 Google 연동을 해제할 수 없음 (FR-01-13) |
| 502 | STORAGE_ERROR | S3 오류 (업로드 등 S3 호출이 필요한 요청 실패) |
| 502 | AI_UNAVAILABLE | OpenRouter 호출 실패 또는 타임아웃 |
| 502 | AI_RESPONSE_INVALID | AI 응답 파싱 실패 |
| 500 | INTERNAL_ERROR | 서버 내부 오류 |

> S3 객체 삭제 실패는 오류 코드가 아닙니다. DB 삭제는 완료된 상태이므로 `200`과 함께 결과 필드(`storageDeleted`, `storageDeleteFailedCount`)로 알립니다 (FUNCTION_SPEC F1-02, F1-03).

---

## 2. 인증 API (F1-01)

2.1~2.8은 API 엔드포인트(JWT 인증)입니다. 2.9~2.11은 화면 폼 엔드포인트(Spring Security 세션 인증)입니다. 화면 로그인, 화면 회원가입, Google 로그인은 세션과 JWT를 함께 발급합니다. API 로그인과 API 회원가입은 JWT만 발급합니다 (FR-01-17, FR-01-18).

### 2.1 회원가입 (API)

- 메서드·URL: `POST /api/auth/signup`
- 인증: 불필요
- 권한: 누구나
- 요청:
```json
{
  "email": "user@example.com",
  "password": "<8자 이상>",
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
- 주요 오류: 400 VALIDATION_FAILED, 409 EMAIL_DUPLICATED

### 2.2 로그인 (API)

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
- 주요 오류: 401 INVALID_CREDENTIALS (Google 전용 계정의 비밀번호 로그인 시도도 같은 응답)

### 2.3 Access Token 재발급 (Refresh Token 로테이션)

- 메서드·URL: `POST /api/auth/refresh`
- 인증: `refresh_token` 쿠키 필요 (Access Token은 불필요)
- 권한: 유효한 Refresh Token을 가진 회원
- 요청: 본문 없음. `refresh_token` 쿠키를 전송합니다.
- 처리 (FR-01-06):
  1. 쿠키 원문의 SHA-256을 계산하여 `refresh_token`에서 조회합니다.
  2. 행이 없거나, 폐기(`revoked_at`)되었거나, 만료되었으면 실패합니다.
  3. 성공하면 기존 행의 `revoked_at`을 기록하고, 새 Refresh Token 행을 INSERT합니다.
  4. 새 `access_token` 쿠키와 새 `refresh_token` 쿠키를 설정합니다.
- 응답 `200`:
```json
{
  "success": true,
  "data": { "accessTokenExpiresIn": 1800 },
  "timestamp": "..."
}
```
- 실패 시 두 쿠키를 만료시킵니다.
- 화면 세션(`JSESSIONID`)은 이 API로 재발급되지 않습니다.
- 주요 오류: 401 TOKEN_INVALID (쿠키 없음, 만료, 폐기, 위조)
- 재사용 탐지 정책은 T7에서 확정합니다.

### 2.4 로그아웃 (API)

- 메서드·URL: `POST /api/auth/logout`
- 인증: 불필요 (Access Token이 만료되었어도 로그아웃할 수 있어야 하므로 요구하지 않습니다)
- 권한: 누구나
- 요청: 본문 없음. `refresh_token` 쿠키가 있으면 해당 토큰을 폐기합니다.
- 처리: 해당 `refresh_token` 행의 `revoked_at`을 기록하고, `access_token`과 `refresh_token` 쿠키를 만료시킵니다 (FR-01-07).
- 응답 `200`:
```json
{ "success": true, "data": null, "timestamp": "..." }
```
- 쿠키가 없거나 이미 폐기된 토큰이어도 `200`을 반환합니다.
- 화면 세션을 종료하지 않습니다. 화면 로그아웃은 2.11을 사용합니다.

### 2.5 Google 로그인 (시작 및 콜백)

- 메서드·URL: `GET /oauth2/authorization/google` (시작), `GET /login/oauth2/code/google` (콜백)
- 인증: 불필요
- 권한: 누구나
- 요청: 브라우저 리다이렉트. Google 인증 페이지로 이동합니다.
- 콜백 처리 (FR-01-09, Q1):
  1. 콜백의 `state`가 OAuth2 임시 세션에 보관된 값과 일치하고 아직 사용되지 않았는지 확인합니다 (CSRF 방지). 일치하지 않으면 로그인하지 않고 `/login?error=google_failed`로 이동합니다.
  2. Google 프로필의 `sub`, `email`, `email_verified`를 확인합니다.
  3. `google_sub`가 같은 회원이 있으면 로그인합니다. Spring Security 화면 세션을 생성하고 JWT 쿠키(`access_token`, `refresh_token`)를 설정한 뒤 `/`로 리다이렉트합니다.
  4. 없고 같은 `email`의 기존 회원이 있으면 **자동 연결하지 않습니다.** `/login?error=email_account_exists`로 리다이렉트합니다. 화면에는 "기존 계정으로 로그인한 뒤 마이페이지에서 Google 계정을 연동해 주세요." 를 표시합니다.
  5. `email_verified`가 아니면 `/login?error=google_unverified`로 리다이렉트합니다.
  6. 둘 다 없으면 새 회원을 만듭니다 (`role = USER`, `password_hash = NULL`, `google_sub = sub`). 닉네임 규칙은 T3에서 정합니다. 화면 세션과 JWT 쿠키를 함께 발급하고 `/`로 리다이렉트합니다.
- 실패(Google 인증 오류 등): `/login?error=google_failed`로 리다이렉트합니다.
- 발급: 성공하면 화면 세션과 JWT를 함께 발급합니다 (FR-01-17).
- 주요 오류(리다이렉트 쿼리): `email_account_exists`, `google_unverified`, `google_failed`

### 2.6 Google 계정 연동 시작

- 메서드·URL: `GET /api/me/google/link`
- 인증: 필요 (`access_token`)
- 권한: 회원 본인 (로그인한 회원 자신의 계정에만 연동)
- 화면 `/mypage`에서 호출하더라도 이 API는 JWT로 회원을 확인합니다. 화면 세션만으로는 연동을 시작할 수 없습니다.
- 연동 흐름은 화면 세션의 로그인 회원을 다른 회원으로 교체하지 않습니다.
- 인증 조건: 요청의 `access_token`으로 회원을 확인합니다. 이 검증은 연동 시작 시점에만 수행합니다. Access Token이 없거나 만료되었으면 401 TOKEN_INVALID로 거부하고 흐름을 시작하지 않습니다.
- 요청: 본문 없음. 브라우저 리다이렉트 API입니다.
- 처리 (FR-01-10, FR-01-12):
  1. Access Token으로 검증된 회원 ID와 흐름 종류(연동)를 OAuth2 임시 세션에 저장합니다. 같은 임시 세션에 OAuth2 `state` 값과 Authorization Request를 보관합니다. 보관 방식은 T11에서 확정합니다.
  2. Google 인증 페이지로 리다이렉트합니다.
  3. 콜백(2.5와 같은 URL)에서 다음을 검증합니다. (a) 콜백의 `state`가 현재 브라우저의 OAuth2 임시 세션에 보관된 값과 일치하고 한 번만 사용되는지 (CSRF 방지). (b) 임시 세션이 존재하고 만료되지 않았는지. 하나라도 실패하면 연동하지 않습니다.
  4. 연동 대상 회원은 임시 세션에 저장된 회원 ID로 정합니다. 그 회원이 아직 존재하는지 확인하고, 없으면 연동하지 않습니다. 콜백은 `access_token`을 요구하지 않으며, Google 인증 중 Access Token이 만료되어도 임시 세션이 유효하면 연동을 완료합니다 (FR-01-15).
  5. 해당 `google_sub`가 다른 회원에게 연결되어 있으면 연동하지 않습니다. 기존 UNIQUE 제약(`uk_member_google_sub`)으로도 중복 연결을 막습니다.
  6. 성공하면 `member.google_sub = sub`를 저장하고 `/mypage?google=linked`로 리다이렉트합니다.
- 실패 리다이렉트:
  - `/mypage?error=google_already_linked` — 이미 다른 회원에게 연결된 Google 계정 (409 GOOGLE_ALREADY_LINKED에 해당)
  - `/mypage?error=google_unverified` — 검증되지 않은 Google 계정
  - `/mypage?error=google_failed` — 인증 실패, `state` 불일치, 임시 세션 없음 또는 만료, 연동 대상 회원 없음
- 주요 오류: 401 TOKEN_INVALID (연동 시작 시 Access Token 없음 또는 만료 → 로그인 페이지로 이동), 409 GOOGLE_ALREADY_LINKED
- 이미 자신에게 연동된 경우에는 변경 없이 `/mypage?google=linked`로 리다이렉트합니다.

### 2.7 Google 계정 연동 해제

- 메서드·URL: `DELETE /api/me/google`
- 인증: 필요 (`access_token`)
- 권한: 회원 본인
- 요청:
```json
{ "password": "<현재 비밀번호>" }
```
- 처리 (FR-01-12, FR-01-13, T8):
  1. 연동된 Google 계정이 없으면 404 GOOGLE_NOT_LINKED.
  2. 회원에게 비밀번호가 없으면(Google 전용 계정) 해제하면 로그인 수단이 없어지므로 409 LAST_LOGIN_METHOD로 거부합니다.
  3. 비밀번호가 맞지 않으면 401 INVALID_CREDENTIALS로 거부합니다 (재인증).
  4. 성공하면 `member.google_sub = NULL`로 저장합니다.
- 응답 `200`:
```json
{ "success": true, "data": { "googleLinked": false }, "timestamp": "..." }
```
- 주요 오류: 401 TOKEN_INVALID, 401 INVALID_CREDENTIALS, 404 GOOGLE_NOT_LINKED, 409 LAST_LOGIN_METHOD

### 2.8 내 정보 조회

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
- 실패는 JSON이 아니라 리다이렉트로 응답합니다: `/signup?error=email_duplicated`, `/signup?error=validation_failed`.

### 2.10 로그인 (화면)

- 메서드·URL: `POST /login` (Spring Security 폼 로그인 처리 경로)
- 인증: 불필요
- 권한: 누구나
- 요청: 폼 필드 `email`, `password`. CSRF 토큰 필요.
- 처리: 자격 증명을 검증하고 Spring Security 세션을 생성합니다. 기본 세션 보호 정책(세션 ID 변경 등)을 적용합니다 (세부 값은 T11). 이어서 JWT Access Token과 Refresh Token을 발급하고, Refresh Token의 SHA-256 해시를 `refresh_token`에 저장합니다. 쿠키를 설정합니다 (FR-01-17).
- 응답: 성공 `302` → `/`.
- 실패: `302` → `/login?error=invalid_credentials`. Google 전용 계정도 같은 결과입니다.

### 2.11 로그아웃 (화면)

- 메서드·URL: `POST /logout`
- 인증: 세션 (세션이 없어도 `/`로 이동)
- 권한: 누구나 (로그인 상태에서 사용)
- 요청: CSRF 토큰 필요. 본문 없음.
- 처리: Spring Security 세션을 무효화하고 SecurityContext를 제거합니다. `access_token`, `refresh_token` 쿠키를 만료시킵니다 (발급 당시와 같은 Path 사용).
- Refresh Token DB 행 폐기 방식은 T12에서 확정합니다. `refresh_token` 쿠키의 Path가 `/api/auth`이므로 `/logout` 요청에서는 쿠키를 읽을 수 없습니다. 제안: 화면 로그아웃 화면이 `POST /api/auth/logout`을 먼저 호출한 뒤 `POST /logout`을 호출합니다.
- 응답: `302` → `/`.

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
| `/mypage` | GET | 세션 필요 | 회원 본인 | 계정 설정: Google 계정 연동·해제, 내 정보 | F1-01 |
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
| GET | /api/me/google/link | 필요 | 회원 본인 | F1-01 |
| DELETE | /api/me/google | 필요 | 회원 본인 (비밀번호 재확인) | F1-01 |
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
