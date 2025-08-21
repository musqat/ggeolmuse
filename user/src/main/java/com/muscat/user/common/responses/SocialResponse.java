package com.muscat.user.common.responses;

import com.muscat.user.common.enums.BaseResponseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SocialResponse implements BaseResponseEnum {

  // 성공 응답
  GOOGLE_LOGIN_SUCCESS("200", "Google 로그인에 성공했습니다."),
  GOOGLE_USER_SYNCED("200", "Google 사용자 정보가 동기화되었습니다."),
  PROVIDERS_RETRIEVED("200", "소셜 로그인 제공자 목록을 조회했습니다."),

  // 400 Bad Request
  GOOGLE_LOGIN_FAILED("400", "Google 로그인에 실패했습니다."),
  GOOGLE_TOKEN_EXCHANGE_FAILED("400", "Google 토큰 교환에 실패했습니다."),
  GOOGLE_USER_INFO_FAILED("400", "Google 사용자 정보 조회에 실패했습니다."),

  // 409 Conflict
  SOCIAL_EMAIL_CONFLICT("409", "동일한 이메일의 로컬 계정이 존재합니다. 계정 연결 기능을 이용해주세요.");

  private final String code;
  private final String message;

  @Override
  public HttpStatus getHttpStatus() {
    return HttpStatus.valueOf(Integer.parseInt(code));
  }
}