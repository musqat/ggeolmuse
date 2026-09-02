-- 1. trades 테이블 마이그레이션
-- ------------------------------------------------------------

-- 1.1 기존 인덱스 삭제 (trade_id 관련)
DROP INDEX IF EXISTS idx_dividends_trade;
DROP INDEX IF EXISTS idx_dividends_trade_date;

-- 1.2 새 id 컬럼 추가
ALTER TABLE trades ADD COLUMN id BIGSERIAL;

-- 1.3 기존 PK 제약조건 삭제
ALTER TABLE trades DROP CONSTRAINT IF EXISTS trades_pkey;

-- 1.4 새 컬럼을 PK로 설정
ALTER TABLE trades ADD PRIMARY KEY (id);

-- 1.5 기존 trade_id 컬럼 삭제
ALTER TABLE trades DROP COLUMN trade_id;

-- 2. holdings 테이블 마이그레이션
-- ------------------------------------------------------------

-- 2.1 새 id 컬럼 추가
ALTER TABLE holdings ADD COLUMN id BIGSERIAL;

-- 2.2 기존 PK 제약조건 삭제
ALTER TABLE holdings DROP CONSTRAINT IF EXISTS holdings_pkey;

-- 2.3 새 컬럼을 PK로 설정
ALTER TABLE holdings ADD PRIMARY KEY (id);

-- 2.4 기존 holding_id 컬럼 삭제
ALTER TABLE holdings DROP COLUMN holding_id;

-- 3. dividends 테이블 마이그레이션
-- ------------------------------------------------------------

-- 3.1 새 id 컬럼 추가
ALTER TABLE dividends ADD COLUMN id BIGSERIAL;

-- 3.2 기존 PK 제약조건 삭제
ALTER TABLE dividends DROP CONSTRAINT IF EXISTS dividends_pkey;

-- 3.3 새 컬럼을 PK로 설정
ALTER TABLE dividends ADD PRIMARY KEY (id);

-- 3.4 기존 dividend_id 컬럼 삭제
ALTER TABLE dividends DROP COLUMN dividend_id;

-- 3.5 trade_id 컬럼을 BIGINT로 변경 (기존 데이터는 null로 설정)
ALTER TABLE dividends DROP COLUMN trade_id;
ALTER TABLE dividends ADD COLUMN trade_id BIGINT;

-- 4. 새 인덱스 생성
-- ------------------------------------------------------------

-- trades 인덱스 (기존 인덱스는 유지됨)
CREATE INDEX IF NOT EXISTS idx_dividends_trade ON dividends(trade_id);
CREATE INDEX IF NOT EXISTS idx_dividends_trade_date ON dividends(trade_id, dividend_date);
