# API 명세 (API_SPEC)

REST API와 Thymeleaf 화면 URL을 정의합니다. 테이블 구조는 [DATABASE_ERD.md](DATABASE_ERD.md), 처리 규칙은 [FUNCTION_SPEC.md](FUNCTION_SPEC.md)를 따릅니다.

## 1. 공통 규칙

- Base URL: `https://<도메인>` (로컬: `http://localhost:8080`)
- JSON API 경로 접두사: `/api`
- 화면 경로: 접두사 없음 (Thymeleaf 렌더링)
- 날짜·시간: ISO 8601, 시간대는 서버 기준 `Asia/Seoul` 로컬 시각 (`2026-11-06T10:00:00`)
- 인증: `access_token` HttpOnly 쿠키 또는 `Authorization: Bearer <accessToken>` 헤더
- 인증 필요 항목은 표의 **인증** 열에 `필요`로 표시합니다.
- 리프레시 토큰: `refresh_token` HttpOnly 쿠키 (`/api/auth/refresh` 경로에서만 전송되도록 Path 제한)

### 1.1 공통 응답 형식

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

### 1.2 공통 오류 코드

| HTTP | code | 의미 |
|------|------|------|
| 400 | VALIDATION_FAILED | 입력값 검증 실패 |
| 400 | CATEGORY_NOT_FOUND | 존재하지 않는 카테고리 |
| 400 | IMAGE_TYPE_NOT_ALLOWED | 허용되지 않은 이미지 형식 |
| 400 | IMAGE_TOO_LARGE | 이미지 크기 초과 |
| 400 | IMAGE_LIMIT_EXCEEDED | 맛집당 이미지 개수 초과 |
| 401 | INVALID_CREDENTIALS | 이메일 또는 비밀번호 불일치 |
| 401 | TOKEN_INVALID | 토큰 만료 또는 위조 |
| 403 | FORBIDDEN | 권한 없음 (작성자 아님) |
| 404 | RESTAURANT_NOT_FOUND | 맛집 없음 |
| 404 | REVIEW_NOT_FOUND | 리뷰 없음 |
| 404 | FAVORITE_NOT_FOUND | 찜 없음 |
| 409 | EMAIL_DUPLICATED | 이메일 중복 |
| 409 | REVIEW_ALREADY_EXISTS | 같은 맛집에 리뷰 중복 |
| 409 | FAVORITE_ALREADY_EXISTS | 찜 중복 |
| 502 | STORAGE_ERROR | S3 오류 |
| 502 | AI_UNAVAILABLE | OpenRouter 호출 실패 또는 타임아웃 |
| 502 | AI_RESPONSE_INVALID | AI 응답 파싱 실패 |
| 500 | INTERNAL_ERROR | 서버 내부 오류 |

---

## 2. 인증 API (F1-01)

### 2.1 회원가입

- `POST /api/auth/signup` · 인증: 불필요

요청:
```json
{
  "email": "user@example.com",
  "password": "<8자 이상>",
  "passwordConfirm": "<동일 값>",
  "nickname": "부산먹보"
}
```

응답 `201`:
```json
{
  "success": true,
  "data": { "memberId": 1, "email": "user@example.com", "nickname": "부산먹보" },
  "timestamp": "..."
}
```

오류: 400 VALIDATION_FAILED, 409 EMAIL_DUPLICATED

### 2.2 로그인

- `POST /api/auth/login` · 인증: 불필요

요청:
```json
{ "email": "user@example.com", "password": "<비밀번호>" }
```

응답 `200`: `access_token`, `refresh_token` 쿠키 설정 + 본문
```json
{
  "success": true,
  "data": { "memberId": 1, "nickname": "부산먹보", "accessTokenExpiresIn": 1800 },
  "timestamp": "..."
}
```

오류: 401 INVALID_CREDENTIALS

### 2.3 토큰 재발급

- `POST /api/auth/refresh` · 인증: `refresh_token` 쿠키 필요

응답 `200`: 새 `access_token` 쿠키 설정, 본문은 2.2와 동일 구조.
오류: 401 TOKEN_INVALID (만료·폐기된 리프레시 토큰)

### 2.4 로그아웃

- `POST /api/auth/logout` · 인증: 필요

응답 `200`: 리프레시 토큰 폐기, 두 쿠키 만료.
```json
{ "success": true, "data": null, "timestamp": "..." }
```

### 2.5 Google 로그인 시작

- `GET /oauth2/authorization/google` · 인증: 불필요
- Spring Security OAuth2 Client가 Google 인증 페이지로 리다이렉트합니다.
- 성공 시 `GET /login/oauth2/code/google` 콜백 처리 후 `/`로 리다이렉트하고 JWT 쿠키를 설정합니다.
- 실패 시 `/login?error=google`로 리다이렉트합니다.

### 2.6 내 정보 조회

- `GET /api/auth/me` · 인증: 필요

응답 `200`:
```json
{
  "success": true,
  "data": { "memberId": 1, "email": "user@example.com", "nickname": "부산먹보", "provider": "LOCAL" },
  "timestamp": "..."
}
```

