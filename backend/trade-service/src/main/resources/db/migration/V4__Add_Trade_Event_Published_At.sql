-- 체결 이벤트 발행 시각. 비어 있으면 다시 발행 대상
ALTER TABLE trades ADD COLUMN event_published_at TIMESTAMP;

-- 이미 있는 체결은 보낸 것으로 둔다
UPDATE trades SET event_published_at = executed_at;

CREATE INDEX IF NOT EXISTS idx_trades_unpublished ON trades (created_at) WHERE event_published_at IS NULL;
