package com.muscat.backtest.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.domain.engine.PortfolioTaxSalePlan.Candidate;
import com.muscat.backtest.domain.engine.PortfolioTaxSalePlan.Sale;
import com.muscat.backtest.common.enums.type.PortfolioTaxSale;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PortfolioTaxSalePlan 양도세 판매 배분")
class PortfolioTaxSalePlanTest {

  private static final BigDecimal FX = new BigDecimal("1000");

  // 평가(USD) · 줄 금액(KRW) · 판매 순서 · 원화 원가 · 당일 팔 수 있는지
  private static Candidate line(String valueUsd, String plannedKrw, Integer order, String costKrw, boolean sellable) {
    return new Candidate(new BigDecimal(valueUsd), new BigDecimal(plannedKrw), order,
      costKrw == null ? null : new BigDecimal(costKrw), sellable);
  }

  private static List<Sale> plan(PortfolioTaxSale method, String feeRate, List<Candidate> lines, String needUsd) {
    return new PortfolioTaxSalePlan(method, feeRate == null ? null : new BigDecimal(feeRate))
      .plan(lines, new BigDecimal(needUsd), FX);
  }

  private static void assertSales(List<Sale> sales, List<Integer> lines, String... usd) {
    assertThat(sales).extracting(Sale::line).containsExactlyElementsOf(lines);
    for (int i = 0; i < usd.length; i++) {
      assertThat(sales.get(i).usd()).isEqualByComparingTo(usd[i]);
    }
  }

  @Test
  @DisplayName("판매 방식이 없으면 모자라도 팔 줄 없음")
  void none() {
    List<Candidate> lines = List.of(line("100", "1000000", 1, null, true));

    assertThat(plan(PortfolioTaxSale.NONE, null, lines, "50")).isEmpty();
    assertThat(plan(null, null, lines, "50")).isEmpty();
  }

  @Test
  @DisplayName("평가 손익을 쓰는 방식은 손실 종목부터 · 이익 손실 섞기 둘뿐")
  void usesGain() {
    assertThat(new PortfolioTaxSalePlan(PortfolioTaxSale.LOSS_FIRST, null).usesGain()).isTrue();
    assertThat(new PortfolioTaxSalePlan(PortfolioTaxSale.GAIN_NEUTRAL, null).usesGain()).isTrue();
    assertThat(new PortfolioTaxSalePlan(PortfolioTaxSale.TARGET_WEIGHT, null).usesGain()).isFalse();
    assertThat(new PortfolioTaxSalePlan(PortfolioTaxSale.CUSTOM_ORDER, null).usesGain()).isFalse();
    assertThat(new PortfolioTaxSalePlan(null, null).usesGain()).isFalse();
  }

  @Test
  @DisplayName("정한 순서는 순서가 작은 줄부터 수수료 뺀 판 돈까지 채우고 모자라면 다음 줄, 팔 수 없는 줄은 건너뜀")
  void customOrder() {
    List<Candidate> lines = List.of(
      line("100", "1000000", 2, null, true),
      line("50", "1000000", 1, null, true),
      line("80", "1000000", 0, null, false));

    assertSales(plan(PortfolioTaxSale.CUSTOM_ORDER, "0.01", lines, "80"), List.of(1, 0), "49.5", "30.5");
  }

  @Test
  @DisplayName("손실 종목부터는 판 돈 × 환율 - 원화 원가가 작은 줄부터, 같으면 요청 줄 순서")
  void lossFirst() {
    List<Candidate> lines = List.of(
      line("100", "1000000", null, "120000", true),
      line("100", "1000000", null, "90000", true),
      line("100", "1000000", null, "120000", true));

    assertSales(plan(PortfolioTaxSale.LOSS_FIRST, null, lines, "150"), List.of(0, 2), "100", "50");
  }

  @Test
  @DisplayName("목표 비중은 넘친 줄부터 넘친 만큼 팔고 목표는 팔 수 없는 줄까지 넣은 줄 금액 비중")
  void targetWeightExcess() {
    List<Candidate> lines = List.of(
      line("600", "2000000", null, null, true),
      line("200", "1000000", null, null, true),
      line("200", "1000000", null, null, false));

    assertSales(plan(PortfolioTaxSale.TARGET_WEIGHT, null, lines, "60"), List.of(0), "60");
    assertSales(plan(PortfolioTaxSale.TARGET_WEIGHT, "0.01", lines, "121"), List.of(0, 1), "113.66666667",
      "7.33333334");
  }

  @Test
  @DisplayName("목표 비중에서 넘친 몫으로 모자라면 남은 몫을 줄 금액 비중대로 나눠 요청 줄 순서로")
  void targetWeightSpread() {
    List<Candidate> lines = List.of(
      line("600", "2000000", null, null, true),
      line("200", "1000000", null, null, true),
      line("200", "1000000", null, null, false));

    assertSales(plan(PortfolioTaxSale.TARGET_WEIGHT, null, lines, "160"), List.of(0, 1), "140", "20");
  }

  @Test
  @DisplayName("몫이 남은 판 돈 이상인 줄은 남은 판 돈만큼 팔고 나머지를 남은 줄에 다시 나눔")
  void spreadCapped() {
    List<Candidate> lines = List.of(
      line("600", "2000000", null, null, true),
      line("200", "1000000", null, null, true),
      line("200", "1000000", null, null, false));

    assertSales(plan(PortfolioTaxSale.TARGET_WEIGHT, null, lines, "760"), List.of(0, 1), "560", "200");
  }

  @Test
  @DisplayName("이익 손실 섞기는 이익 쪽 = 모자란 달러 × (-rL) ÷ (rG - rL), 손실 쪽은 나머지")
  void gainNeutral() {
    List<Candidate> lines = List.of(
      line("100", "1000000", null, "80000", true),
      line("100", "1000000", null, "140000", true));

    assertSales(plan(PortfolioTaxSale.GAIN_NEUTRAL, null, lines, "60"), List.of(0, 1), "40", "20");
  }

  @Test
  @DisplayName("이익 손실 섞기에서 이익 쪽 몫이 이익 쪽 판 돈보다 크면 이익 쪽을 다 팔고 나머지는 손실 쪽")
  void gainNeutralCapped() {
    List<Candidate> lines = List.of(
      line("100", "1000000", null, "80000", true),
      line("100", "1000000", null, "140000", true));

    assertSales(plan(PortfolioTaxSale.GAIN_NEUTRAL, null, lines, "160"), List.of(0, 1), "100", "60");
  }

  @Test
  @DisplayName("이익 손실 섞기에서 한쪽 묶음이 없으면 손실 종목부터와 같은 차례")
  void gainNeutralOneSide() {
    List<Candidate> lines = List.of(
      line("100", "1000000", null, "50000", true),
      line("100", "1000000", null, "90000", true));

    assertSales(plan(PortfolioTaxSale.GAIN_NEUTRAL, null, lines, "120"), List.of(1, 0), "100", "20");
  }
}
