package com.muscat.backtest.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("한 회차 매수 금액 · 주식 수")
class RoundPurchaseTest {

  private static final BigDecimal FX = new BigDecimal("1350");

  @Test
  @DisplayName("주식 수로 사면 주가 × 주식 수에 수수료를 더한 달러를 그 환율로 원화로 바꾼다")
  void byShares() {
    RoundPurchase purchase = RoundPurchase.byShares(3, new BigDecimal("90"), FX, new BigDecimal("0.01"));

    assertThat(purchase.shares()).isEqualByComparingTo("3");
    assertThat(purchase.feeUsd()).isEqualByComparingTo("2.70");
    assertThat(purchase.costUsd()).isEqualByComparingTo("272.70");
    assertThat(purchase.amountKrw()).isEqualByComparingTo("368145");
  }

  @Test
  @DisplayName("수수료율을 비우면 수수료 없이 주가 × 주식 수다")
  void bySharesWithoutFee() {
    RoundPurchase purchase = RoundPurchase.byShares(2, new BigDecimal("100"), FX, null);

    assertThat(purchase.feeUsd()).isEqualByComparingTo("0");
    assertThat(purchase.amountKrw()).isEqualByComparingTo("270000");
  }

  @Test
  @DisplayName("가진 달러로는 살 수 있는 주식 수만큼, 많아야 정한 주식 수까지 산다")
  void withinUsd() {
    assertThat(RoundPurchase.withinUsd(10, new BigDecimal("900"), new BigDecimal("105"), FX, BigDecimal.ZERO))
      .satisfies(purchase -> {
        assertThat(purchase.shares()).isEqualByComparingTo("8");
        assertThat(purchase.costUsd()).isEqualByComparingTo("840");
      });
    assertThat(RoundPurchase.withinUsd(2, new BigDecimal("900"), new BigDecimal("105"), FX, BigDecimal.ZERO).shares())
      .isEqualByComparingTo("2");
  }

  @Test
  @DisplayName("반올림한 수수료까지 더해 가진 달러를 넘으면 한 주 덜 산다")
  void withinUsdFeeRounding() {
    // 2주는 20달러 + 수수료 0.006 → 0.01 로 20.01달러라 가진 20.006달러를 넘음
    RoundPurchase purchase = RoundPurchase.withinUsd(5, new BigDecimal("20.006"), new BigDecimal("10"), FX,
      new BigDecimal("0.0003"));

    assertThat(purchase.shares()).isEqualByComparingTo("1");
    assertThat(purchase.costUsd()).isEqualByComparingTo("10");
  }

  @Test
  @DisplayName("가진 달러로 한 주도 못 사면 null")
  void withinUsdNone() {
    assertThat(RoundPurchase.withinUsd(1, new BigDecimal("50"), new BigDecimal("105"), FX, null)).isNull();
  }

  @Test
  @DisplayName("금액으로 사면 수수료를 뺀 달러로 소수 주를 사고 넣는 원화는 그 금액이다")
  void byAmount() {
    RoundPurchase purchase = RoundPurchase.byAmount(new BigDecimal("1300000"), new BigDecimal("100"),
      new BigDecimal("1300"), new BigDecimal("0.01"));

    assertThat(purchase.shares()).isEqualByComparingTo("9.9");
    assertThat(purchase.feeUsd()).isEqualByComparingTo("10.00");
    assertThat(purchase.costUsd()).isEqualByComparingTo("1000");
    assertThat(purchase.amountKrw()).isEqualByComparingTo("1300000");
  }
}
