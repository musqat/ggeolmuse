package com.muscat.user.common.exceptions;

import com.muscat.user.common.enums.BaseResponseEnum;


/**
 * Keycloak 관련 예외
 */
public class KeycloakException extends BusinessException {
  public KeycloakException(BaseResponseEnum errorCode) {
    super(errorCode);
  }

  public KeycloakException(BaseResponseEnum errorCode, String customMessage) {
    super(errorCode, customMessage);
  }

  public KeycloakException(BaseResponseEnum errorCode, Throwable cause) {
    super(errorCode, cause);
  }
}