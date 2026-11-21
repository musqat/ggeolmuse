package com.muscat.commonlib.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 서비스 공통 오류 코드(요청 형식 · 금액 검사 · 서비스 토큰 · 관리자 역할 · 예상하지 못한 오류)
 */
@Getter
@AllArgsConstructor
public enum CommonResponse implements ErrorCode {

  // 요청 형식
  VALIDATION_ERROR("입력값 검증에 실패했습니다", HttpStatus.BAD_REQUEST),
  MISSING_PARAMETER("필수 파라미터가 누락되었습니다", HttpStatus.BAD_REQUEST),
  INVALID_PARAMETER("요청 값이 올바르지 않습니다", HttpStatus.BAD_REQUEST),
  MALFORMED_REQUEST_BODY("요청 본문을 읽을 수 없습니다", HttpStatus.BAD_REQUEST),

  // 금액 · 환율 검사
  INVALID_AMOUNT("금액은 0보다 커야 합니다", HttpStatus.BAD_REQUEST),
  INVALID_CURRENCY("지원하지 않는 통화입니다", HttpStatus.BAD_REQUEST),
  INSUFFICIENT_AMOUNT("최소 환전 금액보다 적습니다", HttpStatus.BAD_REQUEST),
  EXCESSIVE_AMOUNT("1회 최대 환전 금액을 넘었습니다", HttpStatus.BAD_REQUEST),
  INSUFFICIENT_BALANCE("잔액이 부족합니다", HttpStatus.BAD_REQUEST),
  INVALID_EXCHANGE_RATE("환율은 0보다 커야 합니다", HttpStatus.BAD_REQUEST),
  EXCHANGE_RATE_OUT_OF_RANGE("환율이 정상 범위 밖입니다", HttpStatus.BAD_REQUEST),
  INVALID_BASE_VALUE("기준값은 null이거나 0이 될 수 없습니다", HttpStatus.BAD_REQUEST),

  // 서비스 간 호출 토큰
  SERVICE_TOKEN_MISSING("서비스 토큰 응답에 access_token 이 없습니다", HttpStatus.SERVICE_UNAVAILABLE),
  SERVICE_TOKEN_RETRY_WAIT("서비스 토큰 받기에 실패해 다시 받기를 기다리는 중입니다", HttpStatus.SERVICE_UNAVAILABLE),

  // 관리자 역할 확인
  ADMIN_ROLE_REQUIRED("관리자 권한이 없습니다", HttpStatus.FORBIDDEN),
  ADMIN_ROLE_UNAVAILABLE("관리자 역할을 확인할 수 없습니다. 잠시 후 다시 시도해 주세요", HttpStatus.SERVICE_UNAVAILABLE),

  // 해시
  HASH_UNAVAILABLE("SHA-256 해시를 만들 수 없습니다", HttpStatus.INTERNAL_SERVER_ERROR),

  // 예상하지 못한 오류
  INTERNAL_SERVER_ERROR("서비스에 문제가 발생했습니다. 잠시 후 다시 시도해 주세요", HttpStatus.INTERNAL_SERVER_ERROR);

  private final String message;
  private final HttpStatus httpStatus;
}
