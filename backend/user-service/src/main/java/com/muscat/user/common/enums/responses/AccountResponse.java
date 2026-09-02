package com.muscat.user.common.enums.responses;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AccountResponse {

  // 성공 응답 (200, 201)
  ACCOUNT_CREATED("201", "계좌가 생성되었습니다.", HttpStatus.CREATED),
  ACCOUNT_FOUND("200", "계좌 조회가 완료되었습니다.", HttpStatus.OK),
  DEPOSIT_SUCCESS("200", "입금이 완료되었습니다.", HttpStatus.OK),
  ACCOUNT_UPDATED("200", "계좌 정보가 수정되었습니다.", HttpStatus.OK),
  ACCOUNT_DELETED("200", "계좌가 삭제되었습니다.", HttpStatus.OK),
  EXCHANGE_SUCCESS("200", "환전이 완료되었습니다.", HttpStatus.OK),
  UPDATE_SUCCESS("200", "계좌 업데이트가 완료되었습니다.", HttpStatus.OK),
  EXCHANGE_RATE_FOUND("200", "환율 조회가 완료되었습니다.", HttpStatus.OK),

  // 400 Bad Request
  INVALID_ACCOUNT_NAME("400", "계좌명을 확인해주세요.", HttpStatus.BAD_REQUEST),
  INVALID_DEPOSIT_AMOUNT("400", "입금액이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
  INVALID_COMMISSION_RATE("400", "수수료율이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
  INSUFFICIENT_BALANCE("400", "잔액이 부족합니다.", HttpStatus.BAD_REQUEST),
  INSUFFICIENT_USD_BALANCE("400", "USD 잔액이 부족합니다.", HttpStatus.BAD_REQUEST),
  INVALID_EXCHANGE_RATE("400", "환율이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
  MAX_ACCOUNT_LIMIT_EXCEEDED("400", "계좌 생성 한도를 초과했습니다.", HttpStatus.BAD_REQUEST),
  INVALID_CURRENCY("400", "지원하지 않는 통화입니다.", HttpStatus.BAD_REQUEST),
  INVALID_REQUEST("400", "잘못된 요청입니다.", HttpStatus.BAD_REQUEST),
  CANNOT_DELETE_ACCOUNT_WITH_BALANCE("400", "잔액이 있는 계좌는 삭제할 수 없습니다.", HttpStatus.BAD_REQUEST),
  INVALID_TRANSACTION_TYPE("400", "유효하지 않은 거래 타입입니다.", HttpStatus.BAD_REQUEST),
  INVALID_AMOUNT("400", "금액이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
  NEGATIVE_AMOUNT("400", "금액은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST),
  CURRENCY_MISMATCH("400", "통화가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),

  // 403 Forbidden
  ACCOUNT_ACCESS_DENIED("403", "계좌에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),

  // 404 Not Found
  ACCOUNT_NOT_FOUND("404", "계좌를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

  // 409 Conflict
  DUPLICATE_ACCOUNT_NAME("409", "이미 사용중인 계좌명입니다.", HttpStatus.CONFLICT),

  // 500 Internal Server Error
  ACCOUNT_CREATION_FAILED("500", "계좌 생성에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  EXCHANGE_RATE_SERVICE_ERROR("500", "환율 서비스에 문제가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

  private final String code;
  private final String message;
  private final HttpStatus httpStatus;
}