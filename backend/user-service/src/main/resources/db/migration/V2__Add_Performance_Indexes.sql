-- 1. Users 테이블 인덱스
-- Keycloak ID 조회 최적화 (JWT 인증)
CREATE INDEX IF NOT EXISTS idx_users_keycloak_id ON users(keycloak_id);

-- 활성 사용자 쿼리 (분석용 last_login_at)
CREATE INDEX IF NOT EXISTS idx_users_last_login ON users(last_login_at DESC NULLS LAST);

-- 이메일 인증 상태 쿼리
CREATE INDEX IF NOT EXISTS idx_users_email_verified ON users(email_verified, created_at);

-- 소셜 프로바이더 조회 최적화
CREATE INDEX IF NOT EXISTS idx_users_provider ON users(provider, email);

-- 2. Account History 테이블 인덱스
-- 거래 유형 필터링 및 시간 범위
CREATE INDEX IF NOT EXISTS idx_acct_hist_type_time ON account_history(account_id, transaction_type, created_at DESC);

-- 환전 내역 쿼리
CREATE INDEX IF NOT EXISTS idx_acct_hist_currency ON account_history(account_id, from_currency, to_currency, created_at DESC);

-- Reference ID 조회 (멱등성 키 - 이미 unique, covering index 추가)
CREATE INDEX IF NOT EXISTS idx_acct_hist_ref_with_details ON account_history(reference_id, account_id, amount, created_at);

-- 3. 이메일 인증 토큰
-- 활성 토큰 조회 (만료되지 않은 토큰만)
CREATE INDEX IF NOT EXISTS idx_email_token_valid ON email_verification_tokens(token, expiry_date);

-- 사용자의 대기 중인 토큰
CREATE INDEX IF NOT EXISTS idx_email_token_user ON email_verification_tokens(user_id, created_at DESC);

-- 4. 비밀번호 재설정 토큰
-- 미사용 토큰 조회 최적화 (이미 idx_password_reset_token 존재)
CREATE INDEX IF NOT EXISTS idx_pwd_reset_valid ON password_reset_tokens(token, used, expiry_date);
