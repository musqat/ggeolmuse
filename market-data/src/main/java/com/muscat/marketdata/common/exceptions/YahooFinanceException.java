package com.muscat.marketdata.common.exceptions;

public class YahooFinanceException extends MarketDataException {
    
    public YahooFinanceException(String message) {
        super(message);
    }
    
    public YahooFinanceException(String message, Throwable cause) {
        super(message, cause);
    }
}