-- 상장 목록이 준 이름을 따로 둠. 손으로 고친 이름과 구분해 목록 이름이 바뀐 종목만 따라 고침
-- 적용된 스키마에 한 번 더 돌려도 되는 꼴(마이그레이션 통합 테스트)
ALTER TABLE asset ADD COLUMN IF NOT EXISTS source_name VARCHAR(256);
UPDATE asset SET source_name = name WHERE source_name IS NULL;
