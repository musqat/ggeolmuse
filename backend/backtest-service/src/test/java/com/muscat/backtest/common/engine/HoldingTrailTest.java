package com.muscat.backtest.common.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HoldingTrail 보유 단계 · 연말 정리 기록")
class HoldingTrailTest {

  private static final LocalDate BUY = LocalDate.of(2024, 1, 2);
  private static final LocalDate HARVEST = LocalDate.of(2024, 12, 30);
  private static final Function<LocalDate, BigDecimal> FX = date -> new BigDecimal("1000");

  private static OHLCPriceDto candle(LocalDate date, String close) {
    BigDecimal c = new BigDecimal(close);
    return new OHLCPriceDto("AAPL", date, c, c, c, c, c, 1L, "USD", true);
  }

  // 100달러에 10주(원가 100만원)를 산 뒤 연말 정리 날 종가 150달러
  private static HoldingTrail boughtTenShares() {
    HoldingTrail trail = new HoldingTrail(new TreeSet<>(List.of(HARVEST)), Map.of(HARVEST, candle(HARVEST, "150")),
      new BigDecimal("0.001"), FX);
    trail.taxTrade(StrategyTransaction.builder().date(BUY).actualDate(BUY).price(new BigDecimal("100"))
      .shares(new BigDecimal("10")).fee(BigDecimal.ZERO).cashUsed(new BigDecimal("1000"))
      .fxRate(new BigDecimal("1000")).build());
    return trail;
  }

  @Test
  @DisplayName("받은 날짜 앞의 연말 정리 날만 쓰고 쓴 날은 다시 쓰지 않음")
  void harvestsOnlyBefore() {
    HoldingTrail trail = boughtTenShares();

    assertThat(trail.harvestBefore(HARVEST, new BigDecimal("10"), BigDecimal.ZERO)).isEqualByComparingTo("10");
    assertThat(trail.harvestTransactions(null)).isEmpty();

    BigDecimal held = trail.harvestBefore(LocalDate.of(2025, 1, 2), new BigDecimal("10"), BigDecimal.ZERO);
    assertThat(held).isEqualByComparingTo("9.98");
    assertThat(trail.harvestBefore(LocalDate.of(2025, 6, 2), held, BigDecimal.ZERO)).isEqualByComparingTo("9.98");
    assertThat(trail.harvestTransactions(null)).hasSize(2);
  }

  @Test
  @DisplayName("연말 정리 매도 · 재매수가 양도세 계산 거래와 보유 단계에 남고 수수료 합은 두 거래 수수료")
  void harvestRecorded() {
    HoldingTrail trail = boughtTenShares();

    trail.harvestBefore(LocalDate.of(2025, 1, 2), new BigDecimal("10"), new BigDecimal("5"));

    assertThat(trail.taxTransactions()).hasSize(3);
    assertThat(trail.steps().getLast().from()).isEqualTo(HARVEST);
    assertThat(trail.steps().getLast().shares()).isEqualByComparingTo("9.98");
    assertThat(trail.steps().getLast().cashUsd()).isEqualByComparingTo("5");
    assertThat(trail.harvestFees()).isEqualByComparingTo("3.00");
  }

  @Test
  @DisplayName("연말 정리 날이 null 이면 주식 수 그대로이고 연말 정리 거래는 null")
  void offWithoutDates() {
    HoldingTrail trail = new HoldingTrail(null, Map.of(), null, FX);

    assertThat(trail.harvestBefore(HARVEST, new BigDecimal("10"), BigDecimal.ZERO)).isEqualByComparingTo("10");
    assertThat(trail.harvestTransactions(StrategyTransaction.builder().build())).isNull();
  }

  @Test
  @DisplayName("연말 정리 거래 뒤에 기간 끝 매도를 붙임")
  void endSaleAppended() {
    HoldingTrail trail = boughtTenShares();
    StrategyTransaction endSale = StrategyTransaction.builder().date(LocalDate.of(2025, 6, 30)).build();

    trail.harvestBefore(LocalDate.of(2025, 1, 2), new BigDecimal("10"), BigDecimal.ZERO);

    assertThat(trail.harvestTransactions(endSale)).hasSize(3).last().isSameAs(endSale);
  }
}
