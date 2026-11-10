package com.muscat.backtest.common.constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.time.MonthDay;

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

    /** 배당금 재투자 */
    public static final String DIVIDEND_REINVESTMENT = "배당 재투자";

    /** 조건부 매수 (하락 · 상승 시) */
    public static final String CONDITIONAL_PURCHASE = "조건부 매수";

    /** 기간 마지막 거래일 전량 매도 */
    public static final String PERIOD_END_SALE = "기간 끝 매도";

    /** 짧은 이동평균이 긴 이동평균을 아래에서 위로 넘은 날 매수 */
    public static final String GOLDEN_CROSS = "골든 크로스";

    /** 짧은 이동평균이 긴 이동평균을 위에서 아래로 넘은 날 매도 */
    public static final String DEAD_CROSS = "데드 크로스";

    /** 판 해의 양도세를 다음 해 5월 판 돈에서 냄 */
    public static final String CAPITAL_GAINS_TAX_PAYMENT = "양도세 납부";
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

  /**
   * 요청 값 범위
   */
  public static final class RequestLimit {
    private RequestLimit() {
      throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    /** 단순 · 회당 주식 수 상한 */
    public static final int MAX_SHARES = 1_000_000;
  }

  /**
   * 이동평균 교차 기간 (거래일)
   */
  public static final class MovingAverage {
    private MovingAverage() {
      throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    /** 짧은 기간 기본값 */
    public static final int DEFAULT_SHORT_WINDOW = 20;

    /** 긴 기간 기본값 */
    public static final int DEFAULT_LONG_WINDOW = 60;

    /** 가장 짧은 기간 */
    public static final int MIN_WINDOW = 2;

    /** 가장 긴 기간 */
    public static final int MAX_WINDOW = 250;

    /** 기간 검사 오류 문구 */
    public static final String WINDOW_MESSAGE = "이동평균 기간은 2 ~ 250 사이이고 짧은 기간이 긴 기간보다 작아야 합니다";
  }

  /**
   * 해외 주식 양도소득세
   */
  public static final class CapitalGains {
    private CapitalGains() {
      throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    /** 해마다 실현 이익에서 빼는 기본 공제 (KRW) */
    public static final BigDecimal ANNUAL_DEDUCTION_KRW = new BigDecimal("2500000");

    /** 세율 (지방소득세 포함 22%) */
    public static final BigDecimal TAX_RATE = new BigDecimal("0.22");

    /** 판 다음 해 이 날 이후 첫 거래일이 납부일 */
    public static final MonthDay PAYMENT_FROM = MonthDay.of(Month.MAY, 1);
  }
}
