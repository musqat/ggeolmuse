package com.muscat.marketdata.common.exceptions;

public class DataNotFoundException extends MarketDataException {
    
    private final String symbol;
    private final String dataType;
    
    public DataNotFoundException(String symbol, String dataType) {
        super(String.format("No %s data found for symbol: %s", dataType, symbol));
        this.symbol = symbol;
        this.dataType = dataType;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public String getDataType() {
        return dataType;
    }
}