package com.muscat.backtest.common.exception;

import com.muscat.backtest.common.enums.BacktestResponseCode;
import lombok.Getter;

@Getter
public class BacktestException extends RuntimeException {

  private final BacktestResponseCode errorCode;

  public BacktestException(BacktestResponseCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public BacktestException(BacktestResponseCode errorCode, Throwable cause) {
    super(errorCode.getMessage(), cause);
    this.errorCode = errorCode;
  }

  public BacktestException(BacktestResponseCode errorCode, String details) {
    super(details);
    this.errorCode = errorCode;
  }
}