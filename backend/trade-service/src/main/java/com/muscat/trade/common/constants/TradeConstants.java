package com.muscat.trade.common.constants;

import java.math.BigDecimal;

public final class TradeConstants {

  // 계산 상수
  public static final BigDecimal PERCENTAGE_MULTIPLIER = new BigDecimal("100");
  public static final int SELL_RATIO_PRECISION = 6;
  // 해외주식 배당 원천징수(15.4%). 배당은 뗀 뒤 금액을 입금한다
  public static final BigDecimal DIVIDEND_TAX_RATE = new BigDecimal("0.154");

  // 검증 패턴
  public static final String ACCOUNT_ID_PATTERN = "^[0-9]+$";
  public static final int MAX_SYMBOL_LENGTH = 16;
  public static final int MIN_SYMBOL_LENGTH = 1;

  private TradeConstants() {
  }
}