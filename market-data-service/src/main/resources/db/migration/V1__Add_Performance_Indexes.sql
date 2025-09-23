-- Market Data Service 성능 최적화를 위한 데이터베이스 인덱스 생성

-- 1. Candle 테이블 인덱스
-- 최신 가격 조회 최적화 (getCurrentPrice - findFirstBySymbolOrderByDateDesc)
CREATE INDEX idx_candle_symbol_date_desc ON candle(symbol, date DESC);

-- 기간별 OHLC 조회 최적화 (getMultipleOHLCPrices - findBySymbolsAndDateRange)
CREATE INDEX idx_candle_symbol_date_range ON candle(symbol, date);

-- 배당 포함 캔들 조회 최적화 (getCandlesWithDividends)
CREATE INDEX idx_candle_dividend_lookup ON candle(symbol, date, dividend_amount);

-- 2. Asset 테이블 인덱스
-- 국가별 자산 조회 최적화 (getAssetsByCountry)
CREATE INDEX idx_asset_country ON asset(country);

-- 통화별 자산 조회 최적화 (getAssetsByCurrency)
CREATE INDEX idx_asset_currency ON asset(currency);

-- 자산 유형별 조회 최적화 (getAssetsByType)
CREATE INDEX idx_asset_type ON asset(asset_type);

-- 동적 필터링 최적화 (getAssetsWithFilters)
CREATE INDEX idx_asset_filters ON asset(country, currency, asset_type);

-- 3. Dividend 테이블 인덱스
-- 기간별 배당 조회 최적화 (getDividendHistory - findBySymbolsAndDateRange)
CREATE INDEX idx_dividend_symbol_date ON dividend(symbol, ex_date);

-- 고배당주 검색 최적화 (findHighDividendStocks)
CREATE INDEX idx_dividend_amount_date ON dividend(amount DESC, ex_date);

-- 배당 지급일 조회 최적화
CREATE INDEX idx_dividend_ex_date ON dividend(ex_date);

-- 4. FX Rate 테이블 인덱스
-- 날짜별 환율 조회 최적화 (findByDate)
CREATE INDEX idx_fx_rate_date ON fx_rate(date);

-- 최신 환율 조회 최적화 (findLatestRate)
CREATE INDEX idx_fx_rate_date_desc ON fx_rate(date DESC);

-- 기간별 환율 조회 최적화 (findByDateRange)
CREATE INDEX idx_fx_rate_date_range ON fx_rate(date, currency_pair);

-- 5. 성능 모니터링을 위한 통계 정보 업데이트
-- H2에서는 ANALYZE TABLE 대신 UPDATE STATISTICS 사용
UPDATE INFORMATION_SCHEMA.TABLES SET TABLE_NAME = TABLE_NAME WHERE TABLE_SCHEMA = 'PUBLIC';