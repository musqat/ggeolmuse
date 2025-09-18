package com.muscat.marketdata.domain.util;

import com.muscat.marketdata.domain.entity.Candle;
import java.math.BigDecimal;

public final class CandleUtils {

  private CandleUtils() {
    throw new UnsupportedOperationException("이 클래스는 인스턴스를 생성할 수 없습니다");
  }

  public static BigDecimal getEffectivePrice(Candle candle) {
    return candle.getAdjustedClose() != null ? candle.getAdjustedClose() : candle.getClose();
  }

  public static boolean hasCorporateAction(Candle candle) {
    return candle.getDividendAmount().compareTo(BigDecimal.ZERO) > 0
        || candle.getSplitCoefficient().compareTo(BigDecimal.ONE) != 0;
  }

  public static boolean isValidOhlc(Candle candle) {
    BigDecimal open = candle.getOpen();
    BigDecimal high = candle.getHigh();
    BigDecimal low = candle.getLow();
    BigDecimal close = candle.getClose();

    if (open == null || high == null || low == null || close == null) {
      return false;
    }
    return low.compareTo(open) <= 0
        && low.compareTo(close) <= 0
        && high.compareTo(open) >= 0
        && high.compareTo(close) >= 0;
  }
}