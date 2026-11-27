-- 006: 리뷰 테이블 (F1-04, DATABASE_ERD 2.5)
--
-- 적용 순서: 001 → 002 → 003 → 004 → 005 → 006 (이 파일은 member와 restaurant 테이블이 있어야 한다).
-- 적용 전 확인:
--   1) 대상 DB에 member, restaurant 테이블이 있는지 확인한다 (SHOW CREATE TABLE restaurant;).
--   2) review 테이블이 없는지 확인한다. 이미 있으면 이 파일을 실행하지 않는다.
--   3) 운영 DB는 백업한 뒤 점검 시간에 적용한다.
-- 재실행 금지: 적용된 뒤 다시 실행하면 테이블 생성이 실패한다.
-- 실패 시: SHOW TABLES로 생성된 테이블을 확인하고, 남은 문장만 수동으로 실행한다.
-- ddl-auto는 none을 유지한다. 적용 후 SHOW CREATE TABLE로 구조를 확인한다.
-- 평균 평점(restaurant.average_rating)과 리뷰 수(restaurant.review_count)는 이 파일에서 바꾸지 않는다.
-- 리뷰 작성·삭제 트랜잭션이 맛집 행을 잠그고 두 값을 다시 계산한다 (004에서 이미 만들었다).

CREATE TABLE review (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    restaurant_id  BIGINT        NOT NULL,
    member_id      BIGINT        NOT NULL,
    rating         TINYINT       NOT NULL,
    content        VARCHAR(500)  NOT NULL,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_review_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurant (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT uk_review_member_restaurant UNIQUE (restaurant_id, member_id),
    CONSTRAINT ck_review_rating CHECK (rating BETWEEN 1 AND 5),
    INDEX idx_review_restaurant_created (restaurant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
