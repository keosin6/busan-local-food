# 데이터베이스 ERD

대상 DBMS: **MySQL 8** (InnoDB, utf8mb4 / utf8mb4_unicode_ci)
운영 위치: **AWS EC2 내부 설치** (RDS 미사용, Q4)

이 문서의 테이블, 컬럼, 제약은 [API_SPEC.md](API_SPEC.md)의 요청·응답 필드 및 [FUNCTION_SPEC.md](FUNCTION_SPEC.md)의 처리 흐름과 일치해야 합니다. 변경할 때는 세 문서를 함께 수정합니다.

## 1. ERD (Mermaid)

```mermaid
erDiagram
    MEMBER ||--o{ RESTAURANT : "creates"
    CATEGORY ||--o{ RESTAURANT : "classifies"
    RESTAURANT ||--o{ RESTAURANT_IMAGE : "has"
    RESTAURANT ||--o{ REVIEW : "receives"
    MEMBER ||--o{ REVIEW : "writes"
    RESTAURANT ||--o{ FAVORITE : "is favorited in"
    MEMBER ||--o{ FAVORITE : "marks"
    RESTAURANT ||--o| AI_REVIEW_SUMMARY : "caches"
    MEMBER ||--o{ REFRESH_TOKEN : "owns"

    MEMBER {
        bigint id PK
        varchar email UK
        varchar password_hash "NULL 허용 (Google 전용 계정)"
        varchar nickname UK
        varchar role "USER | ADMIN"
        varchar google_sub UK "NULL 허용 (Google 연동 시 sub)"
        datetime created_at
        datetime updated_at
    }

    CATEGORY {
        bigint id PK
        varchar name UK
        int sort_order
    }

    RESTAURANT {
        bigint id PK
        bigint member_id FK "작성자"
        bigint category_id FK
        varchar name
        varchar address
        varchar phone "NULL 허용"
        text description
        decimal latitude "NULL 허용"
        decimal longitude "NULL 허용"
        decimal average_rating "0.0 ~ 5.0"
        int review_count
        datetime created_at
        datetime updated_at
    }

    RESTAURANT_IMAGE {
        bigint id PK
        bigint restaurant_id FK
        varchar s3_key UK
        varchar original_filename
        varchar content_type
        int file_size
        int sort_order
        datetime created_at
    }

    REVIEW {
        bigint id PK
        bigint restaurant_id FK
        bigint member_id FK
        tinyint rating "1 ~ 5"
        varchar content "최대 500자"
        datetime created_at
        datetime updated_at
    }

    FAVORITE {
        bigint id PK
        bigint member_id FK
        bigint restaurant_id FK
        datetime created_at
    }

    AI_REVIEW_SUMMARY {
        bigint id PK
        bigint restaurant_id FK,UK
        text summary
        int source_review_count
        datetime created_at
    }

    REFRESH_TOKEN {
        bigint id PK
        bigint member_id FK
        char token_hash UK "SHA-256 hex"
        char family_id "계열 UUID (ascii_bin, NOT NULL, 회전 시 유지)"
        datetime expires_at
        datetime revoked_at "NULL 허용"
        datetime created_at
    }

    STORAGE_DELETE_TASK {
        bigint id PK
        varchar s3_key UK "FK 없음 (맛집 삭제 후에도 유지)"
        int retry_count
        varchar last_error "NULL 허용"
        datetime created_at
        datetime updated_at
    }

    LOGIN_FAILURE_EVENT {
        bigint id PK
        char pair_key_hash "HMAC-SHA256 hex (ascii_bin)"
        char ip_key "HMAC-SHA256 hex (ascii_bin)"
        datetime failed_at
    }

    SIGNUP_REQUEST_EVENT {
        bigint id PK
        char ip_key "HMAC-SHA256 hex (ascii_bin)"
        datetime requested_at
    }

    REFRESH_REQUEST_EVENT {
        bigint id PK
        char family_id "계열 UUID (ascii_bin), FK 없음"
        datetime requested_at
    }

    GOOGLE_OAUTH_REQUEST_EVENT {
        bigint id PK
        char ip_key "HMAC-SHA256 hex (ascii_bin)"
        datetime requested_at
    }

    AUTH_RATE_LIMIT_COUNTER {
        char counter_key PK "SHA-256 hex (ascii_bin), 잠금 행"
        datetime updated_at
    }
```

> `STORAGE_DELETE_TASK`는 다른 테이블과 FK로 연결하지 않습니다. 맛집이 삭제된 뒤에도 S3 객체 키를 보관해야 하기 때문입니다.

