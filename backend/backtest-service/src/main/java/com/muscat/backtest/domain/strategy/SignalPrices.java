package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// 신호 전략이 보는 쓸 수 있는 일봉(오래된 날부터)과 신호 값의 누적 합
record SignalPrices(List<OHLCPriceDto> candles, BigDecimal[] sums) {

  static SignalPrices of(Map<LocalDate, OHLCPriceDto> priceMap) {
    List<OHLCPriceDto> candles = usable(priceMap);
    BigDecimal[] sums = new BigDecimal[candles.size() + 1];
    sums[0] = BigDecimal.ZERO;
    for (int i = 0; i < candles.size(); i++) {
      sums[i + 1] = sums[i].add(value(candles.get(i)));
    }
    return new SignalPrices(candles, sums);
  }

  // 쓸 수 있는 일봉(오래된 날부터)
  static List<OHLCPriceDto> usable(Map<LocalDate, OHLCPriceDto> priceMap) {
    return priceMap.values().stream()
      .filter(PriceLookup::isUsable)
      .sorted(Comparator.comparing(OHLCPriceDto::date))
      .toList();
  }

  // 신호 값(수정 종가, 없거나 0 이하면 종가)
  private static BigDecimal value(OHLCPriceDto candle) {
    BigDecimal adjusted = candle.adjustedClose();
    return adjusted != null && adjusted.signum() > 0 ? adjusted : PriceLookup.effectiveClose(candle);
  }

  // i 번째 일봉의 신호 값
  BigDecimal value(int i) {
    return value(candles.get(i));
  }

  // i 번째 일봉까지 window 거래일이 찼는지
  boolean filled(int i, int window) {
    return i + 1 >= window;
  }

  // i 번째 일봉까지 window 거래일 신호 값 합(window 거래일이 찬 i)
  BigDecimal sum(int i, int window) {
    return sums[i + 1].subtract(sums[i + 1 - window]);
  }

  // i 번째 일봉까지 window 거래일 평균 × factor(소수 둘째, 일봉이 모자라면 null)
  BigDecimal average(int i, int window, BigDecimal factor) {
    return filled(i, window)
      ? sum(i, window).multiply(factor).divide(BigDecimal.valueOf(window), MoneyUtils.USD_SCALE, MoneyUtils.ROUND_MODE)
      : null;
  }
}
