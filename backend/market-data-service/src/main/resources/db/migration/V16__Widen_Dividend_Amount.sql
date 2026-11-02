-- 역분할로 커진 과거 배당이 들어가게 배당 금액 정밀도 확대(테이블 재작성 없음)
ALTER TABLE dividend ALTER COLUMN amount SET DATA TYPE NUMERIC(24,8);
