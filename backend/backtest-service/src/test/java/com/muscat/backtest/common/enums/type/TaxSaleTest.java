package com.muscat.backtest.common.enums.type;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("양도세 낼 현금이 모자랄 때 파는 주식 수")
class TaxSaleTest {

  @Test
  @DisplayName("수수료가 없으면 모자란 달러 ÷ 가격을 소수 여덟째에서 올린다")
  void sharesWithoutFee() {
    // 258.92 ÷ 410 = 0.6315121951…
    assertThat(TaxSale.shares(new BigDecimal("258.92"), new BigDecimal("410"), null, BigDecimal.TEN))
      .isEqualByComparingTo("0.63151220");
    assertThat(TaxSale.shares(new BigDecimal("258.92"), new BigDecimal("410"), BigDecimal.ZERO, BigDecimal.TEN))
      .isEqualByComparingTo("0.63151220");
  }

  @Test
  @DisplayName("수수료가 있으면 수수료를 센트로 반올림해 빼도 판 돈이 모자란 달러 이상이 되는 수량을 올림으로 판다")
  void sharesCoverFeeRounding() {
    BigDecimal feeRate = new BigDecimal("0.0025");

    // (10 + 0.01) ÷ (1 × 0.9975) = 10.0350877192…
    BigDecimal shares = TaxSale.shares(BigDecimal.TEN, BigDecimal.ONE, feeRate, new BigDecimal("100"));

    assertThat(shares).isEqualByComparingTo("10.03508772");
    BigDecimal gross = shares.multiply(BigDecimal.ONE);
    BigDecimal proceeds = gross.subtract(gross.multiply(feeRate).setScale(2, RoundingMode.HALF_UP));
    assertThat(proceeds).isGreaterThanOrEqualTo(BigDecimal.TEN);
  }

  @Test
  @DisplayName("팔 수량이 보유보다 많으면 보유 전부를 판다")
  void cappedByHolding() {
    assertThat(TaxSale.shares(new BigDecimal("434.92"), new BigDecimal("4"), null, new BigDecimal("61.25")))
      .isEqualByComparingTo("61.25");
  }

  @Test
  @DisplayName("SELL 일 때만 팔고 비우면 팔지 않는다")
  void sells() {
    assertThat(TaxSale.sells(TaxSale.SELL)).isTrue();
    assertThat(TaxSale.sells(TaxSale.NONE)).isFalse();
    assertThat(TaxSale.sells(null)).isFalse();
  }
}
