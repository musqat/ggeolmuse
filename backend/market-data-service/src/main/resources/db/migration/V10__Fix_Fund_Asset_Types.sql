-- 이름이 펀드(ETF 낱말 · exchange traded fund · money market)인데 주식으로 든 종목을 ETF 로. Netflix 처럼 낱말 안에 든 etf 는 안 봄
-- 낱말 경계는 H2 · Postgres 가 같이 읽게 문자 클래스로 씀
UPDATE asset SET asset_type = 'ETF'
WHERE asset_type = 'EQUITY'
  AND regexp_like(name, '(^|[^a-z0-9])(etf|exchange traded fund|money market)([^a-z0-9]|$)', 'i');
