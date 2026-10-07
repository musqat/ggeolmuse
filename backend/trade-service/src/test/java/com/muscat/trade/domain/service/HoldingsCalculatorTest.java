package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("체결 하나를 보유에 반영")
class HoldingsCalculatorTest {

  private static Trade trade(TradeType type, String quantity, String price, String totalAmount) {
    return Trade.builder()
      .userId("user-1").accountId(20L).symbol("AAPL").tradeType(type)
      .quantity(new BigDecimal(quantity)).price(new BigDecimal(price))
      .totalAmount(new BigDecimal(totalAmount))
      .build();
  }

  private static Holdings holdings(String quantity, String avgPrice, String invested) {
    return Holdings.builder()
      .userId("user-1").accountId(20L).symbol("AAPL")
      .totalQuantity(new BigDecimal(quantity)).avgPurchasePrice(new BigDecimal(avgPrice))
      .totalInvestedAmount(new BigDecimal(invested))
      .build();
  }

  @Test
  @DisplayName("첫 매수는 체결가와 총액으로 새 보유를 만든다")
  void apply_FirstBuy_CreatesHoldings() {
    HoldingsCalculator.Applied applied =
      HoldingsCalculator.apply(null, trade(TradeType.BUY, "10", "150.00", "1501.50"), 2);

    assertThat(applied.uncovered()).isFalse();
    assertThat(applied.holdings().getTotalQuantity()).isEqualByComparingTo("10");
    assertThat(applied.holdings().getAvgPurchasePrice()).isEqualByComparingTo("150.00");
    assertThat(applied.holdings().getTotalInvestedAmount()).isEqualByComparingTo("1501.50");
  }

  @Test
  @DisplayName("추가 매수는 평균 매수가를 가중 평균으로 바꾼다")
  void apply_AdditionalBuy_WeightedAverage() {
    Holdings current = holdings("5", "140.00", "700.00");

    HoldingsCalculator.Applied applied =
      HoldingsCalculator.apply(current, trade(TradeType.BUY, "10", "150.00", "1501.50"), 2);

    assertThat(applied.holdings()).isSameAs(current);
    assertThat(current.getTotalQuantity()).isEqualByComparingTo("15");
    assertThat(current.getAvgPurchasePrice()).isEqualByComparingTo("146.67");
    assertThat(current.getTotalInvestedAmount()).isEqualByComparingTo("2200.00");
  }

  @Test
  @DisplayName("부분 매도는 수량과 투자금액을 비율만큼 줄인다")
  void apply_PartialSell_ReducesProportionally() {
    Holdings current = holdings("10", "150.00", "1500.00");

    HoldingsCalculator.Applied applied =
      HoldingsCalculator.apply(current, trade(TradeType.SELL, "4", "160.00", "640.00"), 2);

    assertThat(applied.uncovered()).isFalse();
    assertThat(current.getTotalQuantity()).isEqualByComparingTo("6");
    assertThat(current.getTotalInvestedAmount()).isEqualByComparingTo("900.00");
    assertThat(current.getAvgPurchasePrice()).isEqualByComparingTo("150.00");
  }

  @Test
  @DisplayName("전량 매도면 보유가 없어진다")
  void apply_FullSell_RemovesHoldings() {
    HoldingsCalculator.Applied applied = HoldingsCalculator.apply(
      holdings("10", "150.00", "1500.00"), trade(TradeType.SELL, "10", "160.00", "1600.00"), 2);

    assertThat(applied.holdings()).isNull();
    assertThat(applied.uncovered()).isFalse();
  }

  @Test
  @DisplayName("보유보다 많이 팔거나 보유가 없으면 uncovered")
  void apply_Oversell_Uncovered() {
    assertThat(HoldingsCalculator.apply(
      holdings("3", "150.00", "450.00"), trade(TradeType.SELL, "4", "160.00", "640.00"), 2).uncovered())
      .isTrue();
    assertThat(HoldingsCalculator.apply(
      null, trade(TradeType.SELL, "1", "160.00", "160.00"), 2).uncovered())
      .isTrue();
  }
}
