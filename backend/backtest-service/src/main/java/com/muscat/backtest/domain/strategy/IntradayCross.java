package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;

// 장중 기준가 판정: 저가가 기준 × (1 - 비율) 이하(오르는 쪽은 고가가 기준 × (1 + 비율) 이상)인 날 그 값, 시가부터 넘었으면 시가
// 보는 쪽(저가 · 고가)이 없거나 0 이하인 날 · 종가가 조정종가뿐인 날은 시가와 그 값을 종가로 봄
final class IntradayCross {

  // 기준가로 사고팔 때 값 소수 자릿수
  static final int PRICE_SCALE = 8;

  private IntradayCross() {}

  // 기준가를 넘은 날 사고파는 값(아니면 null)
  static BigDecimal price(BigDecimal base, OHLCPriceDto candle, BigDecimal rate, boolean rise) {
    BigDecimal close = PriceLookup.effectiveClose(candle);
    BigDecimal trigger = base.multiply(rise ? BigDecimal.ONE.add(rate) : BigDecimal.ONE.subtract(rate));
    BigDecimal extreme = rise ? candle.highPrice() : candle.lowPrice();
    boolean hasRange = candle.closePrice() != null && positive(extreme);
    if (!beyond(hasRange ? extreme : close, trigger, rise)) {
      return null;
    }
    BigDecimal open = hasRange ? candle.openPrice() : close;
    return positive(open) && beyond(open, trigger, rise) ? open : trigger.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
  }

  // 시가부터 기준가를 넘어 그 값이 시가인지
  static boolean atOpen(BigDecimal price, OHLCPriceDto candle) {
    return price != null && positive(candle.openPrice()) && price.compareTo(candle.openPrice()) == 0;
  }

  // 기준 대비 바뀐 비율(%, 절댓값 소수 첫째) 뒤에 정한 글을 둔 거래 이유("16.7%하락" 꼴)
  static String label(BigDecimal base, BigDecimal price, String suffix) {
    return String.format("%.1f%%%s", MoneyUtils.calculateReturnRate(base, price).abs(), suffix);
  }

  // 오르는 쪽이면 기준가 이상, 내리는 쪽이면 기준가 이하
  private static boolean beyond(BigDecimal value, BigDecimal trigger, boolean rise) {
    int compared = value.compareTo(trigger);
    return rise ? compared >= 0 : compared <= 0;
  }

  private static boolean positive(BigDecimal value) {
    return value != null && value.signum() > 0;
  }
}
