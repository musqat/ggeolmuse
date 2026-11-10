-- S&P 500 · 나스닥 100 지수(야후 심볼), 이미 있으면 그대로
INSERT INTO asset (symbol, name, country, currency, asset_type, active)
SELECT '^GSPC', 'S&P 500', 'US', 'USD', 'INDEX', true
WHERE NOT EXISTS (SELECT 1 FROM asset WHERE symbol = '^GSPC');

INSERT INTO asset (symbol, name, country, currency, asset_type, active)
SELECT '^NDX', 'NASDAQ 100', 'US', 'USD', 'INDEX', true
WHERE NOT EXISTS (SELECT 1 FROM asset WHERE symbol = '^NDX');
