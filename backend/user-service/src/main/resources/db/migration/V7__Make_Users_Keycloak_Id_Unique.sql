-- 같은 Keycloak 사용자의 행이 둘 생기지 않게. V2 의 일반 인덱스는 UNIQUE 가 대신함
ALTER TABLE users ALTER COLUMN keycloak_id SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uk_users_keycloak_id UNIQUE (keycloak_id);
DROP INDEX IF EXISTS idx_users_keycloak_id;
