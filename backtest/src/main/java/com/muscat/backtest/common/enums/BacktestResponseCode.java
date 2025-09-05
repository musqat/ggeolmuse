package com.muscat.backtest.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BacktestResponseCode {

  // 2xx 성공 응답
  SUCCESS(200, "성공"),
  CREATED(201, "생성됨"),

  // 4xx 클라이언트 오류
  BAD_REQUEST(400, "잘못된 요청"),
  UNAUTHORIZED(401, "인증 실패"),
  FORBIDDEN(403, "접근 거부"),
  NOT_FOUND(404, "찾을 수 없음"),
  METHOD_NOT_ALLOWED(405, "허용되지 않은 메소드"),
  CONFLICT(409, "요청 충돌"),
  UNPROCESSABLE_ENTITY(422, "처리 불가능한 엔티티"),
  TOO_MANY_REQUESTS(429, "요청 수 초과"),

  // 5xx 서버 오류
  INTERNAL_ERROR(500, "서버 오류"),
  NOT_IMPLEMENTED(501, "미구현"),
  BAD_GATEWAY(502, "게이트웨이 오류"),
  SERVICE_UNAVAILABLE(503, "서비스 이용 불가"),
  GATEWAY_TIMEOUT(504, "게이트웨이 시간초과"),

  // 백테스팅 전용 응답
  SIMULATION_FAILED(400, "시뮬레이션 실행 실패"),
  INVESTMENT_FAILED(400, "투자 실행 실패"),
  STRATEGY_FAILED(400, "전략 실행 실패"),
  ANALYSIS_FAILED(400, "분석 실행 실패"),

  // 데이터 관련 오류 (404)
  DATA_NOT_FOUND(404, "데이터를 찾을 수 없음"),
  STOCK_DATA_NOT_FOUND(404, "주가 데이터를 찾을 수 없음"),
  FX_RATE_DATA_NOT_FOUND(404, "환율 데이터를 찾을 수 없음"),
  DIVIDEND_DATA_NOT_FOUND(404, "배당 데이터를 찾을 수 없음"),
  SYMBOL_NOT_FOUND(404, "종목을 찾을 수 없음"),

  // 외부 서비스 오류 (503)
  MARKET_DATA_ERROR(503, "시장 데이터 서비스 오류"),
  TRADE_SERVICE_ERROR(503, "거래 서비스 오류"),
  EXTERNAL_API_ERROR(503, "외부 API 연동 오류"),
  API_TIMEOUT_ERROR(504, "API 응답 시간 초과"),

  // 계산 및 처리 오류 (422)
  CALCULATION_ERROR(422, "계산 처리 오류"),
  DIVIDEND_CALCULATION_ERROR(422, "배당금 계산 오류"),
  RETURN_CALCULATION_ERROR(422, "수익률 계산 오류"),
  FX_CONVERSION_ERROR(422, "환율 변환 오류"),
  SHARES_CALCULATION_ERROR(422, "주식 수량 계산 오류"),

  // 입력값 검증 오류 (400)
  VALIDATION_ERROR(400, "입력 값 검증 실패"),
  INVALID_DATE_RANGE(400, "유효하지 않은 날짜 범위"),
  INVALID_INVESTMENT_AMOUNT(400, "유효하지 않은 투자 금액"),
  INVALID_SYMBOL(400, "유효하지 않은 종목 코드"),
  FUTURE_DATE_NOT_ALLOWED(400, "미래 날짜는 허용되지 않음"),

  // 비즈니스 로직 오류 (409)
  INSUFFICIENT_DATA(409, "데이터가 부족하여 분석할 수 없음"),
  MARKET_CLOSED(409, "시장 휴장일로 거래할 수 없음"),
  WEEKEND_TRADING_NOT_ALLOWED(409, "주말 거래는 허용되지 않음"),

  // 전략 패턴 관련 오류 (400)
  STRATEGY_REQUEST_NULL(400, "전략 요청 정보는 필수입니다"),
  STRATEGY_SYMBOL_REQUIRED(400, "종목 코드는 필수입니다"),
  STRATEGY_START_DATE_REQUIRED(400, "시작일은 필수입니다"),
  STRATEGY_END_DATE_REQUIRED(400, "종료일은 필수입니다"),
  STRATEGY_DATE_RANGE_INVALID(400, "시작일은 종료일보다 이전이어야 합니다"),
  STRATEGY_TYPE_MISMATCH(400, "지원하지 않는 전략 타입입니다"),
  STRATEGY_MONTHLY_AMOUNT_REQUIRED(400, "월 투자금액은 필수입니다"),
  STRATEGY_TOTAL_INVESTMENT_REQUIRED(400, "총 투자금액은 필수입니다"),
  STRATEGY_DROP_PERCENTAGE_REQUIRED(400, "하락률 조건은 필수입니다"),

  // 비교 분석 관련 오류 (400)
  COMPARISON_REQUEST_TYPE_MISMATCH(400, "비교 요청 타입이 일치하지 않습니다");

  private final int code;
  private final String message;

  public boolean isSuccess() {
    return code >= 200 && code < 300;
  }
}