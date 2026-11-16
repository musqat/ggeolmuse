-- 분할 비율로 나눈 체결가가 센트에서 잘리지 않는 소수 6자리
ALTER TABLE trades ALTER COLUMN price TYPE DECIMAL(19,6);

-- 분할로 맞춘 체결의 누적 비율 · 처음 수량 · 처음 체결가 · 마지막 반영 시각(맞춘 적 없으면 빈 값)
ALTER TABLE trades ADD COLUMN split_ratio DECIMAL(19,8);
ALTER TABLE trades ADD COLUMN pre_split_quantity DECIMAL(15,6);
ALTER TABLE trades ADD COLUMN pre_split_price DECIMAL(19,6);
ALTER TABLE trades ADD COLUMN split_applied_at TIMESTAMP;

-- 반영한 주식 분할(종목 · 분할일마다 한 줄)
CREATE TABLE stock_splits (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    split_date DATE NOT NULL,
    ratio DECIMAL(19,8) NOT NULL,
    adjusted_trades INTEGER NOT NULL,
    applied_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_stock_splits UNIQUE (symbol, split_date)
);
