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

### 화면
- `/signup` 회원가입 폼 (제출: `POST /signup`): 이메일, 비밀번호, 비밀번호 확인, 닉네임
- `/login` 로그인 폼 (제출: `POST /login`): 이메일, 비밀번호, "Google로 로그인" 버튼, 오류 메시지 (`error` 쿼리 값에 따라 표시)
- `/mypage` 계정 설정 (세션 로그인 필요): 내 정보, Google 계정 연동 버튼 또는 연동 해제 버튼
- 헤더: 로그인 상태(세션 기준)에 따라 "로그인/회원가입" 또는 "마이페이지/내 찜 목록/로그아웃" 표시. 관리자에게는 "관리자" 메뉴 추가

### 회원가입 흐름 (화면)
1. 이메일 형식, 비밀번호 길이(8자 이상), 비밀번호 확인 일치, 닉네임(2~20자)을 검증합니다. 실패하면 `/signup?error=validation_failed`로 이동합니다.
2. 이메일이 이미 존재하면 `/signup?error=email_duplicated`로 이동합니다. Google로만 가입된 계정과 이메일이 같은 경우도 같습니다.
3. 비밀번호를 BCrypt로 해시하여 `member`에 저장합니다 (`role = USER`, `google_sub = NULL`).
4. 자동 로그인합니다. Spring Security 세션을 생성하고, JWT Access Token과 Refresh Token을 발급합니다 (Refresh Token의 SHA-256 해시는 `refresh_token`에 저장). 쿠키를 설정하고 메인 페이지(`/`)로 이동합니다.

### 회원가입 흐름 (API)
1. 1~3번은 화면 회원가입과 같습니다 (`POST /api/auth/signup`). 실패는 JSON 오류(400 `VALIDATION_FAILED`, 409 `EMAIL_DUPLICATED`)로 응답합니다.
2. 4번에서 Spring Security 세션을 만들지 않습니다. JWT 쿠키만 발급하고 201 JSON으로 응답합니다.

### 로그인 흐름 (화면)
1. `POST /login`으로 이메일과 비밀번호를 검증합니다. 없거나, 비밀번호가 틀리거나, `password_hash`가 없는(Google 전용) 경우 같은 결과(`/login?error=invalid_credentials`)를 반환합니다. 계정 존재 여부와 가입 방식을 노출하지 않습니다.
2. 성공하면 Spring Security 세션을 생성합니다. 세션 ID 변경 등 Spring Security의 기본 세션 보호 정책을 적용합니다 (세부 값은 T11).
3. JWT Access Token을 발급하여 `access_token` 쿠키(HttpOnly, Path `/api`)에 담습니다. 기본 만료 30분.
4. Refresh Token 원문을 발급하여 `refresh_token` 쿠키(HttpOnly, Path `/api/auth`)에 담고, SHA-256 해시를 `refresh_token` 테이블에 저장합니다 (FR-01-05). 기본 만료 14일.
5. 메인 페이지(`/`)로 이동합니다.

### 로그인 흐름 (API)
1. `POST /api/auth/login`으로 이메일과 비밀번호를 검증합니다. 실패는 401 `INVALID_CREDENTIALS`입니다.
2. JWT Access Token과 Refresh Token을 발급합니다. 쿠키를 설정하고 JSON으로 응답합니다.
3. Spring Security 세션을 생성하지 않습니다. 세션이 있더라도 API 인증을 대신하지 않습니다.

### 재발급 흐름 (API, Refresh Token 로테이션, FR-01-06)
1. 요청의 `refresh_token` 쿠키 원문을 SHA-256으로 해시합니다.
2. `refresh_token`에서 해시가 일치하는 행을 찾습니다. 없거나, `revoked_at`이 있거나, 만료되었으면 `TOKEN_INVALID`(401)를 반환하고 두 JWT 쿠키를 제거합니다.
3. 기존 행의 `revoked_at`을 현재 시각으로 기록합니다.
4. 새 Refresh Token 원문을 발급하고, 해시를 새 행으로 INSERT합니다.
5. 새 `access_token` 쿠키와 새 `refresh_token` 쿠키를 설정합니다.
- 화면 세션은 재발급 대상이 아닙니다. 화면 세션의 만료 정책은 T11에서 정합니다.
- 재사용 탐지(폐기된 토큰의 재사용)에 따른 추가 조치는 T7에서 확정합니다.

