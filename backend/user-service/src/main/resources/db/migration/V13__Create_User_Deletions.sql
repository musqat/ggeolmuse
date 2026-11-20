-- 탈퇴한 사용자의 남은 정리(사용자 삭제 이벤트 · Keycloak 계정 삭제) 기록(끝난 행은 하루 뒤 지움)
CREATE TABLE IF NOT EXISTS user_deletions (
    keycloak_id         VARCHAR(255) PRIMARY KEY,
    deleted_at          TIMESTAMP    NOT NULL,
    event_published_at  TIMESTAMP,
    keycloak_deleted_at TIMESTAMP
);
