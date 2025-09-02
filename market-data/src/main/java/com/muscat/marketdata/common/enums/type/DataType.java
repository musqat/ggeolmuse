package com.muscat.marketdata.common.enums.type;

public enum DataType {
    
    // 심볼 관련
    SYMBOLS,
    SYMBOL_INFO,
    
    // 가격 데이터
    CANDLE,
    OHLC,
    QUOTE,
    PRICE,
    
    // 배당 관련
    DIVIDEND,
    DIVIDEND_HISTORY,
    
    // 환율 데이터
    FX_RATE,
    USD_KRW,
    
    // 뉴스 데이터
    NEWS,
    MARKET_NEWS,
    
    // 기타
    METADATA,
    BATCH_RESULT
}