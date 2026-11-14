-- 계좌 낙관적 잠금 버전(기존 행 0)
ALTER TABLE account ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
