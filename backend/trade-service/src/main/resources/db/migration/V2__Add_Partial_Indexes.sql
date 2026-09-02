-- 1. 활성 보유자산 부분 인덱스
-- 포트폴리오 쿼리는 수량이 0보다 큰 보유자산만 반환해야 함
CREATE INDEX IF NOT EXISTS idx_holdings_active_portfolio ON holdings(user_id, last_updated_at DESC);

-- 계좌별 활성 보유자산
CREATE INDEX IF NOT EXISTS idx_holdings_active_account ON holdings(user_id, account_id, symbol);

-- 심볼별 활성 보유자산 (시장가치 계산용)
CREATE INDEX IF NOT EXISTS idx_holdings_active_symbol ON holdings(symbol, total_quantity DESC);

-- 2. 최근 거래 부분 인덱스
-- 최근 90일 내 거래 (핫 데이터 최적화)
CREATE INDEX IF NOT EXISTS idx_trades_recent_user ON trades(user_id, executed_at DESC);

-- 심볼별 최근 거래 (트렌드 분석용)
CREATE INDEX IF NOT EXISTS idx_trades_recent_symbol ON trades(symbol, executed_at DESC);

-- 3. 배당금 처리 최적화
-- 배당금 계산 대기 중인 보유자산
CREATE INDEX IF NOT EXISTS idx_holdings_pending_dividend ON holdings(symbol, last_dividend_calculated);

-- 최근 배당금 (사용자 알림 쿼리용)
CREATE INDEX IF NOT EXISTS idx_dividends_recent ON dividends(user_id, processed_at DESC);
