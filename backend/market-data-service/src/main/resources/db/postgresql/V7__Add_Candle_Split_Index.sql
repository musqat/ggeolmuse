-- 분할 계수가 1 이 아닌 행만 담아 찾기가 테이블 전체를 읽지 않게 한다. 부분 인덱스라 Postgres 에만 둔다
CREATE INDEX IF NOT EXISTS idx_candle_split_date ON candle (date) WHERE split_coefficient <> 1;
