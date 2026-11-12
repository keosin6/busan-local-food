-- =====================================================================
-- 003: 요청 제한 이벤트 및 카운터 테이블 생성 (Stage 2-3)
-- 대상: MySQL 8.0.16 이상
-- 선행 조건: 001, 002 적용 완료
-- 정의 기준: docs/DATABASE_ERD.md 2.10
--
-- 이 파일은 자동으로 실행되지 않는다. 사람이 대상 DB에 직접 실행한다.
-- 이미 테이블이 있으면 CREATE TABLE이 실패한다. 실패하면 SHOW TABLES로 현재 상태를 확인한다.
-- IF NOT EXISTS를 쓰지 않는다. 오류를 숨기지 않기 위해서다.
-- 기존 테이블을 삭제하거나 초기화하는 구문은 포함하지 않는다.
--
-- 적용 예 (값은 실행하는 사람이 직접 입력):
--   mysql -h <호스트> -P <포트> -u <사용자> -p <데이터베이스명> < db/manual/003_create_auth_rate_limit_tables.sql
--
-- [개인정보 저장 규칙]
-- - 원문 이메일, 원문 IP, 원문 Refresh Token은 어떤 테이블에도 저장하지 않는다.
-- - 이메일+IP 조합과 IP 식별자는 비밀키 기반 HMAC-SHA256 값(소문자 64자리 hex)으로 저장한다.
-- - HMAC 비밀키는 환경변수로 공급하며 저장소에 커밋하지 않는다.
-- - 이 파일의 CHECK 제약은 두지 않는다. 해시 형식은 애플리케이션이 보장한다.
--
-- [보존 규칙]
-- - 로그인 실패, 회원가입, Refresh, Google OAuth2 이벤트는 생성 후 24시간 보관한다.
-- - 15분, 1시간, 1분 제한 시간을 벗어났다는 이유만으로 즉시 삭제하지 않는다.
-- - 정리는 24시간 보존 기준으로만 수행한다. 요청 시 정리와 Spring Scheduling 기반 정리를 병행한다.
-- - 정리 작업의 한 번 삭제 행 수 제한은 구현 단계에서 설계한다.
--
-- [동시성 규칙]
-- - 제한 판단의 원천은 이벤트 테이블이다.
-- - auth_rate_limit_counter는 횟수 저장용이 아니라 같은 키 요청을 직렬화하는 잠금 행이다.
-- - 여러 카운터 키를 잠글 때는 counter_key 사전순으로 잠근다.
-- - 잠금 행은 INSERT ... ON DUPLICATE KEY UPDATE로 확보하고, SELECT ... FOR UPDATE로 잠근다.
-- - counter_key는 키 종류 접두어와 식별값을 결합한 문자열의 SHA-256 해시이다.
--     접두어: LOGIN_PAIR:, LOGIN_IP:, SIGNUP_IP:, REFRESH_FAMILY:, GOOGLE_OAUTH_IP:
-- - 카운터 행 자동 삭제는 이번 스키마 작업에 포함하지 않는다. 잠금 대기 중인 트랜잭션과의 경합을
--   피하기 위해 안전한 정리 절차를 별도로 설계한다.
--
-- [실패 시]
-- 중간 실패는 테이블 단위로 생긴다. 실패하면 SHOW TABLES;로 생성된 테이블을 확인하고,
-- 없는 테이블만 수동으로 생성한다. 이미 있는 테이블은 건드리지 않는다.
-- =====================================================================

SET NAMES utf8mb4;

-- 1. 로그인 실패 이벤트 (요청 실패 1건당 1행)
CREATE TABLE login_failure_event (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    pair_key_hash     CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ip_key            CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    failed_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_login_failure_pair_time (pair_key_hash, failed_at),
    INDEX idx_login_failure_ip_time (ip_key, failed_at),
    INDEX idx_login_failure_time (failed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 회원가입 요청 이벤트 (JSON 파싱과 DTO 검증 이전에 집계)
CREATE TABLE signup_request_event (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    ip_key            CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requested_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_signup_request_ip_time (ip_key, requested_at),
    INDEX idx_signup_request_time (requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Refresh 요청 이벤트 (계열을 확인할 수 있는 요청만 기록). FK 없음.
--    family_id 표현은 refresh_token.family_id와 같다.
CREATE TABLE refresh_request_event (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    family_id         CHAR(36)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requested_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_refresh_request_family_time (family_id, requested_at),
    INDEX idx_refresh_request_time (requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Google OAuth2 요청 이벤트 (로그인 시작, 계정 연동 시작, 콜백. 요청 1회당 1행)
CREATE TABLE google_oauth_request_event (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    ip_key            CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requested_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_google_oauth_ip_time (ip_key, requested_at),
    INDEX idx_google_oauth_time (requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 동일 키 요청 직렬화용 잠금 행. 횟수를 저장하지 않는다. FK 없음.
CREATE TABLE auth_rate_limit_counter (
    counter_key       CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (counter_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