---

## 3. 카테고리 API (F1-02, F1-08)

### 3.1 카테고리 목록

- `GET /api/categories` · 인증: 불필요

응답 `200`:
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

---

## 4. 맛집 API (F1-02, F1-03)

### 4.1 맛집 목록·검색

- `GET /api/restaurants` · 인증: 불필요
- 쿼리 파라미터

| 이름 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| category | Long | - | 카테고리 ID |
| keyword | String | - | 이름·주소 검색어 (최대 50자) |
| sort | String | latest | `latest` 또는 `rating` |
| page | Integer | 0 | 0부터 시작 |
| size | Integer | 12 | 최대 50 |

응답 `200`: 목록 구조(1.1)의 `content` 항목
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

### 4.2 맛집 상세

- `GET /api/restaurants/{id}` · 인증: 불필요

응답 `200`:
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
- 오류: 404 RESTAURANT_NOT_FOUND

### 4.3 맛집 등록

- `POST /api/restaurants` · 인증: 필요

요청:
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

응답 `201`: 4.2 응답의 `id` 및 기본 정보 (`{ "id": 10 }` 포함 상세 DTO)
오류: 400 VALIDATION_FAILED, 400 CATEGORY_NOT_FOUND, 401 TOKEN_INVALID

### 4.4 맛집 수정

- `PUT /api/restaurants/{id}` · 인증: 필요 · 권한: 작성자
- 요청 본문은 4.3과 동일합니다.
- 응답 `200`: 상세 DTO
- 오류: 403 FORBIDDEN, 404 RESTAURANT_NOT_FOUND, 400 VALIDATION_FAILED

### 4.5 맛집 삭제

- `DELETE /api/restaurants/{id}` · 인증: 필요 · 권한: 작성자
- 응답 `200`: `{ "success": true, "data": null }`
- 리뷰, 찜, 이미지(S3 포함), AI 요약을 함께 삭제합니다.
- 오류: 403 FORBIDDEN, 404 RESTAURANT_NOT_FOUND

---

## 5. 맛집 이미지 API (F1-03)

### 5.1 이미지 업로드

- `POST /api/restaurants/{id}/images` · 인증: 필요 · 권한: 작성자
- Content-Type: `multipart/form-data`
- 필드: `file` (파일 1개, JPEG·PNG·WEBP, 5MB 이하)

응답 `201`:
```json
{
  "success": true,
  "data": { "id": 100, "url": "https://...", "sortOrder": 0 },
  "timestamp": "..."
}
```
- 오류: 400 IMAGE_TYPE_NOT_ALLOWED, 400 IMAGE_TOO_LARGE, 400 IMAGE_LIMIT_EXCEEDED, 502 STORAGE_ERROR

### 5.2 이미지 삭제

- `DELETE /api/restaurants/{id}/images/{imageId}` · 인증: 필요 · 권한: 작성자
- S3 객체 삭제 후 DB 행 삭제.
- 응답 `200`: `{ "success": true, "data": null }`
- 오류: 403 FORBIDDEN, 404 RESTAURANT_NOT_FOUND (이미지 없음 포함), 502 STORAGE_ERROR

---

## 6. 리뷰 API (F1-04)

### 6.1 리뷰 목록

- `GET /api/restaurants/{id}/reviews` · 인증: 불필요
- 쿼리: `page` (기본 0), `size` (기본 10, 최대 50)
- 정렬: 최신순 고정

응답 `200`: 목록 구조(1.1)의 `content` 항목
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

### 6.2 리뷰 작성

- `POST /api/restaurants/{id}/reviews` · 인증: 필요

요청:
```json
{ "rating": 5, "content": "국물이 진하고 좋아요." }
```

응답 `201`:
```json
{
  "success": true,
  "data": { "id": 200, "rating": 5, "content": "국물이 진하고 좋아요.", "restaurantAverageRating": 4.7, "restaurantReviewCount": 13 },
  "timestamp": "..."
}
```
- 부작용: 맛집 평균 평점·리뷰 수 갱신, AI 요약 캐시 삭제 (F1-05).
- 오류: 400 VALIDATION_FAILED, 404 RESTAURANT_NOT_FOUND, 409 REVIEW_ALREADY_EXISTS

### 6.3 리뷰 삭제

- `DELETE /api/reviews/{reviewId}` · 인증: 필요 · 권한: 리뷰 작성자
- 응답 `200`:
```json
{
  "success": true,
  "data": { "restaurantAverageRating": 4.5, "restaurantReviewCount": 12 },
  "timestamp": "..."
}
```
- 부작용: 평균 평점·리뷰 수 갱신, AI 요약 캐시 삭제.
- 오류: 403 FORBIDDEN, 404 REVIEW_NOT_FOUND

---

## 7. AI 리뷰 요약 API (F1-05)

### 7.1 맛집 AI 요약 조회

- `GET /api/restaurants/{id}/summary` · 인증: 불필요

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

