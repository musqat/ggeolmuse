package com.muscat.user.common.enums.responses;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SocialResponse {

  // 성공 응답
  GOOGLE_LOGIN_SUCCESS("200", "Google 로그인에 성공했습니다.", HttpStatus.OK),
  GOOGLE_USER_SYNCED("200", "Google 사용자 정보가 동기화되었습니다.", HttpStatus.OK),
  PROVIDERS_RETRIEVED("200", "소셜 로그인 제공자 목록을 조회했습니다.", HttpStatus.OK),
  ACCOUNT_LINKED("200", "소셜 계정 연결이 완료되었습니다.", HttpStatus.OK),
  ACCOUNT_UNLINKED("200", "소셜 계정 연결 해제가 완료되었습니다.", HttpStatus.OK),

  // 400 Bad Request
  INVALID_AUTHORIZATION_CODE("400", "유효하지 않은 인증 코드입니다.", HttpStatus.BAD_REQUEST),
  INVALID_ID_TOKEN("400", "유효하지 않은 ID 토큰입니다.", HttpStatus.BAD_REQUEST),
  EXPIRED_TOKEN("400", "토큰이 만료되었습니다.", HttpStatus.BAD_REQUEST),
  MISSING_REQUIRED_SCOPE("400", "필수 권한이 부족합니다.", HttpStatus.BAD_REQUEST),
  GOOGLE_LOGIN_FAILED("400", "Google 로그인에 실패했습니다.", HttpStatus.BAD_REQUEST),
  GOOGLE_TOKEN_EXCHANGE_FAILED("400", "Google 토큰 교환에 실패했습니다.", HttpStatus.BAD_REQUEST),
  GOOGLE_USER_INFO_FAILED("400", "Google 사용자 정보 조회에 실패했습니다.", HttpStatus.BAD_REQUEST),

  // 401 Unauthorized
  INVALID_GOOGLE_TOKEN("401", "유효하지 않은 Google 토큰입니다.", HttpStatus.UNAUTHORIZED),
  AUTHENTICATION_REQUIRED("401", "소셜 로그인 인증이 필요합니다.", HttpStatus.UNAUTHORIZED),

  // 403 Forbidden
  ACCOUNT_LINK_FORBIDDEN("403", "계정 연결 권한이 없습니다.", HttpStatus.FORBIDDEN),
  SOCIAL_LOGIN_DISABLED("403", "소셜 로그인이 비활성화되어 있습니다.", HttpStatus.FORBIDDEN),

  // 409 Conflict
  SOCIAL_EMAIL_CONFLICT("409", "동일한 이메일의 로컬 계정이 존재합니다. 계정 연결 기능을 이용해주세요.", HttpStatus.CONFLICT),
  ALREADY_LINKED_ACCOUNT("409", "이미 연결된 소셜 계정입니다.", HttpStatus.CONFLICT),

  // 500 Internal Server Error
  GOOGLE_API_ERROR("500", "Google API 서비스에 문제가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  SOCIAL_USER_CREATE_FAILED("500", "소셜 사용자 생성에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  ACCOUNT_LINK_FAILED("500", "계정 연결 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

  private final String code;
  private final String message;
  private final HttpStatus httpStatus;
}