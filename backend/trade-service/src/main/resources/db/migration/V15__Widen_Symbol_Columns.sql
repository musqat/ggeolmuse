-- 종목 코드 칸을 주문이 받는 종목 코드 길이(16)에 맞춤(늘리기라 기존 행 다시 쓰기 없음)
ALTER TABLE trades ALTER COLUMN symbol TYPE VARCHAR(16);
ALTER TABLE holdings ALTER COLUMN symbol TYPE VARCHAR(16);
ALTER TABLE dividend_payouts ALTER COLUMN symbol TYPE VARCHAR(16);
ALTER TABLE stock_splits ALTER COLUMN symbol TYPE VARCHAR(16);
