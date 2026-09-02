-- 1. Backtest History 인덱스
-- 사용자의 백테스트 이력 (가장 일반적인 쿼리)
CREATE INDEX IF NOT EXISTS idx_backtest_user_created ON backtest_history(user_id, created_at DESC);

-- 백테스트 유형 필터링 및 시간 범위
CREATE INDEX IF NOT EXISTS idx_backtest_type_created ON backtest_history(backtest_type, created_at DESC);

-- 사용자의 특정 백테스트 유형 쿼리
CREATE INDEX IF NOT EXISTS idx_backtest_user_type ON backtest_history(user_id, backtest_type, created_at DESC);

-- ID로 백테스트 조회 (primary key 이미 존재, 분석용 covering index 추가)
CREATE INDEX IF NOT EXISTS idx_backtest_created_type ON backtest_history(created_at DESC, backtest_type);

-- FX rate mode 필터링 (통화별 백테스트)
CREATE INDEX IF NOT EXISTS idx_backtest_fx_mode ON backtest_history(fx_rate_mode, created_at DESC);

-- 2. Investment Backtest Result 인덱스
-- 사용자의 캐시된 결과 조회 (user_id에 이미 UNIQUE 존재)
-- 분석용 covering index 추가
CREATE INDEX IF NOT EXISTS idx_investment_result_updated ON investment_backtest_result(updated_at DESC);

-- 최근 계산 타임스탬프 쿼리
CREATE INDEX IF NOT EXISTS idx_investment_result_calculated ON investment_backtest_result(calculated_at DESC);
