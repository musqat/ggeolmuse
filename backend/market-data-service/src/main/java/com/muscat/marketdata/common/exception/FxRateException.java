package com.muscat.marketdata.common.exception;

public class FxRateException extends RuntimeException {
    
    public FxRateException(String message) {
        super(message);
    }
    
    public FxRateException(String message, Throwable cause) {
        super(message, cause);
    }
}