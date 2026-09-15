package com.muscat.user.common.enums.responses;

import com.muscat.commonlib.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AccountHistoryResponse implements ErrorCode {

  // 성공 응답 (200, 201)
  DEPOSIT_HISTORY_CREATED("입금 내역이 생성되었습니다.", HttpStatus.CREATED),
  EXCHANGE_HISTORY_CREATED("환전 내역이 생성되었습니다.", HttpStatus.CREATED),
  HISTORY_FOUND("거래 내역 조회가 완료되었습니다.", HttpStatus.OK),
  HISTORY_LIST_FOUND("거래 내역 목록 조회가 완료되었습니다.", HttpStatus.OK),

  // 400 Bad Request
  INVALID_TRANSACTION_AMOUNT("거래 금액이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
  INVALID_CURRENCY("지원하지 않는 통화입니다.", HttpStatus.BAD_REQUEST),
  INVALID_EXCHANGE_RATE("환율이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
  SAME_CURRENCY_EXCHANGE("동일한 통화로는 환전할 수 없습니다.", HttpStatus.BAD_REQUEST),
  INVALID_DATE_RANGE("유효하지 않은 날짜 범위입니다.", HttpStatus.BAD_REQUEST),

  // 404 Not Found
  HISTORY_NOT_FOUND("거래 내역을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

  // 409 Conflict
  DUPLICATE_TRANSACTION("이미 처리된 거래입니다.", HttpStatus.CONFLICT),

  // 500 Internal Server Error
  HISTORY_CREATION_FAILED("거래 내역 생성에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

  private final String message;
  private final HttpStatus httpStatus;
}
