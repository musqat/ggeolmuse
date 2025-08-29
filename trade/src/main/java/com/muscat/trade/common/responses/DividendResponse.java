package com.muscat.trade.common.responses;

import com.muscat.trade.common.enums.BaseResponseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum DividendResponse implements BaseResponseEnum {

  // 성공 응답 (200)
  DIVIDEND_CALCULATED("200", "배당금 계산이 완료되었습니다."),
  DIVIDEND_PROCESSED("200", "배당금 처리가 완료되었습니다."),
  DIVIDEND_HISTORY_FOUND("200", "배당 내역 조회가 완료되었습니다."),
  DIVIDEND_SUMMARY_FOUND("200", "배당 요약 조회가 완료되었습니다."),
  DIVIDEND_APPLIED("200", "배당금이 계좌에 입금되었습니다."),
  DIVIDEND_UPDATED("200", "배당 정보가 업데이트되었습니다."),
  ALL_DIVIDENDS_PROCESSED("200", "모든 배당금 처리가 완료되었습니다."),

  // 400 Bad Request
  INVALID_DIVIDEND_DATE("400", "배당 기준일이 유효하지 않습니다."),
  INVALID_DIVIDEND_AMOUNT("400", "배당금액이 유효하지 않습니다."),
  INVALID_DIVIDEND_RATE("400", "배당률이 유효하지 않습니다."),
  DIVIDEND_ALREADY_PROCESSED("400", "이미 처리된 배당입니다."),
  NO_HOLDINGS_FOR_DIVIDEND("400", "배당 기준일에 보유종목이 없습니다."),
  INVALID_RECORD_DATE("400", "배당 기준일이 과거여야 합니다."),
  DIVIDEND_PERIOD_INVALID("400", "배당 조회 기간이 유효하지 않습니다."),

  // 403 Forbidden
  DIVIDEND_ACCESS_DENIED("403", "배당 정보에 접근할 권한이 없습니다."),

  // 404 Not Found
  DIVIDEND_NOT_FOUND("404", "배당 정보를 찾을 수 없습니다."),
  DIVIDEND_HISTORY_NOT_FOUND("404", "배당 내역을 찾을 수 없습니다."),
  SYMBOL_DIVIDEND_NOT_FOUND("404", "해당 종목의 배당 정보를 찾을 수 없습니다."),

  // 409 Conflict
  DIVIDEND_ALREADY_CALCULATED("409", "이미 계산된 배당입니다."),

  // 503 Service Unavailable  
  DIVIDEND_DATA_SERVICE_ERROR("503", "배당 데이터 서비스에 문제가 발생했습니다."),

  // 500 Internal Server Error
  DIVIDEND_CALCULATION_FAILED("500", "배당금 계산에 실패했습니다."),
  DIVIDEND_PROCESSING_FAILED("500", "배당금 처리에 실패했습니다."),
  DIVIDEND_UPDATE_FAILED("500", "배당 정보 업데이트에 실패했습니다."),
  BATCH_DIVIDEND_PROCESSING_FAILED("500", "일괄 배당 처리에 실패했습니다.");

  private final String code;
  private final String message;

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.valueOf(Integer.parseInt(code));
  }
}