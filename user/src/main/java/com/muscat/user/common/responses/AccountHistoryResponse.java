package com.muscat.user.common.responses;

import com.muscat.user.common.enums.BaseResponseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AccountHistoryResponse implements BaseResponseEnum {

  // 성공 응답 (200, 201)
  DEPOSIT_HISTORY_CREATED("201", "입금 내역이 생성되었습니다."),
  EXCHANGE_HISTORY_CREATED("201", "환전 내역이 생성되었습니다."),
  HISTORY_FOUND("200", "거래 내역 조회가 완료되었습니다."),
  HISTORY_LIST_FOUND("200", "거래 내역 목록 조회가 완료되었습니다."),

  // 400 Bad Request
  INVALID_TRANSACTION_AMOUNT("400", "거래 금액이 유효하지 않습니다."),
  INVALID_CURRENCY("400", "지원하지 않는 통화입니다."),
  INVALID_EXCHANGE_RATE("400", "환율이 유효하지 않습니다."),
  SAME_CURRENCY_EXCHANGE("400", "동일한 통화로는 환전할 수 없습니다."),
  INVALID_DATE_RANGE("400", "유효하지 않은 날짜 범위입니다."),

  // 404 Not Found
  HISTORY_NOT_FOUND("404", "거래 내역을 찾을 수 없습니다."),

  // 409 Conflict
  DUPLICATE_TRANSACTION("409", "이미 처리된 거래입니다."),

  // 500 Internal Server Error
  HISTORY_CREATION_FAILED("500", "거래 내역 생성에 실패했습니다.");

  private final String code;
  private final String message;

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.valueOf(Integer.parseInt(code));
  }
}
