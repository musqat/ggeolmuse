-- 백테스트 이력 요청 파라미터 칸을 길이 제한 없는 TEXT 로 변경
ALTER TABLE backtest_history ALTER COLUMN request_params TYPE TEXT;