## 2. 테이블 정의

### 2.1 member (회원)

| 컬럼 | 타입 | NULL | 기본값 | 설명 |
|------|------|------|--------|------|
| id | BIGINT | N | AUTO_INCREMENT | PK |
| email | VARCHAR(255) | N | | 로그인 ID, 소문자 저장 |
| password_hash | VARCHAR(100) | Y | | BCrypt 해시. Google 전용 계정은 NULL |
| nickname | VARCHAR(20) | N | | 2~20자. 중복 불가 (`uk_member_nickname`) |
| role | VARCHAR(20) | N | 'USER' | `USER`(일반 회원) 또는 `ADMIN`(관리자) |
| google_sub | VARCHAR(255) | Y | | 연동된 Google 계정의 `sub`. 연동하지 않았으면 NULL |
| created_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) | |
| updated_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) ON UPDATE | |

- UNIQUE: `uk_member_email (email)`
- UNIQUE: `uk_member_nickname (nickname)` — 닉네임 중복 방지 (FR-01-01, FR-01-23). 애플리케이션의 사전 중복 검사는 동시 요청을 완전히 막을 수 없으므로, 최종 중복 방어는 이 제약입니다. 저장 시 위반이 발생하면 서비스 계층에서 `NICKNAME_DUPLICATED`(API 409) 또는 화면 `nickname_duplicated`로 변환합니다 (DEVELOPMENT_RULES 6.4). 중복 판정의 대소문자·악센트 구분은 테이블 collation을 따르며, 구현 단계에서 확인합니다.
- UNIQUE: `uk_member_google_sub (google_sub)` — 한 Google 계정은 한 회원에만 연결 (FR-01-11). MySQL UNIQUE는 NULL 중복을 허용하므로 미연동 회원은 여러 명일 수 있습니다.
- CHECK: `ck_member_role` — `role IN ('USER','ADMIN')`
- 로그인 수단 규칙: `password_hash`와 `google_sub` 중 최소 하나는 NOT NULL이어야 합니다 (FR-01-13). 애플리케이션에서 검증하고, 조건을 DB CHECK로도 둡니다: `ck_member_login_method` — `CHECK (password_hash IS NOT NULL OR google_sub IS NOT NULL)`. 이 조건은 `db/manual/001_create_member_refresh_token.sql`과 일치합니다.
- `role`은 일반 회원 API로 변경할 수 없습니다 (FR-09-09). 변경 경로는 T2에서 정합니다.

#### 변경 내역 (Q1, Q5)

| 구분 | 이전 | 현재 | 이유 |
|------|------|------|------|
| 삭제 | `provider` (LOCAL/GOOGLE) | - | 자동 연동을 하지 않고 한 회원이 이메일 로그인과 Google 연동을 함께 가질 수 있으므로 "가입 방식" 단일 값으로는 표현이 맞지 않음 |
| 삭제 | `provider_id` (Google sub) | `google_sub` | 이름을 역할에 맞게 바꾸고, 중복 연결 방지 UNIQUE를 직접 건다 |
| 추가 | - | `role` | 관리자 구분 (Q5). 별도 관리자 테이블은 만들지 않음 |

- 제거된 `uk_member_provider (provider, provider_id)`는 `uk_member_google_sub`로 대체합니다.

### 2.2 category (카테고리)

| 컬럼 | 타입 | NULL | 기본값 | 설명 |
|------|------|------|--------|------|
| id | BIGINT | N | AUTO_INCREMENT | PK |
| name | VARCHAR(30) | N | | 카테고리명 |
| sort_order | INT | N | 0 | 화면 표시 순서 |

- UNIQUE: `uk_category_name (name)`
- 시드 데이터: 한식, 중식, 일식, 양식, 분식, 카페·디저트, 해산물, 고기·구이, 술집, 기타

### 2.3 restaurant (맛집)

| 컬럼 | 타입 | NULL | 기본값 | 설명 |
|------|------|------|--------|------|
| id | BIGINT | N | AUTO_INCREMENT | PK |
| member_id | BIGINT | N | | 작성자 (FK → member.id) |
| category_id | BIGINT | N | | FK → category.id |
| name | VARCHAR(50) | N | | 맛집 이름 |
| address | VARCHAR(200) | N | | 주소 |
| phone | VARCHAR(20) | Y | | 숫자와 하이픈 |
| description | TEXT | N | | 설명, 최대 2000자 (애플리케이션 검증) |
| latitude | DECIMAL(10,7) | Y | | 위도 |
| longitude | DECIMAL(10,7) | Y | | 경도 |
| average_rating | DECIMAL(2,1) | N | 0.0 | 평균 평점 |
| review_count | INT | N | 0 | 리뷰 수 |
| created_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) | |
| updated_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) ON UPDATE | |

