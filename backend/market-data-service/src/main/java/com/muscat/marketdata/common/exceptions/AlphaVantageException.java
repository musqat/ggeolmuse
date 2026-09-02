package com.muscat.marketdata.common.exceptions;

public class AlphaVantageException extends RuntimeException {

  public AlphaVantageException(String message) {
    super(message);
  }

  public AlphaVantageException(String message, Throwable cause) {
    super(message, cause);
  }
}