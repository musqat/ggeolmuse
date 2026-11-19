package com.muscat.user.common.enums.response;

import com.muscat.commonlib.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AccountHistoryResponse implements ErrorCode {

  // 400 Bad Request
  INVALID_CURRENCY("지원하지 않는 통화입니다.", HttpStatus.BAD_REQUEST),
  INVALID_EXCHANGE_RATE("환율이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
  SAME_CURRENCY_EXCHANGE("동일한 통화로는 환전할 수 없습니다.", HttpStatus.BAD_REQUEST),

  // 409 Conflict
  DUPLICATE_TRANSACTION("이미 처리된 거래입니다.", HttpStatus.CONFLICT);

  private final String message;
  private final HttpStatus httpStatus;
}