### 로그아웃 흐름 (화면, FR-01-07)
1. `POST /logout`을 호출합니다 (CSRF 토큰 필요).
2. Spring Security 세션을 무효화하고 SecurityContext를 제거합니다.
3. `access_token`, `refresh_token` 쿠키를 만료시킵니다. 발급 당시와 같은 Path를 사용합니다.
4. `refresh_token`의 DB 행을 폐기합니다. 다만 `refresh_token` 쿠키의 Path는 `/api/auth`라서 `/logout` 요청에는 전송되지 않습니다. 폐기 방식은 T12에서 확정합니다.
5. 메인 페이지(`/`)로 이동합니다.

### 로그아웃 흐름 (API)
1. `POST /api/auth/logout`을 호출합니다. 인증을 요구하지 않으므로 Access Token이 만료되었어도 호출할 수 있습니다.
2. `refresh_token` 쿠키가 있으면 해당 행의 `revoked_at`을 기록합니다. 쿠키가 없거나 이미 폐기되었어도 실패로 보지 않습니다.
3. `access_token`, `refresh_token` 쿠키를 만료시킵니다.
4. 화면 세션은 종료하지 않습니다. API 로그아웃은 화면 로그인 상태를 바꾸지 않습니다.

### Google 로그인 흐름 (FR-01-09, FR-01-17)
1. "Google로 로그인" 클릭 → Spring Security OAuth2 Client가 Google 인증 페이지로 이동합니다. 이때 `state` 값과 흐름 종류(로그인)를 OAuth2 임시 세션에 보관합니다.
2. 콜백에서 `state`가 임시 세션에 보관한 값과 일치하고 아직 사용되지 않았는지 확인합니다 (CSRF 방지). 일치하면 Google 프로필의 `sub`, `email`, `email_verified`를 확인합니다. 일치하지 않으면 로그인하지 않고 `/login?error=google_failed`로 이동합니다.
3. 처리 규칙:
   - `google_sub`가 같은 회원이 있으면 그 회원으로 로그인합니다.
   - 없고 같은 `email`의 기존 회원이 있으면 **자동으로 연결하지 않습니다.** 로그인하지 않고 `/login?error=email_account_exists`로 이동합니다. 화면에는 "기존 계정으로 로그인한 뒤 마이페이지에서 Google 계정을 연동해 주세요."를 표시합니다.
   - `email_verified`가 아니면 `/login?error=google_unverified`로 이동합니다.
   - 둘 다 없으면 새 회원을 만듭니다 (`role = USER`, `password_hash = NULL`, `google_sub = sub`). 닉네임 생성 규칙은 T3에서 정합니다.
4. 로그인에 성공하면 화면 로그인과 같은 방식으로 Spring Security 세션과 JWT를 모두 발급합니다. 메인 페이지(`/`)로 이동합니다.

### 화면 세션, API JWT, OAuth2 임시 세션의 관계

| 구분 | 역할 | 수명 |
|------|------|------|
| 화면 세션 (Spring Security) | 일반 화면 요청의 회원 식별 | 세션 만료 또는 화면 로그아웃까지 (세부 값 T11) |
| API JWT | `/api/**` 요청의 회원 식별. Access Token(쿠키 또는 Bearer), Refresh Token(쿠키, DB에는 해시) | Access Token 30분, Refresh Token 14일 |
| OAuth2 임시 세션 | Google 인증 진행 상태만 보관: `state`, Authorization Request, 연동 요청 회원 ID, 흐름 종류 | 인증 흐름이 끝날 때까지. 만료 시간은 T11 |

규칙:
1. 화면 요청은 화면 세션으로만 인증합니다. JWT만으로 화면 로그인 상태를 인정하지 않습니다.
2. `/api/**` 요청은 JWT로만 인증합니다. 세션만으로 API 로그인 상태를 인정하지 않습니다.
3. OAuth2 임시 세션은 회원의 로그인 상태를 나타내지 않습니다. `/api/**` 요청을 임시 세션만으로 인증하지 않습니다.
4. OAuth2 임시 세션에는 흐름 상태만 저장합니다. Access Token, Refresh Token 원문, 비밀번호는 저장하지 않습니다.
5. 인증 흐름이 끝나면(성공이든 실패든) 해당 흐름의 임시 상태를 제거합니다. 같은 `state`는 한 번만 사용할 수 있습니다.
6. Google 로그인 흐름이 성공하면 화면 세션과 JWT를 발급합니다. Google 계정 연동 흐름에서는 기존 로그인 세션과 JWT를 교체하지 않습니다.
7. 세션 저장 방식(Spring Security OAuth2 기본 저장소 사용 여부), 만료 시간, 세션 정책 세부 값은 구현 단계에서 확정합니다 (T11). 세션 정책을 무조건 완전 Stateless로 고정하지 않습니다.

