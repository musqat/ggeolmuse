-- 계좌 · 거래일마다 수익 흐름 계산 결과(USD 금액은 평가손익 · 투자 원금만 그 날짜 값이고 나머지는 누적, 원화는 그 날짜 환율)
CREATE TABLE account_profit_daily (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    account_id BIGINT NOT NULL,
    trade_date DATE NOT NULL,
    unrealized_usd DECIMAL(19,2) NOT NULL,
    realized_usd DECIMAL(19,2) NOT NULL,
    dividend_usd DECIMAL(19,2) NOT NULL,
    fee_usd DECIMAL(19,2) NOT NULL,
    fx_rate DECIMAL(18,6) NOT NULL,
    profit_krw DECIMAL(19,0) NOT NULL,
    invested_usd DECIMAL(19,2) NOT NULL,
    invested_krw DECIMAL(19,0) NOT NULL,
    computed_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_account_profit_daily UNIQUE (account_id, trade_date)
);

CREATE INDEX idx_account_profit_daily_user ON account_profit_daily (user_id);
