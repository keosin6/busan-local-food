-- 004: 카테고리와 맛집 테이블 (F1-02, DATABASE_ERD 2.2, 2.3)
--
-- 적용 순서: 001 → 002 → 003 → 004 (이 파일은 001의 member 테이블이 있어야 한다).
-- 적용 전 확인:
--   1) 대상 DB에 member 테이블이 있는지 확인한다 (SHOW TABLES; SHOW CREATE TABLE member;).
--   2) category, restaurant 테이블이 없는지 확인한다. 이미 있으면 이 파일을 실행하지 않는다.
--   3) 운영 DB는 백업한 뒤 점검 시간에 적용한다.
-- 재실행 금지: 적용된 뒤 다시 실행하면 테이블 생성과 시드 입력이 실패하거나 중복된다.
-- 실패 시: SHOW TABLES로 생성된 테이블을 확인하고, 남은 문장만 수동으로 실행한다.
-- ddl-auto는 none을 유지한다. 적용 후 SHOW CREATE TABLE로 구조를 확인한다.
-- 한글 시드가 들어가므로 클라이언트 문자셋을 utf8mb4로 맞춘다 (아래 SET NAMES와 함께 쓴다).
--   예: mysql --default-character-set=utf8mb4 -h <호스트> -u <사용자> -p <DB 이름> < db/manual/004_create_category_and_restaurant.sql

SET NAMES utf8mb4;

CREATE TABLE category (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(30)  NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_category_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO category (name, sort_order) VALUES
    ('한식', 1),
    ('중식', 2),
    ('일식', 3),
    ('양식', 4),
    ('분식', 5),
    ('카페·디저트', 6),
    ('해산물', 7),
    ('고기·구이', 8),
    ('술집', 9),
    ('기타', 10);

CREATE TABLE restaurant (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    member_id       BIGINT        NOT NULL,
    category_id     BIGINT        NOT NULL,
    name            VARCHAR(50)   NOT NULL,
    address         VARCHAR(200)  NOT NULL,
    phone           VARCHAR(20)   NULL,
    description     TEXT          NOT NULL,
    latitude        DECIMAL(10,7) NULL,
    longitude       DECIMAL(10,7) NULL,
    average_rating  DECIMAL(2,1)  NOT NULL DEFAULT 0.0,
    review_count    INT           NOT NULL DEFAULT 0,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_restaurant_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_restaurant_category FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE RESTRICT,
    CONSTRAINT ck_restaurant_rating CHECK (average_rating BETWEEN 0 AND 5),
    CONSTRAINT ck_restaurant_review_count CHECK (review_count >= 0),
    INDEX idx_restaurant_category (category_id, created_at),
    INDEX idx_restaurant_created (created_at),
    INDEX idx_restaurant_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
