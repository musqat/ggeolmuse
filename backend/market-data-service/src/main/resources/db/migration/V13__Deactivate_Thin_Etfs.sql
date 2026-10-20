-- 최근 20봉 평균 거래량이 1만 주 아래인 ETF 를 비활성으로. 기준은 설정(marketdata.constants.etf)과 같음
-- 최근 60일 봉만 보고 종목마다 최신 20봉을 고름
UPDATE asset SET active = false
WHERE asset_type = 'ETF' AND active = true
  AND symbol IN (
    SELECT symbol FROM (
      SELECT symbol, volume, row_number() OVER (PARTITION BY symbol ORDER BY date DESC) AS rn
      FROM candle
      WHERE date >= CURRENT_DATE - 60
        AND symbol IN (SELECT symbol FROM asset WHERE asset_type = 'ETF' AND active = true)
    ) recent
    WHERE rn <= 20
    GROUP BY symbol
    HAVING avg(volume) < 10000
  );
