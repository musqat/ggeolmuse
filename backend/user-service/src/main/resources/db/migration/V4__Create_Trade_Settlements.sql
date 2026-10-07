-- user-service 가 처리한 체결 이벤트. 같은 trade_id 는 한 번만 반영
CREATE TABLE IF NOT EXISTS trade_settlements (
    trade_id     BIGINT PRIMARY KEY,
    account_id   BIGINT,
    status       VARCHAR(20) NOT NULL,
    reason_code  VARCHAR(50),
    processed_at TIMESTAMP NOT NULL
);
