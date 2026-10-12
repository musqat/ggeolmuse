-- 파드 시간대를 UTC 에서 Asia/Seoul 로 바꾸며(helm TZ) 그동안 UTC 벽시계로 쌓인 시각을 한국 시각으로 옮긴다.
-- 새 파드가 요청을 받기 전에 한 번 돈다. 날짜(DATE) 컬럼은 그대로
UPDATE users SET
    created_at = created_at + INTERVAL '9' HOUR,
    updated_at = updated_at + INTERVAL '9' HOUR,
    last_login_at = last_login_at + INTERVAL '9' HOUR;

UPDATE account SET
    created_at = created_at + INTERVAL '9' HOUR,
    updated_at = updated_at + INTERVAL '9' HOUR;

UPDATE account_history SET created_at = created_at + INTERVAL '9' HOUR;

UPDATE email_verification_tokens SET
    created_at = created_at + INTERVAL '9' HOUR,
    expiry_date = expiry_date + INTERVAL '9' HOUR;

UPDATE password_reset_tokens SET
    created_at = created_at + INTERVAL '9' HOUR,
    expiry_date = expiry_date + INTERVAL '9' HOUR;

UPDATE trade_settlements SET processed_at = processed_at + INTERVAL '9' HOUR;

UPDATE dividend_settlements SET processed_at = processed_at + INTERVAL '9' HOUR;
