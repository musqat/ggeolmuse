-- 쓰는 곳 없는 소셜 로그인 열 셋 삭제
ALTER TABLE users DROP COLUMN IF EXISTS social_id;
ALTER TABLE users DROP COLUMN IF EXISTS social_email;
ALTER TABLE users DROP COLUMN IF EXISTS profile_image_url;
