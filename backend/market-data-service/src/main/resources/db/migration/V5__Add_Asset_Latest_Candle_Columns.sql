-- 요약 조회가 쓰는 최신 캔들 값. 운영 DB 에는 이미 있고 마이그레이션만으로 만든 DB 에 생긴다
ALTER TABLE asset ADD COLUMN IF NOT EXISTS latest_close DECIMAL(19,8);
ALTER TABLE asset ADD COLUMN IF NOT EXISTS latest_date DATE;
