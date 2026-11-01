
-- 관리자 작업의 진행 중 여부 · 마지막 상태(파드 사이 공유)
CREATE TABLE admin_job_state (
    name         VARCHAR(64) NOT NULL,
    running      BOOLEAN     NOT NULL,
    heartbeat_at TIMESTAMP   NOT NULL,
    detail       TEXT,
    PRIMARY KEY (name)
);
