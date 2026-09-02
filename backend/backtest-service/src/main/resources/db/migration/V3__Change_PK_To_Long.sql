-- 1. backtest_history 테이블 마이그레이션
-- ------------------------------------------------------------

-- 1.1 새 id 컬럼 추가
ALTER TABLE backtest_history ADD COLUMN id BIGSERIAL;

-- 1.2 기존 PK 제약조건 삭제
ALTER TABLE backtest_history DROP CONSTRAINT IF EXISTS backtest_history_pkey;

-- 1.3 새 컬럼을 PK로 설정
ALTER TABLE backtest_history ADD PRIMARY KEY (id);

-- 1.4 기존 backtest_id 컬럼 삭제
ALTER TABLE backtest_history DROP COLUMN backtest_id;

-- 2. investment_backtest_result 테이블 마이그레이션
-- ------------------------------------------------------------

-- 2.1 새 id 컬럼 추가
ALTER TABLE investment_backtest_result ADD COLUMN id BIGSERIAL;

-- 2.2 기존 PK 제약조건 삭제
ALTER TABLE investment_backtest_result DROP CONSTRAINT IF EXISTS investment_backtest_result_pkey;

-- 2.3 새 컬럼을 PK로 설정
ALTER TABLE investment_backtest_result ADD PRIMARY KEY (id);

-- 2.4 기존 result_id 컬럼 삭제
ALTER TABLE investment_backtest_result DROP COLUMN result_id;
