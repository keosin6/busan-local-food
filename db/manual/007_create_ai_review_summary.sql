-- 007: AI 리뷰 요약 캐시 테이블 (F1-05, DATABASE_ERD 2.7)
--
-- 적용 순서: 001 → 002 → 003 → 004 → 005 → 006 → 007 (이 파일은 restaurant 테이블이 있어야 한다).
-- 적용 전 확인:
--   1) 대상 DB에 restaurant 테이블이 있는지 확인한다 (SHOW CREATE TABLE restaurant;).
--   2) ai_review_summary 테이블이 없는지 확인한다. 이미 있으면 이 파일을 실행하지 않는다.
--   3) 운영 DB는 백업한 뒤 점검 시간에 적용한다.
-- 재실행 금지: 적용된 뒤 다시 실행하면 테이블 생성이 실패한다.
-- 실패 시: SHOW TABLES로 생성된 테이블을 확인하고, 남은 문장만 수동으로 실행한다.
-- ddl-auto는 none을 유지한다. 적용 후 SHOW CREATE TABLE로 구조를 확인한다.
--
-- 이 테이블은 캐시다. 행이 있으면 요약이 유효하다는 뜻이고, 리뷰 작성·삭제 트랜잭션이 맛집의 행을 지운다.
-- 요약 생성 시점의 리뷰 수는 source_review_count에 남긴다. 3 미만 맛집은 요약을 만들지 않으므로 CHECK로 막는다.

CREATE TABLE ai_review_summary (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    restaurant_id        BIGINT       NOT NULL,
    summary              TEXT         NOT NULL,
    source_review_count  INT          NOT NULL,
    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_summary_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurant (id) ON DELETE CASCADE,
    CONSTRAINT uk_summary_restaurant UNIQUE (restaurant_id),
    CONSTRAINT ck_summary_review_count CHECK (source_review_count >= 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
