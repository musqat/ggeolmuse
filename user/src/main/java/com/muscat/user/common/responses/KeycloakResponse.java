package com.muscat.user.common.responses;

import com.muscat.user.common.enums.BaseResponseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum KeycloakResponse implements BaseResponseEnum {

  // 500 Internal Server Error
  USER_CREATE_FAILED("500", "계정 생성에 실패했습니다."),
  LOGIN_FAILED("500", "로그인 처리 중 오류가 발생했습니다."),
  TOKEN_PARSE_FAILED("500", "토큰 파싱에 실패했습니다."),
  API_ERROR("500", "인증 서비스에 문제가 발생했습니다."),
  PASSWORD_CHANGE_FAILED("500", "비밀번호 변경에 실패했습니다."),
  USER_DELETE_FAILED("500", "계정 삭제에 실패했습니다.");

  private final String code;
  private final String message;

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.valueOf(Integer.parseInt(code));
  }
}