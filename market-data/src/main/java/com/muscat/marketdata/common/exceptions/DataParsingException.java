package com.muscat.marketdata.common.exceptions;

public class DataParsingException extends MarketDataException {
    
    private final String provider;
    private final String dataType;
    
    public DataParsingException(String provider, String dataType, String message) {
        super(String.format("[%s] %s 데이터 파싱 실패: %s", provider, dataType, message));
        this.provider = provider;
        this.dataType = dataType;
    }
    
    public DataParsingException(String provider, String dataType, String message, Throwable cause) {
        super(String.format("[%s] %s 데이터 파싱 실패: %s", provider, dataType, message), cause);
        this.provider = provider;
        this.dataType = dataType;
    }
    
    public String getProvider() {
        return provider;
    }
    
    public String getDataType() {
        return dataType;
    }
}