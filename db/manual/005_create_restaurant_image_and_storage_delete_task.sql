-- 005: 맛집 이미지와 S3 삭제 추적 테이블 (F1-03, DATABASE_ERD 2.4, 2.9)
--
-- 적용 순서: 001 → 002 → 003 → 004 → 005 (이 파일은 004의 restaurant 테이블이 있어야 한다).
-- 적용 전 확인:
--   1) 대상 DB에 restaurant 테이블이 있는지 확인한다 (SHOW CREATE TABLE restaurant;).
--   2) restaurant_image, storage_delete_task 테이블이 없는지 확인한다. 이미 있으면 이 파일을 실행하지 않는다.
--   3) 운영 DB는 백업한 뒤 점검 시간에 적용한다.
-- 재실행 금지: 적용된 뒤 다시 실행하면 테이블 생성이 실패한다.
-- 실패 시: SHOW TABLES로 생성된 테이블을 확인하고, 남은 문장만 수동으로 실행한다.
-- ddl-auto는 none을 유지한다. 적용 후 SHOW CREATE TABLE로 구조를 확인한다.
-- 이 파일에는 한글 데이터가 없다. 그래도 클라이언트 문자셋은 utf8mb4로 실행한다.

CREATE TABLE restaurant_image (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    restaurant_id     BIGINT        NOT NULL,
    s3_key            VARCHAR(500)  NOT NULL,
    original_filename VARCHAR(255)  NOT NULL,
    content_type      VARCHAR(50)   NOT NULL,
    file_size         INT           NOT NULL,
    sort_order        INT           NOT NULL DEFAULT 0,
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_image_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurant (id) ON DELETE CASCADE,
    CONSTRAINT uk_image_s3_key UNIQUE (s3_key),
    CONSTRAINT ck_image_file_size CHECK (file_size BETWEEN 1 AND 5242880),
    CONSTRAINT ck_image_content_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_image_sort_order CHECK (sort_order >= 0),
    INDEX idx_image_restaurant (restaurant_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE storage_delete_task (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    s3_key       VARCHAR(500)  NOT NULL,
    retry_count  INT           NOT NULL DEFAULT 0,
    last_error   VARCHAR(500)  NULL,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_storage_task_s3_key UNIQUE (s3_key),
    CONSTRAINT ck_storage_task_retry CHECK (retry_count >= 0),
    INDEX idx_storage_task_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
