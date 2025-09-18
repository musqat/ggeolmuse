package com.muscat.user.common.exceptions;

import com.muscat.commonlib.exception.BaseException;
import com.muscat.user.common.enums.responses.KeycloakResponse;
import org.springframework.http.HttpStatus;

/**
 * Keycloak 관련 예외
 */
public class KeycloakException extends BaseException {

  private final HttpStatus httpStatus;

  public KeycloakException(KeycloakResponse response) {
    super(response.getCode(), response.getMessage());
    this.httpStatus = response.getHttpStatus();
  }


  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}