- FK: `fk_restaurant_member (member_id) → member(id) ON DELETE RESTRICT`
- FK: `fk_restaurant_category (category_id) → category(id) ON DELETE RESTRICT`
- INDEX: `idx_restaurant_category (category_id, created_at)` — 카테고리 목록·최신순
- INDEX: `idx_restaurant_created (created_at)` — 메인 최신 맛집
- INDEX: `idx_restaurant_name (name)` — 이름 검색 보조 (LIKE '%…%'는 풀 스캔이 될 수 있으므로 규모가 커지면 FULLTEXT 검토)
- CHECK: `average_rating BETWEEN 0 AND 5`, `review_count >= 0`

> 권한 규칙 (FR-02-03, FR-09-02): 일반 맛집 수정·삭제 API는 `member_id`가 요청자와 같을 때만 허용합니다. 관리자는 F1-09의 관리자 API로만 다른 회원의 맛집을 수정·삭제합니다.

### 2.4 restaurant_image (맛집 이미지)

| 컬럼 | 타입 | NULL | 기본값 | 설명 |
|------|------|------|--------|------|
| id | BIGINT | N | AUTO_INCREMENT | PK |
| restaurant_id | BIGINT | N | | FK → restaurant.id |
| s3_key | VARCHAR(500) | N | | S3 객체 키 |
| original_filename | VARCHAR(255) | N | | 업로드 원본 파일명 |
| content_type | VARCHAR(50) | N | | `image/jpeg`, `image/png`, `image/webp` |
| file_size | INT | N | | 바이트 단위, 5MB 이하 |
| sort_order | INT | N | 0 | 표시 순서 |
| created_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) | |

- FK: `fk_image_restaurant (restaurant_id) → restaurant(id) ON DELETE CASCADE`
- UNIQUE: `uk_image_s3_key (s3_key)`
- INDEX: `idx_image_restaurant (restaurant_id, sort_order)`
- 삭제 규칙: 이 행을 삭제할 때는 같은 트랜잭션에서 `storage_delete_task`에 `s3_key`를 기록합니다. S3 객체 삭제는 커밋 후 수행합니다 (FUNCTION_SPEC F1-03).
- 구현 메모 (SQL 005): 명세에 없던 CHECK를 추가했습니다. `ck_image_file_size` (1~5242880), `ck_image_content_type` (`image/jpeg`, `image/png`, `image/webp`), `ck_image_sort_order` (0 이상).

### 2.5 review (리뷰)

| 컬럼 | 타입 | NULL | 기본값 | 설명 |
|------|------|------|--------|------|
| id | BIGINT | N | AUTO_INCREMENT | PK |
| restaurant_id | BIGINT | N | | FK → restaurant.id |
| member_id | BIGINT | N | | FK → member.id |
| rating | TINYINT | N | | 1~5 정수 |
| content | VARCHAR(500) | N | | 리뷰 내용 |
| created_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) | |
| updated_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) ON UPDATE | |

- FK: `fk_review_restaurant (restaurant_id) → restaurant(id) ON DELETE CASCADE`
- FK: `fk_review_member (member_id) → member(id) ON DELETE RESTRICT` (구현 시 변경. 명세 원안은 CASCADE)
- UNIQUE: `uk_review_member_restaurant (restaurant_id, member_id)` — 한 회원당 맛집 리뷰 1개 (FR-04-02)
- INDEX: `idx_review_restaurant_created (restaurant_id, created_at)`
- CHECK: `rating BETWEEN 1 AND 5`

> 삭제 권한 (FR-04-03): 회원 삭제 API는 리뷰 작성자 본인만 허용합니다. 관리자 삭제는 F1-09에서 별도 API로 제공합니다.

> 구현 메모 (SQL 006): 명세의 컬럼, UNIQUE, CHECK, INDEX를 그대로 구현했습니다. FK 정책은 두 가지를 정했습니다. `fk_review_restaurant`은 CASCADE이며, 맛집 삭제 트랜잭션이 리뷰를 먼저 명시적으로 지우므로 안전장치입니다. `fk_review_member`는 명세 원안 CASCADE에서 RESTRICT로 바꿨습니다. 리뷰가 남은 회원을 DB가 지우면 맛집의 평균과 리뷰 수가 리뷰 행과 어긋나기 때문입니다. 회원 탈퇴 정책(T1)이 정해지면 서비스가 리뷰를 지우고 집계를 다시 계산한 뒤 회원을 지웁니다.

