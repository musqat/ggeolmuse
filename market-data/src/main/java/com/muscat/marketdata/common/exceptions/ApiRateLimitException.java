package com.muscat.marketdata.common.exceptions;

public class ApiRateLimitException extends MarketDataException {
    
    private final String provider;
    private final int retryAfterSeconds;
    
    public ApiRateLimitException(String provider, int retryAfterSeconds) {
        super(String.format("API rate limit exceeded for %s. Retry after %d seconds", provider, retryAfterSeconds));
        this.provider = provider;
        this.retryAfterSeconds = retryAfterSeconds;
    }
    
    public String getProvider() {
        return provider;
    }
    
    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}