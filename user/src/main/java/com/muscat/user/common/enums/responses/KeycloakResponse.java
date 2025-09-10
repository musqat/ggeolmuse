package com.muscat.user.common.enums.responses;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum KeycloakResponse {

  // 성공 응답
  TOKEN_REFRESHED("200", "토큰 갱신이 완료되었습니다.", HttpStatus.OK),
  USER_CREATED("201", "Keycloak 사용자가 생성되었습니다.", HttpStatus.CREATED),
  PASSWORD_CHANGED("200", "비밀번호 변경이 완료되었습니다.", HttpStatus.OK),
  USER_DELETED("200", "사용자 삭제가 완료되었습니다.", HttpStatus.OK),

  // 400 Bad Request
  INVALID_TOKEN_FORMAT("400", "잘못된 토큰 형식입니다.", HttpStatus.BAD_REQUEST),
  INVALID_CREDENTIALS("400", "인증 정보가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
  INVALID_USER_DATA("400", "사용자 데이터가 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
  WEAK_PASSWORD("400", "비밀번호가 보안 정책에 맞지 않습니다.", HttpStatus.BAD_REQUEST),

  // 401 Unauthorized
  TOKEN_EXPIRED("401", "토큰이 만료되었습니다.", HttpStatus.UNAUTHORIZED),
  INVALID_TOKEN("401", "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
  AUTHENTICATION_FAILED("401", "Keycloak 인증에 실패했습니다.", HttpStatus.UNAUTHORIZED),

  // 403 Forbidden
  INSUFFICIENT_PERMISSIONS("403", "권한이 부족합니다.", HttpStatus.FORBIDDEN),
  ACCOUNT_LOCKED("403", "계정이 잠겨있습니다.", HttpStatus.FORBIDDEN),
  ACCOUNT_DISABLED("403", "계정이 비활성화되어 있습니다.", HttpStatus.FORBIDDEN),

  // 404 Not Found
  USER_NOT_FOUND("404", "Keycloak에서 사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  REALM_NOT_FOUND("404", "지정된 영역을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

  // 409 Conflict
  USER_ALREADY_EXISTS("409", "이미 존재하는 사용자입니다.", HttpStatus.CONFLICT),
  EMAIL_ALREADY_TAKEN("409", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),

  // 422 Unprocessable Entity
  INVALID_EMAIL_FORMAT("422", "올바르지 않은 이메일 형식입니다.", HttpStatus.UNPROCESSABLE_ENTITY),
  PASSWORD_POLICY_VIOLATION("422", "비밀번호 정책에 위배됩니다.", HttpStatus.UNPROCESSABLE_ENTITY),

  // 429 Too Many Requests
  RATE_LIMIT_EXCEEDED("429", "요청 한도를 초과했습니다.", HttpStatus.TOO_MANY_REQUESTS),

  // 500 Internal Server Error
  USER_CREATE_FAILED("500", "계정 생성에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  LOGIN_FAILED("500", "로그인 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  TOKEN_PARSE_FAILED("500", "토큰 파싱에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  API_ERROR("500", "인증 서비스에 문제가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  PASSWORD_CHANGE_FAILED("500", "비밀번호 변경에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  USER_DELETE_FAILED("500", "계정 삭제에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  TOKEN_REFRESH_FAILED("500", "토큰 갱신에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  KEYCLOAK_CONNECTION_ERROR("500", "Keycloak 서버 연결에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  KEYCLOAK_CONFIGURATION_ERROR("500", "Keycloak 설정 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

  private final String code;
  private final String message;
  private final HttpStatus httpStatus;
}