### 2.6 favorite (찜)

| 컬럼 | 타입 | NULL | 기본값 | 설명 |
|------|------|------|--------|------|
| id | BIGINT | N | AUTO_INCREMENT | PK |
| member_id | BIGINT | N | | FK → member.id |
| restaurant_id | BIGINT | N | | FK → restaurant.id |
| created_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) | |

- FK: `fk_favorite_member (member_id) → member(id) ON DELETE CASCADE`
- FK: `fk_favorite_restaurant (restaurant_id) → restaurant(id) ON DELETE CASCADE`
- UNIQUE: `uk_favorite_member_restaurant (member_id, restaurant_id)` — 중복 찜 방지 (FR-06-02)
- INDEX: `idx_favorite_member_created (member_id, created_at)` — 내 찜 목록

> 구현 메모 (SQL 008): 명세 컬럼, FK 두 개(CASCADE), UNIQUE, 내 찜 목록 인덱스를 그대로 구현했습니다. 맛집별 찜 수 조회를 위해 `idx_favorite_restaurant (restaurant_id)`를 추가했습니다(FK 제약에도 쓰입니다). 맛집을 지우면 찜이 DB CASCADE로, 맛집 삭제 트랜잭션에서도 지워집니다. 회원을 지우면 그 회원의 찜이 CASCADE로 지워집니다.

### 2.7 ai_review_summary (AI 리뷰 요약 캐시)

| 컬럼 | 타입 | NULL | 기본값 | 설명 |
|------|------|------|--------|------|
| id | BIGINT | N | AUTO_INCREMENT | PK |
| restaurant_id | BIGINT | N | | FK → restaurant.id, 맛집당 1행 |
| summary | TEXT | N | | AI 생성 요약 |
| source_review_count | INT | N | | 요약 생성 시점의 리뷰 수 |
| created_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) | 생성 시각 |

- FK: `fk_summary_restaurant (restaurant_id) → restaurant(id) ON DELETE CASCADE`
- UNIQUE: `uk_summary_restaurant (restaurant_id)`
- 무효화: 리뷰 작성·삭제 시 해당 행 DELETE (FUNCTION_SPEC F1-05). 회원 탈퇴(T1)와 관리자 삭제(F1-09)의 무효화는 그 기능을 구현할 때 추가한다.

> 구현 메모 (SQL 007): 명세 컬럼, FK, UNIQUE를 그대로 구현했습니다. 추가로 `ck_summary_review_count` (`source_review_count >= 3`)를 두었습니다. 3개 미만 맛집은 캐시하지 않습니다. 무효화는 리뷰 작성·삭제 트랜잭션이 맛집 행을 잠근 뒤 같은 트랜잭션에서 행을 지우는 방식입니다.

### 2.8 refresh_token (리프레시 토큰)

| 컬럼 | 타입 | NULL | 기본값 | 설명 |
|------|------|------|--------|------|
| id | BIGINT | N | AUTO_INCREMENT | PK |
| member_id | BIGINT | N | | FK → member.id |
| token_hash | CHAR(64) | N | | Refresh Token의 SHA-256 해시 (원문 저장 금지) |
| family_id | CHAR(36) ascii_bin | N | | 토큰 계열 식별 UUID (가명 식별자, SQL 002로 추가). 서버가 생성한다. 기존 행은 마이그레이션(SQL 002) 시 MySQL UUID v1을 행마다 개별로 받았다(일회성 백필). 신규 계열 ID는 애플리케이션이 UUID v4로 생성하며, 기본값이 없으므로 INSERT 때 반드시 값을 넣는다. 직접 식별 정보는 없지만 회원 및 인증 세션과 연결될 수 있으므로 외부 노출을 막는다. 같은 로그인 세션의 회전에서는 같은 값을 유지하고, 새 기기 로그인에서는 새로 생성한다 (FR-01-36) |
| expires_at | DATETIME(6) | N | | 만료 시각 |
| revoked_at | DATETIME(6) | Y | | 로그아웃·재발급 등으로 폐기된 시각 |
| created_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) | |

