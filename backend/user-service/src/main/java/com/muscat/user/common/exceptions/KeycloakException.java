package com.muscat.user.common.exceptions;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.user.common.enums.responses.KeycloakResponse;

/**
 * Keycloak 관련 예외
 */
public class KeycloakException extends BusinessException {

  public KeycloakException(KeycloakResponse response) {
    super(response);
  }
}
