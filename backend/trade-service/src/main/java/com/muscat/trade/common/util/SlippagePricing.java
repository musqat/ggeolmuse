package com.muscat.trade.common.util;

import com.muscat.trade.common.enums.type.TradeType;
import java.math.BigDecimal;
import java.math.RoundingMode;

// 모의 매매 체결가에 슬리피지를 얹는다. 매수는 비싸게, 매도는 싸게
public final class SlippagePricing {

  private SlippagePricing() {
    throw new AssertionError("Utility class cannot be instantiated");
  }

  public static BigDecimal apply(BigDecimal marketPrice, BigDecimal slippageRate,
    TradeType tradeType, int scale) {
    if (marketPrice == null || slippageRate == null
      || slippageRate.compareTo(BigDecimal.ZERO) <= 0) {
      return marketPrice;
    }

    BigDecimal factor = tradeType == TradeType.BUY
      ? BigDecimal.ONE.add(slippageRate)
      : BigDecimal.ONE.subtract(slippageRate);

    return marketPrice.multiply(factor).setScale(scale, RoundingMode.HALF_UP);
  }
}
