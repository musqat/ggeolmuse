-- 같은 컬럼에 두 벌씩 만들어진 제약과 인덱스를 지운다.
-- uk 로 시작하는 이름은 Hibernate ddl-auto 가 만든 것이고, 지금 설정은 validate 라
-- 다시 생기지 않는다. 새로 만든 DB 에는 없으므로 IF EXISTS 로 건너뛴다.

-- candle: UNIQUE (symbol, date, currency) 가 두 개. V1 이 만든 쪽을 남긴다
ALTER TABLE candle DROP CONSTRAINT IF EXISTS uknt7qk7gk8w0hrh7a20xv8o65w;

-- dividend: UNIQUE (symbol, ex_date) 두 개와 같은 컬럼 인덱스 둘. V1 의 제약만 남긴다
ALTER TABLE dividend DROP CONSTRAINT IF EXISTS ukqi5pw23iae7subv8f2wh9ocjr;
DROP INDEX IF EXISTS idx_dividend_symbol_date;
DROP INDEX IF EXISTS idx_dividend_symbol_ex_date;

-- asset: V2 는 조건 없는 인덱스를 만드는데 운영 DB 에는 WHERE active = true 가 붙어 있다.
DROP INDEX IF EXISTS idx_asset_active_market_cap;
CREATE INDEX idx_asset_active_market_cap ON asset(active, market_cap DESC NULLS LAST);
