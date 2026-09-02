-- 시가총액 정렬 인덱스
CREATE INDEX IF NOT EXISTS idx_asset_market_cap ON asset(market_cap DESC NULLS LAST);

-- 활성 자산의 시가총액 정렬 인덱스
CREATE INDEX IF NOT EXISTS idx_asset_active_market_cap ON asset(active, market_cap DESC NULLS LAST);
