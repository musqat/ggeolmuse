package com.muscat.trade.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.trade.common.enums.type.TradeType;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("슬리피지 체결가 계산")
class SlippagePricingTest {

  private static final BigDecimal MARKET_PRICE = new BigDecimal("150.00");
  private static final BigDecimal RATE = new BigDecimal("0.001");

  @Test
  @DisplayName("매수는 시장가보다 비싸게 체결한다")
  void apply_Buy_RaisesPrice() {
    BigDecimal price = SlippagePricing.apply(MARKET_PRICE, RATE, TradeType.BUY, 2);

    assertThat(price).isEqualByComparingTo("150.15");
  }

  @Test
  @DisplayName("매도는 시장가보다 싸게 체결한다")
  void apply_Sell_LowersPrice() {
    BigDecimal price = SlippagePricing.apply(MARKET_PRICE, RATE, TradeType.SELL, 2);

    assertThat(price).isEqualByComparingTo("149.85");
  }

  @Test
  @DisplayName("율이 null 이면 시장가 그대로")
  void apply_NullRate_ReturnsMarketPrice() {
    BigDecimal price = SlippagePricing.apply(MARKET_PRICE, null, TradeType.BUY, 2);

    assertThat(price).isSameAs(MARKET_PRICE);
  }

  @Test
  @DisplayName("율이 0 이면 시장가 그대로")
  void apply_ZeroRate_ReturnsMarketPrice() {
    BigDecimal price = SlippagePricing.apply(MARKET_PRICE, BigDecimal.ZERO, TradeType.SELL, 2);

    assertThat(price).isSameAs(MARKET_PRICE);
  }

  @Test
  @DisplayName("율이 음수면 시장가 그대로")
  void apply_NegativeRate_ReturnsMarketPrice() {
    BigDecimal price = SlippagePricing.apply(MARKET_PRICE, new BigDecimal("-0.001"),
      TradeType.BUY, 2);

    assertThat(price).isSameAs(MARKET_PRICE);
  }

  @Test
  @DisplayName("결과를 받은 자릿수로 반올림한다")
  void apply_RoundsToScale() {
    // 9.99 × 1.001 = 9.99999
    BigDecimal price = SlippagePricing.apply(new BigDecimal("9.99"), RATE, TradeType.BUY, 2);

    assertThat(price).isEqualByComparingTo("10.00");
    assertThat(price.scale()).isEqualTo(2);
  }
}
