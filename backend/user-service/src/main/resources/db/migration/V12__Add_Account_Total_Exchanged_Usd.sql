-- 원화 → 달러 환전으로 받은 달러 누계(평균 환율 = 원화 누계 / 달러 누계)
ALTER TABLE account ADD COLUMN IF NOT EXISTS total_exchanged_usd DECIMAL(15,2) NOT NULL DEFAULT 0;

-- 원화 누계에 든 환전(마지막 초기화 뒤, 최근 것부터 원화 합이 원화 누계를 채울 때까지)으로 두 누계 다시 계산
UPDATE account SET (total_exchanged_krw, total_exchanged_usd) = (
    SELECT COALESCE(SUM(h.original_amount), 0), COALESCE(SUM(h.amount), 0)
    FROM account_history h
    WHERE h.account_id = account.id
      AND h.transaction_type = 'EXCHANGE' AND h.from_currency = 'KRW' AND h.to_currency = 'USD'
      AND (
          SELECT COALESCE(SUM(n.original_amount), 0)
          FROM account_history n
          WHERE n.account_id = h.account_id
            AND n.transaction_type = 'EXCHANGE' AND n.from_currency = 'KRW' AND n.to_currency = 'USD'
            AND (n.created_at > h.created_at OR (n.created_at = h.created_at AND n.id > h.id))
      ) < account.total_exchanged_krw
)
WHERE total_exchanged_krw > 0
  AND EXISTS (
      SELECT 1
      FROM account_history h
      WHERE h.account_id = account.id
        AND h.transaction_type = 'EXCHANGE' AND h.from_currency = 'KRW' AND h.to_currency = 'USD'
  );

-- 환전 기록이 없는 원화 누계는 지금 평균 환율로 달러 누계 역산
UPDATE account SET total_exchanged_usd = ROUND(total_exchanged_krw / avg_exchange_rate, 2)
WHERE total_exchanged_krw > 0 AND total_exchanged_usd = 0 AND avg_exchange_rate > 0;

UPDATE account SET avg_exchange_rate = ROUND(total_exchanged_krw / total_exchanged_usd, 6)
WHERE total_exchanged_usd > 0;
