-- 파드 시간대를 UTC 에서 Asia/Seoul 로 바꾸며(helm TZ) 그동안 UTC 벽시계로 쌓인 시각을 한국 시각으로 옮긴다.
-- 새 파드가 요청을 받기 전에 한 번 돈다. 거래일 · 배당일 같은 날짜(DATE) 컬럼은 그대로
UPDATE trades SET
    executed_at = executed_at + INTERVAL '9' HOUR,
    created_at = created_at + INTERVAL '9' HOUR,
    event_published_at = event_published_at + INTERVAL '9' HOUR,
    cancelled_at = cancelled_at + INTERVAL '9' HOUR;

UPDATE holdings SET
    created_at = created_at + INTERVAL '9' HOUR,
    last_updated_at = last_updated_at + INTERVAL '9' HOUR;

UPDATE dividends SET
    processed_at = processed_at + INTERVAL '9' HOUR,
    created_at = created_at + INTERVAL '9' HOUR;
