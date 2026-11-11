-- =====================================================================
-- 002: refresh_token.family_id 추가 (Stage 2-3)
-- 대상: MySQL 8.0.16 이상
-- 선행 조건: 001_create_member_refresh_token.sql 적용 완료
-- 정의 기준: docs/DATABASE_ERD.md 2.8 (refresh_token)
--
-- 이 파일은 자동으로 실행되지 않는다. 사람이 대상 DB에 직접 실행한다.
-- 재실행을 전제로 하지 않는다. 이 파일은 한 번만 적용한다.
--
-- [적용 전 필수 확인]
-- 1. 대상 DB를 백업한다.
-- 2. 현재 스키마를 확인한다. family_id 컬럼이 이미 있으면 이 파일을 실행하지 않는다.
--      SHOW CREATE TABLE refresh_token;
-- 3. 대상이 개발 DB인지, 운영 DB인지 확인한다. 운영 DB에는 점검 시간에 실행한다.
--
-- [실행 단계]
--   단계 1: family_id를 NULL 허용으로 추가
--   단계 2: 기존 행마다 개별 UUID 부여 (NULL인 행만 대상)
--   단계 3: NULL이 남아 있지 않은지 확인 후 NOT NULL로 변경
--   단계 4: idx_refresh_family 인덱스 생성
--
-- [실패 시 복구 절차]
-- DDL은 트랜잭션으로 묶이지 않으므로 중간 실패 시 일부 단계만 적용된 상태가 된다.
-- 실패하면 이 파일을 처음부터 다시 실행하지 않는다.
--   1) SHOW CREATE TABLE refresh_token; 으로 어느 단계까지 적용되었는지 확인한다.
--      - family_id 컬럼이 없으면: 단계 1부터 실행
--      - family_id 컬럼은 있고 NULL 행이 남았으면: 단계 2부터 실행
--      - NOT NULL로 바뀌지 않았으면: 단계 3부터 실행
--      - 인덱스가 없으면: 단계 4만 실행
--   2) 확인된 단계 이후의 문장만 수동으로 실행한다.
--
-- [데이터 처리 원칙]
-- - 기존 토큰 행의 계열 정보는 복원할 수 없으므로 행마다 서로 다른 UUID를 부여한다.
--   같은 계열로 묶지 않는다.
-- - 기존 token_hash, member_id, expires_at, revoked_at, created_at 값은 변경하지 않는다.
-- - 기존 Refresh Token을 일괄 폐기하지 않는다. 기존 데이터를 삭제하지 않는다.
--
-- [UUID 형식]
-- 이 파일은 MySQL UUID() 함수를 사용한다. 반환값은 버전 1 형식이며 시각 정보와
-- 서버 노드 식별 값을 포함한다. 마이그레이션 시점의 한 번 채우는 값에만 사용한다.
-- 애플리케이션이 새로 발급하는 family_id는 난수 기반 버전 4 UUID를 사용하도록 구현 단계에서 정한다.
-- =====================================================================

SET NAMES utf8mb4;

-- 단계 1: NULL 허용 컬럼으로 추가 (기존 행 값은 아직 NULL)
ALTER TABLE refresh_token
    ADD COLUMN family_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL
    AFTER token_hash;

-- 단계 2: 기존 행마다 개별 UUID 부여.
-- UUID()는 행마다 평가되므로 같은 계열로 묶이지 않는다.
UPDATE refresh_token
   SET family_id = UUID()
 WHERE family_id IS NULL;

-- 단계 3: NULL이 남아 있으면 아래 변경이 실패한다. 실패 시 단계 2의 결과를 확인한다.
--   SELECT COUNT(*) FROM refresh_token WHERE family_id IS NULL;   -- 기대값: 0
ALTER TABLE refresh_token
    MODIFY COLUMN family_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

-- 단계 4: 계열 단위 조회 인덱스
ALTER TABLE refresh_token
    ADD INDEX idx_refresh_family (family_id);