### Google 계정 연동 흐름 (FR-01-10, FR-01-11, FR-01-12, FR-01-15)
1. 이메일로 로그인한 회원이 `/mypage`에서 "Google 계정 연동"을 누릅니다.
2. `GET /api/me/google/link`는 요청의 `access_token`으로 현재 회원을 확인합니다. 이 검증은 연동 시작 시점에만 합니다. 검증된 회원 ID와 흐름 종류(연동)를 OAuth2 임시 세션에 저장하고, `state` 값을 함께 보관한 뒤 Google 인증 페이지로 이동합니다. Access Token이 없거나 만료되었으면 흐름을 시작하지 않습니다.
3. 콜백에서 다음을 검증합니다. (a) 콜백의 `state`가 임시 세션에 보관된 값과 일치하고 아직 사용되지 않았는지 (CSRF 방지). (b) 임시 세션이 존재하고 만료되지 않았는지. (c) 임시 세션에 저장된 회원 ID의 회원이 존재하는지. 하나라도 실패하면 아무것도 바꾸지 않고 `/mypage?error=google_failed`로 이동합니다. 콜백은 `access_token`을 요구하지 않습니다. Google 인증 중 Access Token이 만료되어도 임시 세션이 유효하면 연동을 완료합니다.
4. `email_verified`가 아니면 `/mypage?error=google_unverified`로 이동합니다.
5. `google_sub`가 다른 회원에게 이미 연결되어 있으면 `/mypage?error=google_already_linked`로 이동합니다 (FR-01-11, 409 `GOOGLE_ALREADY_LINKED`에 해당).
6. 이미 대상 회원에게 연결되어 있으면 변경하지 않고 `/mypage?google=linked`로 이동합니다.
7. 그 밖의 경우 임시 세션에 저장된 회원의 `google_sub`에 `sub`를 저장하고 `/mypage?google=linked`로 이동합니다.
- 연동 흐름은 화면 세션의 로그인 회원을 다른 회원으로 바꾸지 않습니다. 다른 회원의 계정에 연동할 수 없습니다.
- 본인 확인 강도(추가 인증 등)는 T8에서 확정합니다.

### Google 계정 연동 해제 흐름 (FR-01-12, FR-01-13)
1. `/mypage`에서 "Google 계정 연동 해제"를 누르고 현재 비밀번호를 입력합니다.
2. `DELETE /api/me/google`이 요청을 처리합니다. JWT로 회원을 확인합니다.
3. 연동된 Google 계정이 없으면 `GOOGLE_NOT_LINKED`(404)를 반환합니다.
4. 비밀번호가 없는 회원(Google 전용 계정)이면 해제 후 로그인 수단이 없어지므로 `LAST_LOGIN_METHOD`(409)로 거부합니다.
5. 비밀번호가 틀리면 `INVALID_CREDENTIALS`(401)로 거부합니다.
6. 성공하면 `google_sub = NULL`로 저장합니다. 이후 Google 로그인은 자동 연결 없이 `email_account_exists` 흐름이 됩니다.

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
| 이메일 중복 (화면) | `/signup?error=email_duplicated` |
| 입력값 검증 실패 (API) | 400 `VALIDATION_FAILED` |
| 입력값 검증 실패 (화면) | `/signup?error=validation_failed` |
| 자격 증명 불일치 (API, Google 전용 계정 포함) | 401 `INVALID_CREDENTIALS` |
| 자격 증명 불일치 (화면, Google 전용 계정 포함) | `/login?error=invalid_credentials` |
| 토큰 없음·만료·폐기·위조 (API) | 401 `TOKEN_INVALID` |
| 세션 없음 (화면 인증 필요 페이지) | `/login`으로 리다이렉트 |
| 재인증 실패 (연동 해제) | 401 `INVALID_CREDENTIALS` |
| Google 인증 실패 | `/login?error=google_failed` |
| 기존 이메일 계정과 충돌 (자동 연결 없음) | `/login?error=email_account_exists` |
| 검증되지 않은 Google 계정 | `/login?error=google_unverified` 또는 `/mypage?error=google_unverified` |
| 다른 회원에게 연결된 Google 계정 | `/mypage?error=google_already_linked` (409 `GOOGLE_ALREADY_LINKED`) |
| 연동되지 않은 상태에서 해제 | 404 `GOOGLE_NOT_LINKED` |
| 로그인 수단이 없어지는 해제 | 409 `LAST_LOGIN_METHOD` |

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
