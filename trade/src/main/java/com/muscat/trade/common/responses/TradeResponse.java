package com.muscat.trade.common.responses;

import com.muscat.trade.common.enums.BaseResponseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TradeResponse implements BaseResponseEnum {

  // 성공 응답 (200, 201)
  TRADE_BUY_SUCCESS("200", "매수 주문이 성공적으로 처리되었습니다."),
  TRADE_SELL_SUCCESS("200", "매도 주문이 성공적으로 처리되었습니다."),
  TRADE_HISTORY_FOUND("200", "거래 내역 조회가 완료되었습니다."),
  TRADE_DETAIL_FOUND("200", "거래 상세 조회가 완료되었습니다."),
  TRADE_VALIDATION_SUCCESS("200", "거래 가능 여부 확인이 완료되었습니다."),

  // 400 Bad Request
  INVALID_TRADE_QUANTITY("400", "거래 수량이 유효하지 않습니다."),
  INVALID_TRADE_PRICE("400", "거래 가격이 유효하지 않습니다."),
  INVALID_SYMBOL("400", "유효하지 않은 종목 심볼입니다."),
  INVALID_TRADE_DATE("400", "거래일이 유효하지 않습니다."),
  INSUFFICIENT_BALANCE("400", "계좌 잔액이 부족합니다."),
  INSUFFICIENT_HOLDINGS("400", "보유 수량이 부족합니다."),
  MARKET_CLOSED("400", "시장이 열려있지 않습니다."),
  MINIMUM_ORDER_NOT_MET("400", "최소 주문 수량에 미달됩니다."),

  // 403 Forbidden
  TRADE_ACCESS_DENIED("403", "거래에 접근할 권한이 없습니다."),

  // 404 Not Found
  TRADE_NOT_FOUND("404", "거래 내역을 찾을 수 없습니다."),
  ACCOUNT_NOT_FOUND("404", "계좌를 찾을 수 없습니다."),
  SYMBOL_NOT_FOUND("404", "종목을 찾을 수 없습니다."),

  // 409 Conflict
  DUPLICATE_TRADE("409", "중복 거래 요청입니다."),
  TRADE_ALREADY_EXECUTED("409", "이미 체결된 거래입니다."),

  // 503 Service Unavailable
  MARKET_DATA_SERVICE_ERROR("503", "시장 데이터 서비스에 문제가 발생했습니다."),
  USER_SERVICE_ERROR("503", "사용자 서비스에 문제가 발생했습니다."),

  // 500 Internal Server Error
  TRADE_EXECUTION_FAILED("500", "거래 처리 중 오류가 발생했습니다."),
  PRICE_CALCULATION_ERROR("500", "가격 계산 중 오류가 발생했습니다."),
  FEE_CALCULATION_ERROR("500", "수수료 계산 중 오류가 발생했습니다.");

  private final String code;
  private final String message;

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.valueOf(Integer.parseInt(code));
  }
}