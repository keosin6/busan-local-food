-- 008: 찜 테이블 (F1-06, DATABASE_ERD 2.6)
--
-- 적용 순서: 001 → 002 → 003 → 004 → 005 → 006 → 007 → 008 (이 파일은 member와 restaurant 테이블이 있어야 한다).
-- 적용 전 확인:
--   1) 대상 DB에 member, restaurant 테이블이 있는지 확인한다 (SHOW CREATE TABLE restaurant;).
--   2) favorite 테이블이 없는지 확인한다. 이미 있으면 이 파일을 실행하지 않는다.
--   3) 운영 DB는 백업한 뒤 점검 시간에 적용한다.
-- 재실행 금지: 적용된 뒤 다시 실행하면 테이블 생성이 실패한다.
-- 실패 시: SHOW TABLES로 생성된 테이블을 확인하고, 남은 문장만 수동으로 실행한다.
-- ddl-auto는 none을 유지한다. 적용 후 SHOW CREATE TABLE로 구조를 확인한다.
--
-- 같은 회원은 같은 맛집을 한 번만 찜할 수 있다 (uk_favorite_member_restaurant). 동시 요청의 최종 방어선이다.
-- FK 정책은 둘 다 CASCADE다. 맛집을 지우면 찜이 함께 지워지고(F1-02 삭제 흐름), 회원을 지우면 그 회원의 찜이 지워진다.
-- 찜은 회원의 개인 데이터이므로 RESTRICT로 막지 않는다. 회원 탈퇴(T1)가 찜 때문에 막히지 않게 한다.
-- idx_favorite_member_created는 내 찜 목록을 최신 찜 순으로 읽는다.
-- idx_favorite_restaurant은 맛집별 찜 수 조회에 쓴다. FK 제약에도 이 인덱스를 쓰도록 먼저 만든다.

CREATE TABLE favorite (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    member_id      BIGINT       NOT NULL,
    restaurant_id  BIGINT       NOT NULL,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_favorite_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurant (id) ON DELETE CASCADE,
    CONSTRAINT uk_favorite_member_restaurant UNIQUE (member_id, restaurant_id),
    INDEX idx_favorite_member_created (member_id, created_at),
    INDEX idx_favorite_restaurant (restaurant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
