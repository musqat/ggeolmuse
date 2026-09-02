package com.muscat.backtest.common.constants;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 백테스트 서비스에서 사용하는 상수 정의
 */
public final class BacktestConstants {

  private BacktestConstants() {
    throw new UnsupportedOperationException("Constants class cannot be instantiated");
  }

  /**
   * 금액 계산 관련 상수
   */
  public static final class Money {
    private Money() {
      throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    /** 금액 소수점 자리수 (2자리) */
    public static final int SCALE = 2;

    /** 금액 반올림 모드 */
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /** 주식 수량 소수점 자리수 (8자리) */
    public static final int SHARES_SCALE = 8;

    /** 백분율 변환 계수 (100) */
    public static final BigDecimal PERCENTAGE_MULTIPLIER = BigDecimal.valueOf(100);
  }

  /**
   * 거래 트리거 유형
   */
  public static final class TransactionTrigger {
    private TransactionTrigger() {
      throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    /** DCA 월정액 투자 */
    public static final String MONTHLY_INVESTMENT = "월정액";

    /** 배당금 재투자 */
    public static final String DIVIDEND_REINVESTMENT = "배당 재투자";

    /** 조건부 매수 (하락 시) */
    public static final String CONDITIONAL_PURCHASE = "조건부 매수";
  }

  /**
   * 데이터 조회 관련 상수
   */
  public static final class DataLookup {
    private DataLookup() {
      throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    /** 과거 데이터 최대 검색 일수 (주말/공휴일 처리) */
    public static final int MAX_LOOKBACK_DAYS = 5;
  }
}
