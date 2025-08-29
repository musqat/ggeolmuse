package com.muscat.trade.common.exceptions;

import com.muscat.trade.common.enums.BaseResponseEnum;
import lombok.Getter;

/**
 * 기본 비즈니스 예외 클래스
 */
@Getter
public class BusinessException extends RuntimeException {
  private final BaseResponseEnum errorCode;

  public BusinessException(BaseResponseEnum errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public BusinessException(BaseResponseEnum errorCode, String customMessage) {
    super(customMessage);
    this.errorCode = errorCode;
  }

  public BusinessException(BaseResponseEnum errorCode, Throwable cause) {
    super(errorCode.getMessage(), cause);
    this.errorCode = errorCode;
  }
}