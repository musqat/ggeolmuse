-- 전 기간을 다시 받았을 때 받은 봉 중 가장 최근 날짜. 찾기는 이 날짜보다 뒤에 난 분할만 본다
ALTER TABLE asset ADD COLUMN IF NOT EXISTS full_history_through DATE;
