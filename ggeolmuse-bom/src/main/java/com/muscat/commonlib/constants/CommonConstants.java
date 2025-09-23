package com.muscat.commonlib.constants;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 공통 상수 정의
 */
public final class CommonConstants {
    
    // === 날짜/시간 관련 ===
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String ISO_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String TIMEZONE_UTC = "UTC";
    public static final String TIMEZONE_KST = "Asia/Seoul";
    
    // === 페이지네이션 관련 ===
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE = 0;
    
    // === 계산 관련 ===
    public static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP;
    public static final int DEFAULT_SCALE = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO;
    public static final BigDecimal ONE = BigDecimal.ONE;
    public static final BigDecimal HUNDRED = new BigDecimal("100");
    
    // === 통화 관련 ===
    public static final String CURRENCY_KRW = "KRW";
    public static final String CURRENCY_USD = "USD";
    public static final int KRW_SCALE = 0;  // KRW는 소수점 없음
    public static final int USD_SCALE = 2;  // USD는 소수점 2자리
    
    // === 검증 관련 ===
    public static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    public static final String PHONE_PATTERN = "^[0-9]{10,11}$";
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 100;
    
    // === HTTP 관련 ===
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
    public static final String CHARSET_UTF8 = "UTF-8";
    
    // === 헤더 관련 ===
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_API_VERSION = "X-Api-Version";
    
    private CommonConstants() {
        // 유틸리티 클래스는 인스턴스화할 수 없습니다
        throw new UnsupportedOperationException("유틸리티 클래스는 인스턴스화할 수 없습니다");
    }
}