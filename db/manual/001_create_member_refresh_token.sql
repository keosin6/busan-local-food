-- =====================================================================
-- 001: member, refresh_token 테이블 생성 (Stage 2-1)
-- 대상: MySQL 8.0.16 이상 (CHECK 제약 적용)
-- 정의 기준: docs/DATABASE_ERD.md 2.1 (member), 2.8 (refresh_token)
--
-- 이 파일은 자동으로 실행되지 않는다 (Flyway 등 마이그레이션 도구 미사용).
-- 사람이 대상 DB에 직접 한 번 실행한다. 기존 데이터를 삭제하거나
-- 테이블을 초기화하는 구문은 포함하지 않는다.
-- 이미 테이블이 있으면 CREATE TABLE이 실패하므로, 실패 시 상태를 먼저 확인한다.
--
-- 적용 예 (값은 실행하는 사람이 직접 입력):
--   mysql -h <호스트> -P <포트> -u <사용자> -p <데이터베이스명> < db/manual/001_create_member_refresh_token.sql
--
-- 실행 순서: member를 먼저 만든 뒤 refresh_token을 만든다 (FK 참조 순서).
-- =====================================================================

SET NAMES utf8mb4;

CREATE TABLE member (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    email           VARCHAR(255)  NOT NULL,
    password_hash   VARCHAR(100)  NULL,
    nickname        VARCHAR(20)   NOT NULL,
    role            VARCHAR(20)   NOT NULL DEFAULT 'USER',
    google_sub      VARCHAR(255)  NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_member_email UNIQUE (email),
    CONSTRAINT uk_member_nickname UNIQUE (nickname),
    CONSTRAINT uk_member_google_sub UNIQUE (google_sub),
    CONSTRAINT ck_member_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_member_login_method CHECK (password_hash IS NOT NULL OR google_sub IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refresh_token (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    member_id    BIGINT       NOT NULL,
    token_hash   CHAR(64)     NOT NULL,
    expires_at   DATETIME(6)  NOT NULL,
    revoked_at   DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    INDEX idx_refresh_member (member_id, revoked_at),
    CONSTRAINT fk_refresh_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
