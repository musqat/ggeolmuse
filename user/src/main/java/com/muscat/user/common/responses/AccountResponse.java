package com.muscat.user.common.responses;

import com.muscat.user.common.enums.BaseResponseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AccountResponse implements BaseResponseEnum {

  // 성공 응답 (200, 201)
  ACCOUNT_CREATED("201", "계좌가 생성되었습니다."),
  ACCOUNT_FOUND("200", "계좌 조회가 완료되었습니다."),
  DEPOSIT_SUCCESS("200", "입금이 완료되었습니다."),
  ACCOUNT_UPDATED("200", "계좌 정보가 수정되었습니다."),
  ACCOUNT_DELETED("200", "계좌가 삭제되었습니다."),
  EXCHANGE_SUCCESS("200", "환전이 완료되었습니다."),

  // 400 Bad Request
  INVALID_ACCOUNT_NAME("400", "계좌명을 확인해주세요."),
  INVALID_DEPOSIT_AMOUNT("400", "입금액이 유효하지 않습니다."),
  INVALID_COMMISSION_RATE("400", "수수료율이 유효하지 않습니다."),
  INSUFFICIENT_BALANCE("400", "잔액이 부족합니다."),
  INVALID_EXCHANGE_RATE("400", "환율이 유효하지 않습니다."),
  MAX_ACCOUNT_LIMIT_EXCEEDED("400", "계좌 생성 한도를 초과했습니다."),
  INVALID_CURRENCY("400", "지원하지 않는 통화입니다."),

  // 403 Forbidden
  ACCOUNT_ACCESS_DENIED("403", "계좌에 접근할 권한이 없습니다."),

  // 404 Not Found
  ACCOUNT_NOT_FOUND("404", "계좌를 찾을 수 없습니다."),

  // 409 Conflict
  DUPLICATE_ACCOUNT_NAME("409", "이미 사용중인 계좌명입니다."),

  // 500 Internal Server Error
  ACCOUNT_CREATION_FAILED("500", "계좌 생성에 실패했습니다."),
  EXCHANGE_RATE_SERVICE_ERROR("500", "환율 서비스에 문제가 발생했습니다.");

  private final String code;
  private final String message;

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.valueOf(Integer.parseInt(code));
  }
}