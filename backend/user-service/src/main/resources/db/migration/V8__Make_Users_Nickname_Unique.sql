-- 닉네임을 사용자마다 다르게(대소문자 구분). 빈 닉네임과 겹친 닉네임의 나중 가입 쪽은 비움
UPDATE users SET nickname = NULL WHERE trim(nickname) = '';
UPDATE users u SET nickname = NULL
WHERE u.nickname IS NOT NULL
  AND EXISTS (SELECT 1 FROM users o WHERE o.nickname = u.nickname AND o.id < u.id);
ALTER TABLE users ADD CONSTRAINT uk_users_nickname UNIQUE (nickname);
