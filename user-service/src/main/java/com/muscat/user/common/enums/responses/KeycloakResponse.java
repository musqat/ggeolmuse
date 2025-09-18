package com.muscat.user.common.enums.responses;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum KeycloakResponse {

  // 500 Internal Server Error - 실제 사용되는 오류들
  API_ERROR("500", "Keycloak API 호출 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  USER_CREATE_FAILED("500", "Keycloak 사용자 생성에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  USER_DELETE_FAILED("500", "Keycloak 사용자 삭제에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

  private final String code;
  private final String message;
  private final HttpStatus httpStatus;
}