-- 10-20 이후 봉을 받은 수집에서 거래량 검사로 내려간 기존 ETF 를 되돌림. 그 앞 20봉 평균이 1만 주 아래(V13 기준)면 그대로
-- 상장폐지일 없는 비활성 ETF 중 10-20 전에도 봉이 있는 것만 봄
UPDATE asset SET active = true
WHERE asset_type = 'ETF' AND active = false AND delisted_date IS NULL
  AND EXISTS (SELECT 1 FROM candle c WHERE c.symbol = asset.symbol AND c.date < DATE '2026-10-20')
  AND symbol IN (
    SELECT symbol FROM (
      SELECT symbol, volume,
             row_number() OVER (PARTITION BY symbol ORDER BY date DESC) AS rn,
             max(date) OVER (PARTITION BY symbol) AS last_date
      FROM candle
      WHERE date >= DATE '2026-08-01'
        AND symbol IN (SELECT symbol FROM asset WHERE asset_type = 'ETF' AND active = false AND delisted_date IS NULL)
    ) recent
    WHERE rn <= 21
    GROUP BY symbol
    HAVING max(last_date) >= DATE '2026-10-20'
       AND (avg(CASE WHEN rn >= 2 THEN volume END) IS NULL OR avg(CASE WHEN rn >= 2 THEN volume END) >= 10000)
  );
