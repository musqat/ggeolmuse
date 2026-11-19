package com.muscat.gateway.common.enums.response;

import org.springframework.http.HttpStatus;

/**
 * gateway 가 직접 내는 오류 코드(뒤 서비스 장애 · 시간 초과의 대체 응답)
 */
public enum GatewayResponse {

  SERVICE_UNAVAILABLE("요청하신 서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해주세요.", HttpStatus.SERVICE_UNAVAILABLE);

  private final String message;
  private final HttpStatus httpStatus;

  GatewayResponse(String message, HttpStatus httpStatus) {
    this.message = message;
    this.httpStatus = httpStatus;
  }

  public String getMessage() {
    return message;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}
