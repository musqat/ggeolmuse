package com.muscat.backtest.common.exception;

import com.muscat.backtest.common.enums.BacktestResponse;
import lombok.Getter;

@Getter
public class BacktestException extends RuntimeException {

  private final BacktestResponse errorCode;

  public BacktestException(BacktestResponse errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public BacktestException(BacktestResponse errorCode, Throwable cause) {
    super(errorCode.getMessage(), cause);
    this.errorCode = errorCode;
  }

  public BacktestException(BacktestResponse errorCode, String details) {
    super(details);
    this.errorCode = errorCode;
  }
}