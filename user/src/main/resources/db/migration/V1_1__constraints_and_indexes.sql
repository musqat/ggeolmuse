-- USERS: 이메일 고유
create unique index if not exists ux_users_email on users(email);

-- ACCOUNT_HISTORY: 참조ID(멱등키) 고유
create unique index if not exists ux_acct_hist_reference on account_history(reference_id);

-- ACCOUNT_HISTORY: 계정+시간 조회용
create index if not exists ix_acct_hist_acc_time on account_history(account_id, created_at);

-- ACCOUNT: 같은 유저 안에서 계좌명 중복 방지
create unique index if not exists ux_account_user_name on account(user_id, account_name);