- FK: `fk_refresh_member (member_id) → member(id) ON DELETE CASCADE`
- UNIQUE: `uk_refresh_token_hash (token_hash)`
- INDEX: `idx_refresh_member (member_id, revoked_at)`
- INDEX: `idx_refresh_family (family_id)`. 계열 단위 요청 제한 조회용 (SQL 002 확정).
- 계열(`family_id`): 회전할 때 기존 행의 값을 새 행에 그대로 복사한다. 새 기기 로그인은 새 UUID를 만든다. 신규 `family_id`는 애플리케이션이 생성하는 UUID v4다. 기존 행의 백필 값은 MySQL UUID v1이며 일회성 마이그레이션에만 쓴다. 직접적인 개인 식별 정보는 포함하지 않지만, 회원 및 인증 세션과 연결될 수 있는 가명 식별자다. 따라서 개인정보 및 보안 정보에 준하여 접근을 통제하고 로그를 관리하며, 원본 Refresh Token과 마찬가지로 외부 API 응답, 클라이언트 데이터, 일반 애플리케이션 로그에 노출하지 않는다. 요청 제한의 키로도 쓴다 (API_SPEC 1.4). 기존 계열 구조와 T7·T14 정책은 바뀌지 않는다.
- 원문 저장 위치: 브라우저의 HttpOnly Cookie만 (FR-01-05). DB에는 해시만 저장합니다.
- 로테이션 (FR-01-06): 재발급 시 기존 행의 `revoked_at`을 기록하고 새 행을 INSERT합니다. 행을 물리 삭제하지 않습니다. 만료되거나 폐기된 행을 정리하는 방식은 구현 단계에서 정합니다.
- 재사용 탐지 (T7 A안 확정): 폐기된 Refresh Token의 재사용이 실제로 탐지되면 해당 회원의 모든 활성 Refresh Token(`revoked_at IS NULL`)을 폐기합니다. 원문은 저장하지 않고 SHA-256 해시만 저장합니다. 폐기된 행은 재사용 탐지를 위해 보존합니다.
- 동시 갱신 (T14 A안 확정): 재발급과 재사용 탐지는 회원 행 잠금 안에서 같은 순서로 처리합니다 (DEVELOPMENT_RULES 5.7.4). 여러 탭의 갱신은 브라우저의 Web Locks API로 조정합니다. 이 결정은 테이블 구조를 바꾸지 않으며, 유예 창이나 새 컬럼을 추가하지 않습니다.
- 네트워크 응답 유실로 인한 정상 사용자의 재로그인은 A안의 수용된 한계입니다.

### 2.9 storage_delete_task (S3 삭제 추적) — 신규

| 컬럼 | 타입 | NULL | 기본값 | 설명 |
|------|------|------|--------|------|
| id | BIGINT | N | AUTO_INCREMENT | PK |
| s3_key | VARCHAR(500) | N | | 삭제해야 할 S3 객체 키 |
| retry_count | INT | N | 0 | S3 삭제 시도 실패 횟수 |
| last_error | VARCHAR(500) | Y | | 마지막 실패 메시지 (비밀 정보 제외) |
| created_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) | 삭제 대기 등록 시각 |
| updated_at | DATETIME(6) | N | CURRENT_TIMESTAMP(6) ON UPDATE | 마지막 시도 시각 |

- UNIQUE: `uk_storage_task_s3_key (s3_key)`
- INDEX: `idx_storage_task_created (created_at)` — 관리자 목록 정렬
- 행의 의미: "DB에서는 이미 삭제되었지만 S3 객체는 아직 지워지지 않은 키". S3 삭제에 성공하면 행을 삭제합니다.
- 생성 규칙: 맛집 삭제·이미지 삭제 트랜잭션 안에서 INSERT합니다. 이렇게 하면 DB 커밋 후 프로세스가 중단되어도 키가 남습니다.
- 재시도: 관리자 API로 수동 재시도합니다 (FR-03-06, FR-09-06). 자동 재시도 여부는 T6에서 정합니다.
- 구현 메모 (SQL 005): 명세에 없던 CHECK `ck_storage_task_retry` (`retry_count >= 0`)를 추가했습니다. 나머지 컬럼, UNIQUE, INDEX는 명세와 같습니다.

#### 추가 이유

S3 삭제 실패 키를 추적하고 재시도하려면 영속 기록이 필요합니다. 기존 테이블 중에는 삭제가 끝난 `restaurant_image` 행을 이 용도로 쓸 수 없고(행이 이미 사라짐), 맛집이 삭제되면 같이 사라지는 테이블도 적합하지 않습니다. 따라서 Q2 구현에 필요한 최소 테이블 1개만 추가합니다.

