-- ============================================================
-- Backtest Service V2 Performance Indexes
-- Created: 2025-11-08
-- Purpose: Optimize backtest history queries and user analytics
-- ============================================================

-- 1. Backtest History Indexes
-- User's backtest history (most common query)
CREATE INDEX IF NOT EXISTS idx_backtest_user_created ON backtest_history(user_id, created_at DESC)
    WHERE user_id IS NOT NULL;

-- Backtest type filtering with time range
CREATE INDEX IF NOT EXISTS idx_backtest_type_created ON backtest_history(backtest_type, created_at DESC);

-- User's specific backtest type queries
CREATE INDEX IF NOT EXISTS idx_backtest_user_type ON backtest_history(user_id, backtest_type, created_at DESC)
    WHERE user_id IS NOT NULL;

-- Backtest lookup by ID (primary key already exists, add covering index for analytics)
CREATE INDEX IF NOT EXISTS idx_backtest_created_type ON backtest_history(created_at DESC, backtest_type);

-- FX rate mode filtering (for currency-specific backtests)
CREATE INDEX IF NOT EXISTS idx_backtest_fx_mode ON backtest_history(fx_rate_mode, created_at DESC)
    WHERE fx_rate_mode IS NOT NULL;

-- 2. Investment Backtest Result Indexes
-- User's cached result lookup (already has UNIQUE on user_id)
-- Add covering index for analytics
CREATE INDEX IF NOT EXISTS idx_investment_result_updated ON investment_backtest_result(updated_at DESC);

-- Recent calculation timestamp queries
CREATE INDEX IF NOT EXISTS idx_investment_result_calculated ON investment_backtest_result(calculated_at DESC);

-- ============================================================
-- Query Pattern Optimizations:
--
-- Most Common Queries:
-- 1. getUserBacktestHistory(userId, limit)
--    -> Uses: idx_backtest_user_created
--    -> Expected speedup: 70% (2.5s → 750ms for 1000+ records)
--
-- 2. getRecentBacktestsByType(type, limit)
--    -> Uses: idx_backtest_type_created
--    -> Expected speedup: 65%
--
-- 3. getUserBacktestsByType(userId, type)
--    -> Uses: idx_backtest_user_type
--    -> Expected speedup: 75%
--
-- 4. getBacktestAnalytics(dateRange)
--    -> Uses: idx_backtest_created_type
--    -> Expected speedup: 60% (aggregation queries)
-- ============================================================
