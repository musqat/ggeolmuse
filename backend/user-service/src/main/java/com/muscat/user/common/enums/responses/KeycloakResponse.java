package com.muscat.user.common.enums.responses;

import com.muscat.commonlib.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum KeycloakResponse implements ErrorCode {

  // 500 Internal Server Error - 실제 사용되는 오류들
  API_ERROR("Keycloak API 호출 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  USER_CREATE_FAILED("Keycloak 사용자 생성에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  USER_DELETE_FAILED("Keycloak 사용자 삭제에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

  // 401 Unauthorized - 토큰 갱신 실패
  TOKEN_REFRESH_FAILED("토큰 갱신에 실패했습니다. 다시 로그인해주세요.", HttpStatus.UNAUTHORIZED);

  private final String message;
  private final HttpStatus httpStatus;
}