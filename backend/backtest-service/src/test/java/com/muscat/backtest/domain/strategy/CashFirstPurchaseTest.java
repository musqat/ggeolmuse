package com.muscat.backtest.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.backtest.domain.strategy.CashFirstPurchase.Fill;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CashFirstPurchase 판 돈 먼저 쓰는 매수")
class CashFirstPurchaseTest {

  private static final BigDecimal PRICE = new BigDecimal("10");
  private static final BigDecimal FX = new BigDecimal("1300");
  // 회당 13만원(환율 1300 에 100달러), 수수료 없음
  private static final CashFirstPurchase BY_AMOUNT = new CashFirstPurchase(new BigDecimal("130000"), null, null);
  // 회당 3주(10달러 · 환율 1300 에 3만 9천원), 수수료 없음
  private static final CashFirstPurchase BY_SHARES = new CashFirstPurchase(null, 3, null);

  private static void assertFill(Fill fill, String shares, String deposit, String cashUsed) {
    assertThat(fill.shares()).isEqualByComparingTo(shares);
    assertThat(fill.deposit()).isEqualByComparingTo(deposit);
    if (cashUsed == null) {
      assertThat(fill.cashUsed()).isNull();
    } else {
      assertThat(fill.cashUsed()).isEqualByComparingTo(cashUsed);
    }
  }

  @Test
  @DisplayName("회당 금액에 판 돈이 없으면 회당 금액 전부를 새 돈으로, 횟수를 다 썼거나 남은 예산을 넘으면 사지 않음")
  void amountWithoutCash() {
    assertFill(BY_AMOUNT.fill(PRICE, FX, BigDecimal.ZERO, false, null), "10", "130000", null);
    assertThat(BY_AMOUNT.fill(PRICE, FX, BigDecimal.ZERO, true, null)).isNull();
    assertThat(BY_AMOUNT.fill(PRICE, FX, BigDecimal.ZERO, false, new BigDecimal("100000"))).isNull();
  }

  @Test
  @DisplayName("회당 금액에 판 돈이 모자라면 모자란 원화만 새로 넣고, 넉넉하면 판 돈만 씀")
  void amountWithCash() {
    assertFill(BY_AMOUNT.fill(PRICE, FX, new BigDecimal("40"), false, null), "10", "78000", "40");
    assertFill(BY_AMOUNT.fill(PRICE, FX, new BigDecimal("150"), false, null), "10", "0", "100");
  }

  @Test
  @DisplayName("회당 금액에서 모자란 원화가 남은 예산을 넘으면 판 돈 전부로만 삼")
  void amountTopUpBlocked() {
    assertFill(BY_AMOUNT.fill(PRICE, FX, new BigDecimal("40"), false, new BigDecimal("50000")), "4", "0", "40");
    assertFill(BY_AMOUNT.fill(PRICE, FX, new BigDecimal("40"), true, null), "4", "0", "40");
  }

  @Test
  @DisplayName("주식 수에 판 돈이 없으면 주가 × 주식 수 + 수수료를 원화로 새로 넣고, 예산을 넘거나 횟수를 다 썼으면 사지 않음")
  void sharesWithoutCash() {
    assertFill(BY_SHARES.fill(PRICE, FX, BigDecimal.ZERO, false, null), "3", "39000", null);
    assertThat(BY_SHARES.fill(PRICE, FX, BigDecimal.ZERO, false, new BigDecimal("30000"))).isNull();
    assertThat(BY_SHARES.fill(PRICE, FX, BigDecimal.ZERO, true, null)).isNull();
  }

  @Test
  @DisplayName("주식 수를 판 돈으로 다 사면 새 돈 없음, 모자라면 차액만 원화로, 차액을 못 넣으면 판 돈 안에서 살 수 있는 주식 수만")
  void sharesWithCash() {
    assertFill(BY_SHARES.fill(PRICE, FX, new BigDecimal("50"), false, null), "3", "0", "30");
    assertFill(BY_SHARES.fill(PRICE, FX, new BigDecimal("20"), false, null), "3", "13000", "20");
    assertFill(BY_SHARES.fill(PRICE, FX, new BigDecimal("20"), true, null), "2", "0", "20");
    assertThat(BY_SHARES.fill(PRICE, FX, new BigDecimal("5"), true, null)).isNull();
  }

  @Test
  @DisplayName("판 돈이 있을 때 환율이 0 이면 계산 오류")
  void zeroFxRate() {
    assertThatThrownBy(() -> BY_AMOUNT.fill(PRICE, BigDecimal.ZERO, new BigDecimal("40"), false, null))
      .isInstanceOf(ArithmeticException.class);
  }
}
