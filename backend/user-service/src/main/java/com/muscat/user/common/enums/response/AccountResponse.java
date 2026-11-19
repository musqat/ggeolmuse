package com.muscat.user.common.enums.response;

import com.muscat.commonlib.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AccountResponse implements ErrorCode {

  // 400 Bad Request
  INVALID_DEPOSIT_AMOUNT("입금액이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
  INSUFFICIENT_BALANCE("잔액이 부족합니다.", HttpStatus.BAD_REQUEST),
  INSUFFICIENT_USD_BALANCE("USD 잔액이 부족합니다.", HttpStatus.BAD_REQUEST),
  INVALID_EXCHANGE_RATE("환율이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
  INVALID_CURRENCY("지원하지 않는 통화입니다.", HttpStatus.BAD_REQUEST),
  INVALID_TRANSACTION_TYPE("유효하지 않은 거래 타입입니다.", HttpStatus.BAD_REQUEST),
  INVALID_IDEMPOTENCY_KEY("중복 요청 키는 64자 이하의 영문 · 숫자 · 하이픈입니다.", HttpStatus.BAD_REQUEST),

  // 403 Forbidden
  ACCOUNT_ACCESS_DENIED("계좌에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),

  // 404 Not Found
  ACCOUNT_NOT_FOUND("계좌를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

  // 409 Conflict
  DUPLICATE_ACCOUNT_NAME("이미 사용중인 계좌명입니다.", HttpStatus.CONFLICT),

  // 422 Unprocessable Entity
  IDEMPOTENCY_KEY_REUSED("같은 중복 요청 키로 다른 요청을 보냈습니다.", HttpStatus.UNPROCESSABLE_ENTITY),

  // 500 Internal Server Error
  ACCOUNT_CREATION_FAILED("계좌 생성에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

  // 503 Service Unavailable
  EXCHANGE_RATE_SERVICE_ERROR("환율을 불러오지 못했습니다.", HttpStatus.SERVICE_UNAVAILABLE);

  private final String message;
  private final HttpStatus httpStatus;
}
