package com.muscat.backtest.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
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
    trail.taxTrade(StrategyTransaction.buy(new StrategyTransaction.Order(null, null, BUY, BUY, new BigDecimal("100"),
      new BigDecimal("1000"), null, null), new BigDecimal("10"), BigDecimal.ZERO, null, new BigDecimal("1000")));
    return trail;
  }

  // 2025-06-30 에 150달러로 10주를 판 기간 끝 매도
  private static StrategyTransaction endSale() {
    LocalDate end = LocalDate.of(2025, 6, 30);
    return StrategyTransaction.sell(new StrategyTransaction.Order(null, null, end, end, new BigDecimal("150"),
      new BigDecimal("1000"), null, TransactionTrigger.PERIOD_END_SALE), new BigDecimal("10"), BigDecimal.ZERO,
      new BigDecimal("1500"), new BigDecimal("500"));
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
    assertThat(trail.harvestTransactions(endSale())).isNull();
  }

  @Test
  @DisplayName("연말 정리 거래 뒤에 기간 끝 매도를 추가")
  void endSaleAppended() {
    HoldingTrail trail = boughtTenShares();
    StrategyTransaction endSale = endSale();

    trail.harvestBefore(LocalDate.of(2025, 1, 2), new BigDecimal("10"), BigDecimal.ZERO);

    assertThat(trail.harvestTransactions(endSale)).hasSize(3).last().isSameAs(endSale);
  }
}