---

## 8. 찜 API (F1-06)

### 8.1 찜하기

- `POST /api/restaurants/{id}/favorite` · 인증: 필요

응답 `201`: `{ "success": true, "data": { "restaurantId": 10, "favorited": true, "favoriteCount": 4 }, "timestamp": "..." }`
- 오류: 404 RESTAURANT_NOT_FOUND, 409 FAVORITE_ALREADY_EXISTS

### 8.2 찜 취소

- `DELETE /api/restaurants/{id}/favorite` · 인증: 필요

응답 `200`: `{ "success": true, "data": { "restaurantId": 10, "favorited": false, "favoriteCount": 3 }, "timestamp": "..." }`
- 오류: 404 FAVORITE_NOT_FOUND

### 8.3 내 찜 목록

- `GET /api/me/favorites` · 인증: 필요
- 쿼리: `page` (기본 0), `size` (기본 12, 최대 50)
- 정렬: 찜한 시각 내림차순
- 응답 `200`: 목록 구조(1.1), `content` 항목은 맛집 목록 항목(4.1)과 같음

---

## 9. AI 맛집 추천 API (F1-07)

### 9.1 추천 요청

- `POST /api/recommendations` · 인증: 필요

요청:
```json
{ "preference": "2명이서 조용히 먹을 매운 국물 요리" }
```

응답 `200`:
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
- 후보가 없으면 `recommendations: []`, `message: "등록된 맛집이 없어 추천할 수 없습니다."`.
- 오류: 400 VALIDATION_FAILED (1~200자), 502 AI_UNAVAILABLE, 502 AI_RESPONSE_INVALID

---

## 10. 화면 URL (Thymeleaf)

| 경로 | 메서드 | 인증 | 설명 | 관련 기능 |
|------|--------|------|------|-----------|
| `/` | GET | 불필요 | 메인 페이지 (카테고리, 최신 맛집, 검색창) | F1-08 |
| `/login` | GET | 불필요 | 로그인 폼 | F1-01 |
| `/signup` | GET | 불필요 | 회원가입 폼 | F1-01 |
| `/restaurants` | GET | 불필요 | 맛집 목록·검색 (`category`, `keyword`, `sort`, `page`) | F1-02 |
| `/restaurants/{id}` | GET | 불필요 | 맛집 상세 (이미지, 리뷰, AI 요약, 찜 버튼) | F1-02, F1-04, F1-05, F1-06 |
| `/restaurants/new` | GET | 필요 | 맛집 등록 폼 | F1-02 |
| `/restaurants/{id}/edit` | GET | 필요 · 작성자 | 맛집 수정 폼 및 이미지 관리 | F1-02, F1-03 |
| `/favorites` | GET | 필요 | 내 찜 목록 | F1-06 |
| `/recommend` | GET | 필요 | AI 추천 입력 및 결과 | F1-07 |

- 화면 폼 제출(등록, 수정, 리뷰, 찜)은 위 REST API를 `fetch`로 호출하거나 일반 폼 POST(Spring MVC 컨트롤러)로 처리합니다. 구현 방식은 `DEVELOPMENT_RULES.md`의 "화면 구현 방식" 절을 따릅니다.

---

## 11. 엔드포인트 요약

| 메서드 | 경로 | 인증 | 기능 |
|--------|------|------|------|
| POST | /api/auth/signup | 불필요 | F1-01 |
| POST | /api/auth/login | 불필요 | F1-01 |
| POST | /api/auth/refresh | 리프레시 쿠키 | F1-01 |
| POST | /api/auth/logout | 필요 | F1-01 |
| GET | /oauth2/authorization/google | 불필요 | F1-01 |
| GET | /api/auth/me | 필요 | F1-01 |
| GET | /api/categories | 불필요 | F1-02, F1-08 |
| GET | /api/restaurants | 불필요 | F1-02 |
| GET | /api/restaurants/{id} | 불필요 | F1-02 |
| POST | /api/restaurants | 필요 | F1-02 |
| PUT | /api/restaurants/{id} | 필요 · 작성자 | F1-02 |
| DELETE | /api/restaurants/{id} | 필요 · 작성자 | F1-02 |
| POST | /api/restaurants/{id}/images | 필요 · 작성자 | F1-03 |
| DELETE | /api/restaurants/{id}/images/{imageId} | 필요 · 작성자 | F1-03 |
| GET | /api/restaurants/{id}/reviews | 불필요 | F1-04 |
| POST | /api/restaurants/{id}/reviews | 필요 | F1-04 |
| DELETE | /api/reviews/{reviewId} | 필요 · 작성자 | F1-04 |
| GET | /api/restaurants/{id}/summary | 불필요 | F1-05 |
| POST | /api/restaurants/{id}/favorite | 필요 | F1-06 |
| DELETE | /api/restaurants/{id}/favorite | 필요 | F1-06 |
| GET | /api/me/favorites | 필요 | F1-06 |
| POST | /api/recommendations | 필요 | F1-07 |
