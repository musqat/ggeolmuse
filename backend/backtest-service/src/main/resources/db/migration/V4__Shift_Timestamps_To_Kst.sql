-- 파드 시간대를 UTC 에서 Asia/Seoul 로 바꾸며(helm TZ) 그동안 UTC 벽시계로 쌓인 시각을 한국 시각으로 옮긴다.
-- 새 파드가 요청을 받기 전에 한 번 돈다
UPDATE backtest_history SET created_at = created_at + INTERVAL '9' HOUR;

UPDATE investment_backtest_result SET
    calculated_at = calculated_at + INTERVAL '9' HOUR,
    created_at = created_at + INTERVAL '9' HOUR,
    updated_at = updated_at + INTERVAL '9' HOUR;
