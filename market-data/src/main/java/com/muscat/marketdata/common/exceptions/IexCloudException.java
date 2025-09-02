package com.muscat.marketdata.common.exceptions;

public class IexCloudException extends MarketDataException {
    
    public IexCloudException(String message) {
        super(message);
    }
    
    public IexCloudException(String message, Throwable cause) {
        super(message, cause);
    }
}