### 2.10 요청 제한 이벤트와 카운터 (확정 스키마)

요청 제한(API_SPEC 1.4, FR-01-28~FR-01-35)의 테이블은 `db/manual/003_create_auth_rate_limit_tables.sql`로 생성합니다. 아래 컬럼, 타입, NULL, PK, 인덱스는 해당 SQL과 일치합니다. 이 절에 원문 이메일, 원문 IP, 원문 Refresh Token을 저장하는 컬럼은 없습니다. 모든 테이블은 `ENGINE=InnoDB`, `utf8mb4_unicode_ci`를 사용하며, 해시와 계열 값 컬럼은 `ascii_bin`입니다. FK는 두지 않습니다.

| 테이블 | 컬럼 (타입, NULL) | PK | 인덱스 | 역할 |
|--------|-------------------|----|--------|------|
| `login_failure_event` | `id` BIGINT N, `pair_key_hash` CHAR(64) N, `ip_key` CHAR(64) N, `failed_at` DATETIME(6) N | `id` | `idx_login_failure_pair_time (pair_key_hash, failed_at)`, `idx_login_failure_ip_time (ip_key, failed_at)`, `idx_login_failure_time (failed_at)` | 로그인 실패 이벤트. 요청 실패 1건당 1행 |
| `signup_request_event` | `id` BIGINT N, `ip_key` CHAR(64) N, `requested_at` DATETIME(6) N | `id` | `idx_signup_request_ip_time (ip_key, requested_at)`, `idx_signup_request_time (requested_at)` | 회원가입 요청 이벤트. JSON 파싱과 DTO 검증 이전 집계 (T15) |
| `refresh_request_event` | `id` BIGINT N, `family_id` CHAR(36) N, `requested_at` DATETIME(6) N | `id` | `idx_refresh_request_family_time (family_id, requested_at)`, `idx_refresh_request_time (requested_at)` | Refresh 요청 이벤트. 계열을 확인할 수 있는 요청만 기록. `refresh_token.family_id`를 논리적으로 참조하며 FK 없음 |
| `google_oauth_request_event` | `id` BIGINT N, `ip_key` CHAR(64) N, `requested_at` DATETIME(6) N | `id` | `idx_google_oauth_ip_time (ip_key, requested_at)`, `idx_google_oauth_time (requested_at)` | Google OAuth2 요청 이벤트. 로그인 시작, 계정 연동 시작, 콜백 각 1회 |
| `auth_rate_limit_counter` | `counter_key` CHAR(64) N, `updated_at` DATETIME(6) N (기본값 CURRENT_TIMESTAMP(6), 갱신 시 자동 갱신) | `counter_key` | 추가 인덱스 없음 | 동일 키 요청을 직렬화하는 잠금 행. 횟수를 저장하지 않음 |

개인정보와 키 규칙 (R2, R7, T16 확정):
- 이벤트 테이블에는 원문 이메일과 원문 IP를 저장하지 않습니다.
- 이메일+IP 조합(`pair_key_hash`)과 IP 식별자(`ip_key`)는 HMAC-SHA256 값입니다. 값은 소문자 64자리 hex입니다. 이 형식은 애플리케이션이 보장하며, DB CHECK 제약은 두지 않습니다.
- HMAC 비밀키는 환경변수로 공급하고 저장소에 커밋하지 않습니다. 키를 교체하면 기존 키로 만든 값은 새 키로 조회되지 않으므로, 교체 전에 정리합니다 (DEVELOPMENT_RULES 5.8).
- `counter_key`는 키 종류 접두어와 식별값을 결합한 문자열의 SHA-256 해시입니다. 접두어는 `LOGIN_PAIR:`, `LOGIN_IP:`, `SIGNUP_IP:`, `REFRESH_FAMILY:`, `GOOGLE_OAUTH_IP:`입니다.
- Refresh 계열(`family_id`)은 HMAC을 쓰지 않습니다. 가명 식별자이므로 접근을 통제합니다 (DEVELOPMENT_RULES 5.3).

보존 규칙 (R3, T18 확정):
- 로그인 실패, 회원가입, Refresh, Google OAuth2 이벤트는 모두 생성 후 24시간 보관한 뒤 삭제합니다.
- 15분, 1시간, 1분 제한 시간을 벗어났다는 이유만으로 즉시 삭제하지 않습니다.
- 정리는 24시간 보존 기준으로만 수행합니다. 요청 시 정리와 Spring Scheduling 기반 정리를 병행합니다. 한 번에 삭제하는 행 수 제한은 구현 단계에서 설계합니다.

