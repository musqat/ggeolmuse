-- 거래소 시험 종목(기호 · 이름)을 비활성으로
UPDATE asset SET active = false, delisted_date = CURRENT_DATE
WHERE active
  AND (regexp_like(symbol, '^([A-Z]TEST(-[A-Z])?|ZTST|Z[A-Z]ZZT)$') OR lower(name) LIKE '%listed test%' OR lower(name) LIKE '%test stock%');
