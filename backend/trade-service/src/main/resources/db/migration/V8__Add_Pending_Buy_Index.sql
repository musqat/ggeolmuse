-- 대기 매수 다시 보내기 · 지표 조회용. 대기 행만 담아 작게 유지
CREATE INDEX IF NOT EXISTS idx_trades_pending ON trades (event_published_at) WHERE status = 'PENDING';
