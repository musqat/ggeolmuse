package com.muscat.trade.common.enums.response;

import com.muscat.commonlib.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TradeResponse implements ErrorCode {

  // === 클라이언트 오류 (400번대) ===
  INDEX_NOT_TRADABLE("지수는 사고팔 수 없습니다.", HttpStatus.BAD_REQUEST),
  MANUAL_PRICE_OUT_OF_RANGE("입력한 가격이 그 날짜 가격 범위를 벗어났습니다.", HttpStatus.BAD_REQUEST),
  INVALID_ACCOUNT_ID("계좌 ID가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
  INSUFFICIENT_BALANCE("잔액이 부족합니다.", HttpStatus.BAD_REQUEST),
  INSUFFICIENT_HOLDINGS("보유 수량이 부족합니다.", HttpStatus.BAD_REQUEST),
  INSUFFICIENT_SELLABLE_QUANTITY("매도 가능한 수량이 부족합니다.", HttpStatus.BAD_REQUEST),
  INVALID_IDEMPOTENCY_KEY("중복 요청 키는 64자 이하의 영문 · 숫자 · 하이픈입니다.", HttpStatus.BAD_REQUEST),
  INVALID_PERIOD("기간은 1M · 3M · 1Y · ALL 가운데 하나입니다.", HttpStatus.BAD_REQUEST),

  // === 인증/인가 오류 (401, 403) ===
  ACCOUNT_ACCESS_DENIED("계좌에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),

  // === 리소스 없음 (404) ===
  ACCOUNT_NOT_FOUND("계좌를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  TRADE_NOT_FOUND("거래 내역을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

  // === 데이터 검증 오류 (422) ===
  IDEMPOTENCY_KEY_REUSED("같은 중복 요청 키로 다른 요청을 보냈습니다.", HttpStatus.UNPROCESSABLE_ENTITY),
  FUTURE_DATE_NOT_ALLOWED("미래 날짜로는 거래할 수 없습니다.", HttpStatus.UNPROCESSABLE_ENTITY),
  HOLIDAY_TRADING_NOT_ALLOWED("휴일에는 거래할 수 없습니다.", HttpStatus.UNPROCESSABLE_ENTITY),

  // === 외부 서비스 오류 (502, 503) ===
  MARKET_DATA_SERVICE_ERROR("시장 데이터 서비스 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY),
  USER_SERVICE_ERROR("유저 서비스 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY),
  PRICE_DATA_UNAVAILABLE("가격 정보를 가져올 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE),

  // === 서버 내부 오류 (500) ===
  TRANSACTION_FAILED("거래 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  EVENT_PUBLISH_FAILED("이벤트 발행에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  DIVIDEND_PAYMENT_FAILED("배당금 지급에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

  private final String message;
  private final HttpStatus httpStatus;
}
