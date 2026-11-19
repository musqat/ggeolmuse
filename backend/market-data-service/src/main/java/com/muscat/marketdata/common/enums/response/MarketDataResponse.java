package com.muscat.marketdata.common.enums.response;

import com.muscat.commonlib.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MarketDataResponse implements ErrorCode {

  // 클라이언트 오류
  INVALID_DATE_RANGE("유효하지 않은 날짜 범위입니다", HttpStatus.BAD_REQUEST),
  INVALID_SORT("유효하지 않은 정렬 기준입니다", HttpStatus.BAD_REQUEST),
  ASSET_NAME_REQUIRED("종목 이름이 비어 있습니다", HttpStatus.BAD_REQUEST),
  ASSET_ALREADY_EXISTS("이미 등록된 종목입니다", HttpStatus.CONFLICT),

  // 리소스 없음
  SYMBOL_NOT_FOUND("종목을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
  PRICE_DATA_NOT_FOUND("가격 데이터를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
  FX_RATE_NOT_FOUND("환율 데이터를 찾을 수 없습니다", HttpStatus.NOT_FOUND),

  // 외부 서비스 오류 - 실제 사용되는 것들
  ALPHA_VANTAGE_ERROR("Alpha Vantage 서비스 오류", HttpStatus.BAD_GATEWAY),
  YAHOO_NO_DATA("Yahoo Finance 응답에 시세가 없습니다", HttpStatus.NOT_FOUND),
  YAHOO_RESPONSE_INVALID("Yahoo Finance 응답을 읽지 못했습니다", HttpStatus.BAD_GATEWAY),
  ALPHA_VANTAGE_RATE_LIMITED("Alpha Vantage 요청 한도를 넘었습니다", HttpStatus.SERVICE_UNAVAILABLE),
  MARKET_CAP_UNAVAILABLE("시가총액을 가져오지 못했습니다", HttpStatus.BAD_GATEWAY),
  DATA_SOURCE_UNAVAILABLE("시세 공급원이 설정되지 않았습니다", HttpStatus.SERVICE_UNAVAILABLE),

  // 서버 내부 오류
  FIXED_QUOTES_UNREADABLE("고정 시세 파일을 읽지 못했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
  ADMIN_JOB_STATE_UNREADABLE("관리자 작업 상태를 읽지 못했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
  ADMIN_JOB_STATE_UNWRITABLE("관리자 작업 상태를 쓰지 못했습니다", HttpStatus.INTERNAL_SERVER_ERROR);

  private final String message;
  private final HttpStatus httpStatus;
}
