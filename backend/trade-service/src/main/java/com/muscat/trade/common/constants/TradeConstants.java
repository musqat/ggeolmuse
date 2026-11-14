package com.muscat.trade.common.constants;

import java.math.BigDecimal;

public final class TradeConstants {

  // 계산 상수
  public static final BigDecimal PERCENTAGE_MULTIPLIER = new BigDecimal("100");
  public static final int SELL_RATIO_PRECISION = 6;
  // 미국 주식 배당 원천징수(15%, 뺀 뒤 금액을 입금)
  public static final BigDecimal DIVIDEND_TAX_RATE = new BigDecimal("0.15");

  // 검증 패턴
  public static final String ACCOUNT_ID_PATTERN = "^[0-9]+$";
  public static final int MAX_SYMBOL_LENGTH = 16;
  public static final int MIN_SYMBOL_LENGTH = 1;
  // 지수 심볼 앞 글자(^GSPC · ^NDX)
  public static final String INDEX_SYMBOL_PREFIX = "^";

  private TradeConstants() {
  }
}