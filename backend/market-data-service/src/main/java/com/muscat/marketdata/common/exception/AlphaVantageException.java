package com.muscat.marketdata.common.exception;

public class AlphaVantageException extends RuntimeException {

  public AlphaVantageException(String message) {
    super(message);
  }

  public AlphaVantageException(String message, Throwable cause) {
    super(message, cause);
  }
}