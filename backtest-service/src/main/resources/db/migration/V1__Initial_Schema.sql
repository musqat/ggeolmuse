-- Backtest History table
CREATE TABLE IF NOT EXISTS backtest_history (
    backtest_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255),
    backtest_type VARCHAR(50) NOT NULL,
    request_params VARCHAR(2000),
    fx_rate_mode VARCHAR(20),
    created_at TIMESTAMP NOT NULL
);

-- Investment Backtest Result table
CREATE TABLE IF NOT EXISTS investment_backtest_result (
    result_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) UNIQUE NOT NULL,
    result_data TEXT,
    calculated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
