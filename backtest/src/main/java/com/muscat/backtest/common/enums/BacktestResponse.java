package com.muscat.backtest.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum BacktestResponse {

  // === 성공 응답 (200번대) ===
  SIMULATION_COMPLETED("200", "백테스트 시뮬레이션이 완료되었습니다.", HttpStatus.OK),
  INVESTMENT_ANALYSIS_COMPLETED("200", "투자 분석이 완료되었습니다.", HttpStatus.OK),
  STRATEGY_BACKTEST_COMPLETED("200", "전략 백테스트가 완료되었습니다.", HttpStatus.OK),
  COMPARISON_ANALYSIS_COMPLETED("200", "비교 분석이 완료되었습니다.", HttpStatus.OK),
  PORTFOLIO_ANALYSIS_COMPLETED("200", "포트폴리오 분석이 완료되었습니다.", HttpStatus.OK),
  PERFORMANCE_METRICS_CALCULATED("200", "성과 지표 계산이 완료되었습니다.", HttpStatus.OK),
  BACKTEST_HISTORY_RETRIEVED("200", "백테스트 이력 조회가 완료되었습니다.", HttpStatus.OK),
  RESULTS_SAVED("201", "백테스트 결과가 저장되었습니다.", HttpStatus.CREATED),

  // === 클라이언트 오류 (400번대) ===
  INVALID_REQUEST("400", "잘못된 백테스트 요청입니다.", HttpStatus.BAD_REQUEST),
  INVALID_SYMBOL("400", "유효하지 않은 종목 코드입니다.", HttpStatus.BAD_REQUEST),
  INVALID_DATE_RANGE("400", "유효하지 않은 날짜 범위입니다.", HttpStatus.BAD_REQUEST),
  INVALID_DATE_FORMAT("400", "날짜 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
  START_DATE_AFTER_END_DATE("400", "시작 날짜는 종료 날짜보다 이전이어야 합니다.", HttpStatus.BAD_REQUEST),
  FUTURE_DATE_NOT_ALLOWED("400", "미래 날짜로는 백테스트할 수 없습니다.", HttpStatus.BAD_REQUEST),
  INVALID_INVESTMENT_AMOUNT("400", "유효하지 않은 투자 금액입니다.", HttpStatus.BAD_REQUEST),
  INVALID_STRATEGY_TYPE("400", "유효하지 않은 투자 전략 유형입니다.", HttpStatus.BAD_REQUEST),
  INVALID_COMPARISON_TYPE("400", "유효하지 않은 비교 유형입니다.", HttpStatus.BAD_REQUEST),
  INVALID_TRADING_FEE("400", "거래 수수료는 0 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
  INVALID_USER_ID("400", "유효하지 않은 사용자 ID입니다.", HttpStatus.BAD_REQUEST),
  MINIMUM_INVESTMENT_NOT_MET("400", "최소 투자 금액을 충족하지 않습니다.", HttpStatus.BAD_REQUEST),
  MAXIMUM_INVESTMENT_EXCEEDED("400", "최대 투자 금액을 초과했습니다.", HttpStatus.BAD_REQUEST),
  DATE_RANGE_TOO_LONG("400", "백테스트 기간이 너무 깁니다.", HttpStatus.BAD_REQUEST),
  DATE_RANGE_TOO_SHORT("400", "백테스트 기간이 너무 짧습니다.", HttpStatus.BAD_REQUEST),
  WEEKEND_DATE_NOT_ALLOWED("400", "주말 날짜는 백테스트 시작/종료일로 설정할 수 없습니다.", HttpStatus.BAD_REQUEST),
  HOLIDAY_DATE_NOT_ALLOWED("400", "휴일 날짜는 백테스트 시작/종료일로 설정할 수 없습니다.", HttpStatus.BAD_REQUEST),

  // === 전략별 검증 오류 (400) ===
  DCA_MONTHLY_AMOUNT_REQUIRED("400", "정액적립식 전략은 월 투자금액이 필수입니다.", HttpStatus.BAD_REQUEST),
  DCA_INVALID_MONTHLY_AMOUNT("400", "월 투자금액은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST),
  CONDITIONAL_DROP_RATE_REQUIRED("400", "조건적 매수 전략은 하락률이 필수입니다.", HttpStatus.BAD_REQUEST),
  CONDITIONAL_INVALID_DROP_RATE("400", "하락률은 0%와 100% 사이여야 합니다.", HttpStatus.BAD_REQUEST),
  CONDITIONAL_TOTAL_AMOUNT_REQUIRED("400", "조건적 매수 전략은 총 투자금액이 필수입니다.", HttpStatus.BAD_REQUEST),

  // === 전략 관련 검증 오류 (기존 코드 호환성) ===
  STRATEGY_REQUEST_NULL("400", "전략 요청 정보는 필수입니다.", HttpStatus.BAD_REQUEST),
  STRATEGY_SYMBOL_REQUIRED("400", "종목 코드는 필수입니다.", HttpStatus.BAD_REQUEST),
  STRATEGY_START_DATE_REQUIRED("400", "시작일은 필수입니다.", HttpStatus.BAD_REQUEST),
  STRATEGY_END_DATE_REQUIRED("400", "종료일은 필수입니다.", HttpStatus.BAD_REQUEST),
  STRATEGY_DATE_RANGE_INVALID("400", "시작일은 종료일보다 이전이어야 합니다.", HttpStatus.BAD_REQUEST),
  STRATEGY_TYPE_MISMATCH("400", "지원하지 않는 전략 타입입니다.", HttpStatus.BAD_REQUEST),
  STRATEGY_MONTHLY_AMOUNT_REQUIRED("400", "월 투자금액은 필수입니다.", HttpStatus.BAD_REQUEST),
  STRATEGY_TOTAL_INVESTMENT_REQUIRED("400", "총 투자금액은 필수입니다.", HttpStatus.BAD_REQUEST),
  STRATEGY_DROP_PERCENTAGE_REQUIRED("400", "하락률 조건은 필수입니다.", HttpStatus.BAD_REQUEST),

  // === 비교 분석 검증 오류 (400) ===
  COMPARISON_SYMBOLS_REQUIRED("400", "종목 비교는 최소 2개의 종목이 필요합니다.", HttpStatus.BAD_REQUEST),
  COMPARISON_TOO_MANY_SYMBOLS("400", "한 번에 비교 가능한 종목 수를 초과했습니다.", HttpStatus.BAD_REQUEST),
  COMPARISON_STRATEGIES_REQUIRED("400", "전략 비교는 최소 2개의 전략이 필요합니다.", HttpStatus.BAD_REQUEST),
  COMPARISON_TIMING_REQUIRED("400", "타이밍 비교는 최소 2개의 시점이 필요합니다.", HttpStatus.BAD_REQUEST),
  COMPARISON_SAME_STRATEGY("400", "동일한 전략은 비교할 수 없습니다.", HttpStatus.BAD_REQUEST),
  COMPARISON_SAME_TIMING("400", "동일한 시점은 비교할 수 없습니다.", HttpStatus.BAD_REQUEST),
  COMPARISON_REQUEST_TYPE_MISMATCH("400", "비교 요청 타입이 일치하지 않습니다.", HttpStatus.BAD_REQUEST),

  // === 인증/인가 오류 (401, 403) ===
  AUTHENTICATION_REQUIRED("401", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
  INVALID_TOKEN("401", "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
  TOKEN_EXPIRED("401", "토큰이 만료되었습니다.", HttpStatus.UNAUTHORIZED),
  BACKTEST_ACCESS_DENIED("403", "백테스트에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),
  PREMIUM_FEATURE_REQUIRED("403", "프리미엄 기능입니다. 업그레이드가 필요합니다.", HttpStatus.FORBIDDEN),
  USER_SUSPENDED("403", "사용자 계정이 정지되어 백테스트를 사용할 수 없습니다.", HttpStatus.FORBIDDEN),

  // === 리소스 없음 (404) ===
  DATA_NOT_FOUND("404", "백테스트에 필요한 데이터를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  SYMBOL_NOT_FOUND("404", "해당 종목을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  STOCK_DATA_NOT_FOUND("404", "주가 데이터를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  FX_RATE_DATA_NOT_FOUND("404", "환율 데이터를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  DIVIDEND_DATA_NOT_FOUND("404", "배당 데이터를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  BACKTEST_RESULT_NOT_FOUND("404", "백테스트 결과를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  PORTFOLIO_DATA_NOT_FOUND("404", "포트폴리오 데이터를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  USER_NOT_FOUND("404", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

  // === 충돌 오류 (409) ===
  BACKTEST_IN_PROGRESS("409", "이미 백테스트가 진행 중입니다.", HttpStatus.CONFLICT),
  DUPLICATE_BACKTEST_REQUEST("409", "중복된 백테스트 요청입니다.", HttpStatus.CONFLICT),
  INSUFFICIENT_DATA("409", "데이터가 부족하여 백테스트를 수행할 수 없습니다.", HttpStatus.CONFLICT),
  MARKET_CLOSED_PERIOD("409", "해당 기간은 시장이 휴장이어서 백테스트할 수 없습니다.", HttpStatus.CONFLICT),

  // === 데이터 처리 오류 (422) ===
  INCONSISTENT_DATA("422", "일관성이 없는 데이터로 인해 백테스트할 수 없습니다.", HttpStatus.UNPROCESSABLE_ENTITY),
  MISSING_PRICE_DATA("422", "필요한 가격 데이터가 누락되었습니다.", HttpStatus.UNPROCESSABLE_ENTITY),
  INVALID_PRICE_DATA("422", "유효하지 않은 가격 데이터입니다.", HttpStatus.UNPROCESSABLE_ENTITY),
  MISSING_DIVIDEND_DATA("422", "배당 데이터가 누락되었습니다.", HttpStatus.UNPROCESSABLE_ENTITY),
  INVALID_FX_RATE_DATA("422", "유효하지 않은 환율 데이터입니다.", HttpStatus.UNPROCESSABLE_ENTITY),

  // === 요청 한도 초과 (429) ===
  BACKTEST_RATE_LIMIT_EXCEEDED("429", "백테스트 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
  DAILY_BACKTEST_LIMIT_EXCEEDED("429", "일일 백테스트 한도를 초과했습니다.", HttpStatus.TOO_MANY_REQUESTS),
  CONCURRENT_BACKTEST_LIMIT("429", "동시 백테스트 실행 한도를 초과했습니다.", HttpStatus.TOO_MANY_REQUESTS),

  // === 외부 서비스 오류 (502, 503) ===
  MARKET_DATA_SERVICE_ERROR("502", "시장 데이터 서비스에 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY),
  TRADE_SERVICE_ERROR("502", "거래 서비스에 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY),
  USER_SERVICE_ERROR("502", "사용자 서비스에 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY),
  EXTERNAL_API_ERROR("502", "외부 API 서비스에 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY),
  MARKET_DATA_UNAVAILABLE("503", "시장 데이터 서비스를 사용할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE),
  TRADE_SERVICE_UNAVAILABLE("503", "거래 서비스를 사용할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE),
  BACKTEST_SERVICE_MAINTENANCE("503", "백테스트 서비스가 점검 중입니다.", HttpStatus.SERVICE_UNAVAILABLE),

  // === 게이트웨이 시간 초과 (504) ===
  BACKTEST_TIMEOUT("504", "백테스트 처리 시간이 초과되었습니다.", HttpStatus.GATEWAY_TIMEOUT),
  DATA_FETCH_TIMEOUT("504", "데이터 조회 시간이 초과되었습니다.", HttpStatus.GATEWAY_TIMEOUT),
  EXTERNAL_API_TIMEOUT("504", "외부 API 응답 시간이 초과되었습니다.", HttpStatus.GATEWAY_TIMEOUT),

  // === 서버 내부 오류 (500) ===
  CALCULATION_ERROR("500", "백테스트 계산 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  DIVIDEND_CALCULATION_ERROR("500", "배당금 계산 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  RETURN_CALCULATION_ERROR("500", "수익률 계산 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  FX_CONVERSION_ERROR("500", "환율 변환 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  SHARES_CALCULATION_ERROR("500", "주식 수량 계산 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  PORTFOLIO_CALCULATION_ERROR("500", "포트폴리오 계산 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  PERFORMANCE_CALCULATION_ERROR("500", "성과 지표 계산 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  STRATEGY_EXECUTION_ERROR("500", "투자 전략 실행 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  DATA_PROCESSING_ERROR("500", "데이터 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  DATABASE_ERROR("500", "데이터베이스 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  RESULT_SAVE_ERROR("500", "백테스트 결과 저장 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  CONFIGURATION_ERROR("500", "백테스트 설정 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  MEMORY_ERROR("500", "메모리 부족으로 백테스트를 완료할 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  THREAD_POOL_ERROR("500", "백테스트 처리 스레드 풀 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  INTERNAL_SERVER_ERROR("500", "백테스트 서비스에 예기치 못한 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

  private final String code;
  private final String message;
  private final HttpStatus httpStatus;
}