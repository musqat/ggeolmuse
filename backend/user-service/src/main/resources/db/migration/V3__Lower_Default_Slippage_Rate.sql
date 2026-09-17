-- 슬리피지 기본값 1% → 0.1%
-- 계좌를 만들 때 율을 받는 칸이 없었으므로 0.01 인 행은 전부 손대지 않은 기본값이다
ALTER TABLE account ALTER COLUMN slippage_rate SET DEFAULT 0.001;

UPDATE account SET slippage_rate = 0.001 WHERE slippage_rate = 0.01;