동시성 규칙 (구현 단계 적용):
- 제한 판단의 원천은 이벤트 테이블입니다. `auth_rate_limit_counter`는 횟수 저장용이 아니라 같은 키 요청을 직렬화하는 잠금 행입니다.
- 단순 `SELECT COUNT(*)` 후 `INSERT`로 제한을 구현하지 않습니다.
- 여러 카운터 키를 잠글 때는 `counter_key`의 사전순으로 잠가 교착 상태를 피합니다.
- 잠금 행은 `INSERT ... ON DUPLICATE KEY UPDATE`로 확보하고, 같은 트랜잭션에서 `SELECT ... FOR UPDATE`로 잠급니다.
- 잠금 상태에서 기간 안의 이벤트 수를 세고, 한도 미만이면 이벤트 한 건을 기록하고 커밋합니다. 한도에 도달하면 이벤트를 기록하지 않고 `429`를 반환합니다.
- 카운터 행의 자동 삭제는 구현하지 않습니다. 잠금 대기 중인 트랜잭션과 경합하면 동시성 보장이 깨지므로, 안전한 정리 절차는 별도로 설계합니다.

## 3. 관계 요약

| 관계 | 카디널리티 | 삭제 정책 |
|------|-----------|-----------|
| member → restaurant | 1 : N | 회원 삭제는 제한 (RESTRICT). 탈퇴 정책은 T1 |
| category → restaurant | 1 : N | 사용 중인 카테고리 삭제 제한 (RESTRICT) |
| restaurant → restaurant_image | 1 : N | CASCADE. S3 객체는 `storage_delete_task`를 거쳐 애플리케이션이 삭제 |
| restaurant → review | 1 : N | CASCADE |
| member → review | 1 : N | RESTRICT (리뷰가 남은 회원 삭제 제한, 맛집 집계 정합성. 탈퇴 절차는 T1에서 확정) |
| restaurant ↔ member (favorite) | N : M | CASCADE |
| restaurant → ai_review_summary | 1 : 0..1 | CASCADE |
| member → refresh_token | 1 : N | CASCADE |
| storage_delete_task | 독립 | FK 없음. 맛집 삭제 후에도 유지 |

## 4. 데이터 정리 규칙

### 4.1 맛집 삭제 시 데이터 정리 (Q2)

DB 내부 정리는 한 트랜잭션에서 수행하고, S3 삭제는 커밋 후에 수행합니다.

```
[DB 트랜잭션]
  1. restaurant 행 잠금 조회 (PESSIMISTIC_WRITE, 없으면 404), restaurant_image의 s3_key 목록 조회
  2. storage_delete_task에 각 s3_key INSERT
  3. ai_review_summary DELETE (restaurant_id)
  4. favorite DELETE (restaurant_id)
  5. review DELETE (restaurant_id)
  6. restaurant_image DELETE (restaurant_id)
  7. restaurant DELETE (id)
  COMMIT
[커밋 후, DB 트랜잭션 밖]
  8. 각 s3_key에 대해 S3 객체 삭제
     - 성공: storage_delete_task 행 DELETE
     - 실패: storage_delete_task.retry_count + 1, last_error 기록
```

