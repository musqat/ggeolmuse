package com.muscat.commonlib.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 서비스 예외의 부모. errorCode 는 코드의 enum 이름이다
 */
@Getter
public class BusinessException extends BaseException {

  private final ErrorCode code;

  public BusinessException(ErrorCode code) {
    this(code, code.getMessage());
  }

  public BusinessException(ErrorCode code, String message) {
    super(code.name(), message);
    this.code = code;
  }

  public BusinessException(ErrorCode code, Throwable cause) {
    super(code.name(), code.getMessage(), cause);
    this.code = code;
  }

  public HttpStatus getHttpStatus() {
    return code.getHttpStatus();
  }
}
