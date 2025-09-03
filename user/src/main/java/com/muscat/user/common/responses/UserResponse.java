package com.muscat.user.common.responses;

import com.muscat.user.common.enums.BaseResponseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserResponse implements BaseResponseEnum {

  // 성공 응답 (200, 201)
  USER_CREATED("201", "회원가입이 완료되었습니다."),
  EMAIL_VERIFIED("200", "이메일 인증이 완료되었습니다."),
  LOGIN_SUCCESS("200", "로그인에 성공했습니다."),
  PROFILE_FOUND("200", "프로필 조회에 성공했습니다."),
  PROFILE_UPDATED("200", "프로필이 수정되었습니다."),
  PASSWORD_CHANGED("200", "비밀번호가 변경되었습니다."),
  ACCOUNT_DELETED("200", "계정이 삭제되었습니다."),

  // 400 Bad Request
  INVALID_INPUT("400", "입력값을 확인해주세요."),
  EMAIL_NOT_VERIFIED("400", "이메일 인증이 완료되지 않았습니다."),
  INVALID_PASSWORD("400", "비밀번호가 올바르지 않습니다."),
  EMAIL_TOKEN_INVALID("400", "유효하지 않은 인증 토큰입니다."),
  EMAIL_TOKEN_EXPIRED("400", "인증 토큰이 만료되었습니다."),
  EMAIL_ALREADY_VERIFIED("400", "이미 인증된 이메일입니다."),
  ACCOUNT_DELETION_BLOCKED("400", "잔액이 있는 계좌가 존재하여 계정을 삭제할 수 없습니다."),

  // 401 Unauthorized
  AUTHENTICATION_FAILED("401", "인증에 실패했습니다."),
  INVALID_CREDENTIALS("401", "이메일 또는 비밀번호가 올바르지 않습니다."),

  // 404 Not Found
  USER_NOT_FOUND("404", "사용자를 찾을 수 없습니다."),

  // 409 Conflict
  EMAIL_ALREADY_EXISTS("409", "이미 사용 중인 이메일입니다."),
  NICKNAME_ALREADY_EXISTS("409", "이미 사용 중인 닉네임입니다."),

  // 500 Internal Server Error
  EMAIL_SEND_FAILED("500", "이메일 발송에 실패했습니다."),
  PASSWORD_CHANGE_FAILED("500", "비밀번호 변경에 실패했습니다."),
  INTERNAL_SERVER_ERROR("500", "서버에 문제가 발생했습니다.");

  private final String code;
  private final String message;

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.valueOf(Integer.parseInt(code));
  }
  }