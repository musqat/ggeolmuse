package com.muscat.trade.common.constants;

import com.muscat.commonlib.constants.CommonConstants;
import java.math.BigDecimal;

// 거래 관련 상수 정의 클래스
public final class TradeConstants {
    
    // === 계산 관련 상수 ===
    public static final BigDecimal PERCENTAGE_MULTIPLIER = new BigDecimal("100");
    
    // === 페이지네이션 관련 상수 ===
    @Deprecated
    public static final int MAX_PAGE_SIZE = Integer.MAX_VALUE;
    
    // === 백테스트 관련 상수 ===
    public static final int BACKTEST_TIMEOUT_SECONDS = 2;
    
    // === 정밀도 관련 상수 ===
    public static final int SELL_RATIO_PRECISION = 6;  // 매도 비율 계산 정밀도
    
    // === 검증 관련 상수 ===
    public static final String SYMBOL_PATTERN = "^[A-Z0-9.]+$";
    public static final String ACCOUNT_ID_PATTERN = "^[0-9]+$";
    public static final int MAX_SYMBOL_LENGTH = 16;
    public static final int MIN_SYMBOL_LENGTH = 1;
    
    private TradeConstants() {
    }
}