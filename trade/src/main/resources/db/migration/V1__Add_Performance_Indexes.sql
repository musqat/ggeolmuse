-- 성능 최적화를 위한 데이터베이스 인덱스 생성

-- 1. Trade 테이블 인덱스
-- 사용자별 거래 내역 조회 최적화 (TradingController.getTradeHistory)
CREATE INDEX idx_trades_user_executed_at ON trades(user_id, executed_at DESC);

-- 종목별 거래 내역 조회 최적화 (TradingController.getTradeHistoryBySymbol)  
CREATE INDEX idx_trades_user_symbol_executed ON trades(user_id, symbol, executed_at DESC);

-- 기간별 거래 내역 조회 최적화 (TradingController.getTradeHistoryByPeriod)
CREATE INDEX idx_trades_user_trade_date ON trades(user_id, trade_date);

-- FIFO 검증용 복합 인덱스 (TradingServiceImpl.findTradesByUserAccountSymbolBeforeDate)
CREATE INDEX idx_trades_fifo_validation ON trades(user_id, account_id, symbol, trade_date, executed_at);

-- 거래 유형별 조회 최적화 (TradingServiceImpl.findFirstByUserIdAndAccountIdAndSymbolAndTradeTypeOrderByTradeDateDescExecutedAtDesc)
CREATE INDEX idx_trades_type_lookup ON trades(user_id, account_id, symbol, trade_type, trade_date DESC, executed_at DESC);

-- 2. Holdings 테이블 인덱스
-- 사용자별 포트폴리오 조회 최적화 (PortfolioController.getPortfolio)
CREATE INDEX idx_holdings_user_id ON holdings(user_id);

-- 계좌별 포트폴리오 조회 최적화 (PortfolioController.getAccountPortfolio)
CREATE INDEX idx_holdings_user_account ON holdings(user_id, account_id);

-- 배당 계산 대상 조회 최적화 (HoldingsRepository.findHoldingsNeedingDividendCalculation)
CREATE INDEX idx_holdings_dividend_calc ON holdings(last_dividend_calculated, total_quantity);

-- 거래 시 Holdings 조회 최적화 (이미 unique constraint가 있어 추가 불필요)
-- UNIQUE CONSTRAINT가 이미 존재: (user_id, account_id, symbol)

-- 3. 성능 모니터링을 위한 통계 정보 업데이트
-- H2에서는 ANALYZE TABLE 대신 UPDATE STATISTICS 사용
UPDATE INFORMATION_SCHEMA.TABLES SET TABLE_NAME = TABLE_NAME WHERE TABLE_SCHEMA = 'PUBLIC';