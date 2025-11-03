-- ============================================================
-- Tables
-- ============================================================

-- Trades table
CREATE TABLE IF NOT EXISTS trades (
    trade_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    account_id BIGINT NOT NULL,
    symbol VARCHAR(10) NOT NULL,
    trade_type VARCHAR(10) NOT NULL,
    quantity DECIMAL(15,6) NOT NULL,
    price DECIMAL(15,2) NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    fee DECIMAL(10,2) NOT NULL DEFAULT 0,
    trade_date DATE NOT NULL,
    executed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Holdings table
CREATE TABLE IF NOT EXISTS holdings (
    holding_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    account_id BIGINT NOT NULL,
    symbol VARCHAR(10) NOT NULL,
    total_quantity DECIMAL(15,6) NOT NULL DEFAULT 0,
    avg_purchase_price DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_invested_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    last_dividend_calculated DATE,
    created_at TIMESTAMP NOT NULL,
    last_updated_at TIMESTAMP NOT NULL,
    UNIQUE (user_id, account_id, symbol)
);

-- Dividends table (사용자 배당 지급 내역)
CREATE TABLE IF NOT EXISTS dividends (
    dividend_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    account_id BIGINT NOT NULL,
    symbol VARCHAR(10) NOT NULL,
    trade_id VARCHAR(36),
    shares DECIMAL(15,6) NOT NULL,
    dividend_per_share DECIMAL(15,6) NOT NULL,
    gross_amount DECIMAL(15,2) NOT NULL,
    tax_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    net_amount DECIMAL(15,2) NOT NULL,
    dividend_date DATE NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- ============================================================
-- Performance Indexes
-- ============================================================

-- 1. Trades 테이블 인덱스
-- 종목 및 거래일 조회 최적화
CREATE INDEX idx_trades_symbol_date ON trades(symbol, trade_date);

-- 사용자별 거래 내역 조회 최적화 (getTradeHistory)
CREATE INDEX idx_trades_user_executed_at ON trades(user_id, executed_at DESC);

-- 종목별 거래 내역 조회 최적화 (getTradeHistoryBySymbol)
CREATE INDEX idx_trades_user_symbol_executed ON trades(user_id, symbol, executed_at DESC);

-- 기간별 거래 내역 조회 최적화 (getTradeHistoryByPeriod)
CREATE INDEX idx_trades_user_trade_date ON trades(user_id, trade_date);

-- 계좌별 거래 조회 최적화
CREATE INDEX idx_trades_user_account ON trades(user_id, account_id);

-- FIFO 검증용 복합 인덱스
CREATE INDEX idx_trades_fifo_validation ON trades(user_id, account_id, symbol, trade_date, executed_at);

-- 거래 유형별 조회 최적화
CREATE INDEX idx_trades_type_lookup ON trades(user_id, account_id, symbol, trade_type, trade_date DESC, executed_at DESC);

-- 2. Holdings 테이블 인덱스
-- 사용자별 포트폴리오 조회 최적화 (getPortfolio)
CREATE INDEX idx_holdings_user_id ON holdings(user_id);

-- 계좌별 포트폴리오 조회 최적화 (getAccountPortfolio)
CREATE INDEX idx_holdings_user_account ON holdings(user_id, account_id);

-- 종목별 조회 최적화
CREATE INDEX idx_holdings_user_symbol ON holdings(user_id, symbol);

-- 계좌-종목 조회 최적화
CREATE INDEX idx_holdings_account_symbol ON holdings(account_id, symbol);

-- 배당 계산 대상 조회 최적화
CREATE INDEX idx_holdings_dividend_calc ON holdings(last_dividend_calculated, total_quantity);

-- 3. Dividends 테이블 인덱스
-- 사용자-종목-날짜 조회 최적화
CREATE INDEX idx_dividends_user_symbol_date ON dividends(user_id, symbol, dividend_date);

-- 사용자별 최근 배당 조회 최적화
CREATE INDEX idx_dividends_user_created ON dividends(user_id, created_at);

-- 종목-날짜 조회 최적화
CREATE INDEX idx_dividends_symbol_date ON dividends(symbol, dividend_date);

-- 거래 연결 조회 최적화
CREATE INDEX idx_dividends_trade ON dividends(trade_id);

-- 거래-날짜 조회 최적화
CREATE INDEX idx_dividends_trade_date ON dividends(trade_id, dividend_date);
