-- 계좌 · 종목 · 배당락일마다 받을 배당 몫. 거래 기록으로 다시 계산해 덮는다
CREATE TABLE dividend_payouts (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    account_id BIGINT NOT NULL,
    symbol VARCHAR(10) NOT NULL,
    ex_date DATE NOT NULL,
    shares DECIMAL(19,6) NOT NULL,
    dividend_per_share DECIMAL(19,6) NOT NULL,
    gross_amount DECIMAL(19,2) NOT NULL,
    tax_amount DECIMAL(19,2) NOT NULL,
    net_amount DECIMAL(19,2) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_dividend_payouts UNIQUE (account_id, symbol, ex_date)
);

CREATE INDEX idx_dividend_payouts_user ON dividend_payouts (user_id);
