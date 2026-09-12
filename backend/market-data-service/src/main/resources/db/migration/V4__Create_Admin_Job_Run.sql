-- 관리자 작업의 마지막 실행 기록. 전체 다시 받기를 언제 했는지 화면에 보여준다
CREATE TABLE IF NOT EXISTS admin_job_run (
    name        VARCHAR(64) PRIMARY KEY,
    last_run_at TIMESTAMP   NOT NULL,
    published   INTEGER     NOT NULL
);