- 2번 단계로 DB 행과 S3 키가 같은 커밋에 묶입니다. 3~7번 단계의 FK CASCADE는 안전장치로 함께 둡니다.
- 8번 단계가 실패해도 맛집 삭제는 이미 커밋되었으므로 되돌리지 않습니다.
- 이 흐름은 두 저장소의 원자성을 보장하지 않습니다. 실패는 `storage_delete_task`로 추적·재시도합니다.
- 동시성 (PR #39): 1번 단계의 잠금은 맛집 행에 겁니다. 이미지 업로드와 이미지 개별 삭제도 같은 행을 잠근 뒤 진행하므로, 커밋되지 않은 업로드가 목록에서 빠져 S3 객체가 고아로 남는 일을 막습니다.

### 4.2 집계 값 관리

- `restaurant.review_count`와 `restaurant.average_rating`은 리뷰 작성·삭제 트랜잭션 안에서 갱신합니다.
- 갱신 방식: `UPDATE restaurant SET review_count = review_count + 1, average_rating = ... WHERE id = ?` 또는 재계산 쿼리. 구현 시 동시성 테스트를 작성합니다.
- 구현 (F1-04): 재계산 쿼리를 씁니다. 작성·삭제 트랜잭션은 맛집 행을 `PESSIMISTIC_WRITE`로 잠근 뒤 리뷰 행의 개수와 평점 합을 세고, 평균은 합계 나누기 개수를 소수 첫째 자리에서 반올림합니다. 리뷰가 없으면 0.0입니다. 동시성 테스트는 `ReviewConcurrencyDbTest`입니다.
- 평균 평점은 `ROUND(AVG(rating), 1)`과 같은 규칙(소수점 첫째 자리 반올림)을 따릅니다.

## 5. 관리자 기능의 데이터 구조

- 관리자 전용 테이블은 두지 않습니다. 관리자 여부는 `member.role = 'ADMIN'`으로 판단합니다 (FR-09-01).
- 관리자 화면의 목록과 집계(대시보드)는 기존 테이블에 대한 조회 쿼리로 구현합니다.
  - 회원 수: `member`
  - 맛집 수, 최근 7일 신규 맛집: `restaurant`
  - 리뷰 수, 최근 7일 신규 리뷰: `review`
  - S3 삭제 재시도 대기 수: `storage_delete_task`
- 회원 권한 변경 경로는 이번 ERD에 포함하지 않습니다. 정책은 T2에서 정합니다.

## 6. 마이그레이션 규칙

- 스키마 변경은 `db/manual/` 아래 번호가 붙은 SQL 파일로 관리하며, 사람이 대상 DB에 직접 실행합니다. Flyway 등 마이그레이션 도구는 사용하지 않습니다 (REQUIREMENTS 3). 도구 도입은 설계 변경 절차로만 정합니다.
- 한 번 적용된 SQL 파일은 수정하지 않습니다. 새 변경은 다음 번호의 파일로 추가합니다. `ddl-auto`는 `none`을 유지합니다.
- 이번 변경(`member` 컬럼 교체, `storage_delete_task` 추가)은 아직 배포된 DB가 없으므로 초기 스키마에 직접 반영합니다.

### 6.1 수동 SQL 적용 순서 (001~008)

| 순서 | 파일 | 내용 | 재실행 |
|------|------|------|--------|
| 1 | `db/manual/001_create_member_refresh_token.sql` | `member`, `refresh_token` 생성 | 금지 (이미 적용된 파일) |
| 2 | `db/manual/002_add_refresh_token_family_id.sql` | `refresh_token.family_id` 추가, 기존 행 UUID 부여, NOT NULL, `idx_refresh_family` | 금지 (실패 시 SHOW CREATE TABLE로 단계를 확인하고 남은 문장만 실행) |
| 3 | `db/manual/003_create_auth_rate_limit_tables.sql` | 요청 제한 이벤트 4개와 카운터 1개 테이블 생성 | 금지 (이미 테이블이 있으면 실패) |
| 4 | `db/manual/004_create_category_and_restaurant.sql` | `category`(시드 10개), `restaurant` 생성 (F1-02) | 금지 (이미 테이블이 있으면 실패) |
| 5 | `db/manual/005_create_restaurant_image_and_storage_delete_task.sql` | `restaurant_image`, `storage_delete_task` 생성 (F1-03) | 금지 (이미 테이블이 있으면 실패) |
| 6 | `db/manual/006_create_review.sql` | `review` 생성 (F1-04) | 금지 (이미 테이블이 있으면 실패) |
| 7 | `db/manual/007_create_ai_review_summary.sql` | `ai_review_summary` 생성 (F1-05) | 금지 (이미 테이블이 있으면 실패) |
| 8 | `db/manual/008_create_favorite.sql` | `favorite` 생성 (F1-06) | 금지 (이미 테이블이 있으면 실패) |

- 적용 전에는 대상 DB를 백업하고 현재 스키마를 확인합니다. 운영 DB는 점검 시간에 적용합니다.
- 002의 `UUID()`는 버전 1 형식입니다. 시각 정보와 서버 노드 식별 값을 담으므로, 마이그레이션 시점의 한 번 채우는 값으로만 씁니다. 애플리케이션의 신규 `family_id`는 버전 4(난수 기반) UUID를 사용합니다 (2026-11-12 확정, PROGRESS 6.8). 백필 값은 일회성이며 외부 노출 금지 대상입니다.
- `ddl-auto`는 `none`을 유지합니다. 스키마 검증은 적용 후 SHOW CREATE TABLE과 information_schema 조회로 확인합니다.
