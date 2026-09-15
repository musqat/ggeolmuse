package com.muscat.user.common.enums.responses;

import com.muscat.commonlib.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserResponse implements ErrorCode {

  // 성공 응답 (200, 201)
  USER_CREATED("회원가입이 완료되었습니다.", HttpStatus.CREATED),
  EMAIL_VERIFIED("이메일 인증이 완료되었습니다.", HttpStatus.OK),
  LOGIN_SUCCESS("로그인에 성공했습니다.", HttpStatus.OK),
  PROFILE_FOUND("프로필 조회에 성공했습니다.", HttpStatus.OK),
  PROFILE_UPDATED("프로필이 수정되었습니다.", HttpStatus.OK),
  PASSWORD_CHANGED("비밀번호가 변경되었습니다.", HttpStatus.OK),
  ACCOUNT_DELETED("계정이 삭제되었습니다.", HttpStatus.OK),

  // 400 Bad Request
  INVALID_INPUT("입력값을 확인해주세요.", HttpStatus.BAD_REQUEST),
  EMAIL_NOT_VERIFIED("이메일 인증이 완료되지 않았습니다.", HttpStatus.BAD_REQUEST),
  INVALID_PASSWORD("비밀번호가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
  EMAIL_TOKEN_INVALID("유효하지 않은 인증 토큰입니다.", HttpStatus.BAD_REQUEST),
  EMAIL_TOKEN_EXPIRED("인증 토큰이 만료되었습니다.", HttpStatus.BAD_REQUEST),
  EMAIL_ALREADY_VERIFIED("이미 인증된 이메일입니다.", HttpStatus.BAD_REQUEST),
  PASSWORD_RESET_TOKEN_INVALID("유효하지 않은 비밀번호 재설정 토큰입니다.", HttpStatus.BAD_REQUEST),
  PASSWORD_RESET_TOKEN_EXPIRED("비밀번호 재설정 토큰이 만료되었거나 이미 사용되었습니다.", HttpStatus.BAD_REQUEST),
  ACCOUNT_DELETION_BLOCKED("잔액이 있는 계좌가 존재하여 계정을 삭제할 수 없습니다.", HttpStatus.BAD_REQUEST),

  // 401 Unauthorized
  AUTHENTICATION_FAILED("인증에 실패했습니다.", HttpStatus.UNAUTHORIZED),
  INVALID_CREDENTIALS("이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),

  // 403 Forbidden
  FORBIDDEN("접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
  ADMIN_REQUIRED("관리자 권한이 필요합니다.", HttpStatus.FORBIDDEN),

  // 404 Not Found
  USER_NOT_FOUND("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

  // 409 Conflict
  EMAIL_ALREADY_EXISTS("이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
  NICKNAME_ALREADY_EXISTS("이미 사용 중인 닉네임입니다.", HttpStatus.CONFLICT),

  // 422 Unprocessable Entity
  VALIDATION_FAILED("입력값 검증에 실패했습니다.", HttpStatus.UNPROCESSABLE_ENTITY),
  EMAIL_FORMAT_INVALID("올바른 이메일 형식이 아닙니다.", HttpStatus.UNPROCESSABLE_ENTITY),
  PASSWORD_PATTERN_INVALID("비밀번호는 8자 이상, 영문/숫자/특수문자를 포함해야 합니다.", HttpStatus.UNPROCESSABLE_ENTITY),
  NICKNAME_LENGTH_INVALID("닉네임은 2-20자 사이여야 합니다.", HttpStatus.UNPROCESSABLE_ENTITY),

  // 429 Too Many Requests
  EMAIL_SEND_LIMIT_EXCEEDED("이메일 발송 한도를 초과했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
  LOGIN_ATTEMPT_LIMIT_EXCEEDED("로그인 시도 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
  RATE_LIMIT_EXCEEDED("요청 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),

  // 500 Internal Server Error
  EMAIL_SEND_FAILED("이메일 발송에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  PASSWORD_CHANGE_FAILED("비밀번호 변경에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  PASSWORD_RESET_FAILED("비밀번호 재설정에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  GOOGLE_LOGIN_FAILED("Google 소셜 로그인 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  GOOGLE_USER_INFO_FAILED("Google 사용자 정보 조회에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  KEYCLOAK_TOKEN_FAILED("Keycloak 토큰 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  KEYCLOAK_USER_CREATE_FAILED("Keycloak 사용자 생성에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  DATABASE_ERROR("데이터베이스 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  EXTERNAL_SERVICE_ERROR("외부 서비스 연동 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  INTERNAL_SERVER_ERROR("서버에 문제가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

  private final String message;
  private final HttpStatus httpStatus;
  }