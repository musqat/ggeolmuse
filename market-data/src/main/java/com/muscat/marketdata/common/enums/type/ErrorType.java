package com.muscat.marketdata.common.enums.type;

public enum ErrorType {
    
    // 기본 오류
    INTERNAL_ERROR,
    EXTERNAL_SERVICE_ERROR,
    NETWORK_ERROR,
    TIMEOUT_ERROR,
    
    // 데이터 관련 오류
    DATA_NOT_FOUND,
    DATA_PARSING_ERROR,
    
    // API 관련 오류
    API_CALL_ERROR,
    RATE_LIMIT_EXCEEDED,
    AUTHENTICATION_ERROR,
    AUTHORIZATION_ERROR,
    
    // 입력 검증 오류
    INVALID_PARAMETER,
    MISSING_PARAMETER,
    INVALID_DATE_RANGE,
    INVALID_SYMBOL
}