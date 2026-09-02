package com.muscat.trade.common.constants;

import java.math.BigDecimal;

public final class TradeConstants {

  // 계산 상수
  public static final BigDecimal PERCENTAGE_MULTIPLIER = new BigDecimal("100");
  public static final int SELL_RATIO_PRECISION = 6;

  // 검증 패턴
  public static final String SYMBOL_PATTERN = "^[A-Z0-9.]+$";
  public static final String ACCOUNT_ID_PATTERN = "^[0-9]+$";
  public static final int MAX_SYMBOL_LENGTH = 16;
  public static final int MIN_SYMBOL_LENGTH = 1;

  private TradeConstants() {
  }
}