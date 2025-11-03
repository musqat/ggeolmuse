CREATE INDEX IF NOT EXISTS idx_asset_market_cap ON asset(market_cap DESC NULLS LAST);

CREATE INDEX IF NOT EXISTS idx_asset_active_market_cap ON asset(active, market_cap DESC NULLS LAST) WHERE active = true;
