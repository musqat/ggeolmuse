package com.muscat.marketdata.common.exceptions;

/**
 * Yahoo Finance API 호출 중 발생하는 예외
 */
public class YahooFinanceException extends RuntimeException {

    public YahooFinanceException(String message) {
        super(message);
    }

    public YahooFinanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
