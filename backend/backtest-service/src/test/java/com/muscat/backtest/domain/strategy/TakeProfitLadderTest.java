package com.muscat.backtest.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.domain.dto.request.PartialTakeProfit;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TakeProfitLadder 단계 · 팔 몫")
class TakeProfitLadderTest {

  private static BigDecimal number(String value) {
    return new BigDecimal(value);
  }

  private static PartialTakeProfit step(String gainRate, String sellRate) {
    return new PartialTakeProfit(number(gainRate), number(sellRate));
  }

  @Test
  @DisplayName("단계를 비우면 크게 올라도 팔지 않는다")
  void disabledWithoutSteps() {
    TakeProfitLadder none = TakeProfitLadder.of(null);
    TakeProfitLadder empty = TakeProfitLadder.of(List.of());
    none.open();
    empty.open();

    assertThat(none.check(number("1000"), number("10"), number("1000"))).isNull();
    assertThat(empty.check(number("1000"), number("10"), number("1000"))).isNull();
  }

  @Test
  @DisplayName("종가가 평균 단가 × (1 + 수익률) 이상이면 보유의 팔 비율과 그 몫의 원가를 팔고 거래 이유에 단계 수익률을 적는다")
  void sellsShareAtStep() {
    TakeProfitLadder ladder = TakeProfitLadder.of(List.of(step("0.2", "0.25")));
    ladder.open();

    // 원가 1000달러 · 10주라 평균 단가 100, 단계 120
    assertThat(ladder.check(number("119.99"), number("10"), number("1000"))).isNull();
    assertThat(ladder.check(number("120"), number("10"), number("1000")))
      .isEqualTo(new SaleShare(number("2.50000000"), number("250.00000000"), false, "부분 익절 +20%"));
  }

  @Test
  @DisplayName("하루에 아직 안 쓴 가장 낮은 단계 하나만 보고, 판 단계는 그 사이클에서 다시 보지 않는다")
  void oneStepPerDay() {
    TakeProfitLadder ladder = TakeProfitLadder.of(List.of(step("0.1", "0.5"), step("0.3", "0.5")));
    ladder.open();

    // 130 은 두 단계를 다 넘지만 첫 단계부터
    assertThat(ladder.check(number("130"), number("10"), number("1000")))
      .isEqualTo(new SaleShare(number("5.00000000"), number("500.00000000"), false, "부분 익절 +10%"));
    ladder.sold();
    assertThat(ladder.check(number("129.99"), number("5"), number("500"))).isNull();
    assertThat(ladder.check(number("130"), number("5"), number("500")))
      .isEqualTo(new SaleShare(number("2.50000000"), number("250.00000000"), false, "부분 익절 +30%"));
    ladder.sold();
    assertThat(ladder.check(number("1000"), number("2.5"), number("250"))).isNull();
  }

  @Test
  @DisplayName("팔지 않은 단계는 다음 날 다시 보고, 새 사이클을 열면 첫 단계부터 다시 본다")
  void reopensFromFirstStep() {
    TakeProfitLadder ladder = TakeProfitLadder.of(List.of(step("0.1", "1")));
    ladder.open();

    assertThat(ladder.check(number("110"), number("10"), number("1000"))).isNotNull();
    assertThat(ladder.check(number("110"), number("10"), number("1000")))
      .isEqualTo(new SaleShare(number("10"), number("1000"), true, "부분 익절 +10%"));
    ladder.sold();
    assertThat(ladder.check(number("200"), number("10"), number("1000"))).isNull();

    ladder.open();
    // 원가 990달러 · 10주라 평균 단가 99, 단계 108.9
    assertThat(ladder.check(number("108.9"), number("10"), number("990")))
      .isEqualTo(new SaleShare(number("10"), number("990"), true, "부분 익절 +10%"));
  }

  @Test
  @DisplayName("거래 이유의 단계 수익률은 끝자리 0 없이 적는다")
  void triggerText() {
    TakeProfitLadder ladder = TakeProfitLadder.of(List.of(step("0.125", "0.5")));
    ladder.open();

    assertThat(ladder.check(number("112.5"), number("10"), number("1000")).trigger()).isEqualTo("부분 익절 +12.5%");
  }
}
