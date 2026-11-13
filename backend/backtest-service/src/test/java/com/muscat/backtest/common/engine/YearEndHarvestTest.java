package com.muscat.backtest.common.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("YearEndHarvest 연말 정리")
class YearEndHarvestTest {

  private static final LocalDate BOUGHT = LocalDate.of(2024, 1, 15);
  private static final LocalDate SOLD = LocalDate.of(2024, 6, 14);
  private static final LocalDate DECEMBER = LocalDate.of(2024, 12, 31);

  // 주식 수 · 원화(환율 1000)로 산 거래
  private static StrategyTransaction buy(String symbol, String shares, String amountKrw) {
    return StrategyTransaction.builder().symbol(symbol).date(BOUGHT).actualDate(BOUGHT)
      .shares(new BigDecimal(shares)).price(BigDecimal.ONE).amount(new BigDecimal(amountKrw))
      .fxRate(new BigDecimal("1000")).trigger("매수").build();
  }

  // 판 거래(판 돈 USD, 환율 1000)
  private static StrategyTransaction sell(String shares, String proceedsUsd) {
    return StrategyTransaction.builder().date(SOLD).actualDate(SOLD).shares(new BigDecimal(shares).negate())
      .price(BigDecimal.ONE).amount(BigDecimal.ZERO).proceeds(new BigDecimal(proceedsUsd))
      .fxRate(new BigDecimal("1000")).trigger("매도").build();
  }

  private static List<YearEndHarvest.Sale> plan(List<StrategyTransaction> transactions, Map<String, String> prices,
    String feeRate) {
    return YearEndHarvest.plan(DECEMBER, transactions, prices.entrySet().stream()
      .collect(Collectors.toMap(Map.Entry::getKey, entry -> new BigDecimal(entry.getValue()))),
      new BigDecimal(feeRate), new BigDecimal("1000"));
  }

  @Nested
  @DisplayName("연말 정리 날")
  class Dates {

    private static OHLCPriceDto candle(LocalDate date, BigDecimal close) {
      return new OHLCPriceDto("YE", date, close, close, close, close, close, 1_000L, "USD", true);
    }

    @Test
    @DisplayName("결제가 그해 안에 끝나는 마지막 거래일이라 2024-05-28 전 해는 T+2 로 2023년 12월 27일, 그 뒤 해는 T+1 로 12월 30일이다")
    void lastTradingDaySettlingInYear() {
      List<LocalDate> candles = List.of(LocalDate.of(2023, 12, 22), LocalDate.of(2023, 12, 26),
        LocalDate.of(2023, 12, 27), LocalDate.of(2023, 12, 28), LocalDate.of(2023, 12, 29),
        LocalDate.of(2024, 12, 26), LocalDate.of(2024, 12, 27), LocalDate.of(2024, 12, 30), LocalDate.of(2024, 12, 31),
        LocalDate.of(2025, 12, 26), LocalDate.of(2025, 12, 29), LocalDate.of(2025, 12, 30), LocalDate.of(2025, 12, 31),
        LocalDate.of(2026, 1, 2));

      assertThat(YearEndHarvest.dates(candles, LocalDate.of(2023, 1, 3), LocalDate.of(2026, 1, 2)))
        .containsExactly(LocalDate.of(2023, 12, 27), LocalDate.of(2024, 12, 30), LocalDate.of(2025, 12, 30));
    }

    @Test
    @DisplayName("12월 31일이 평가일 뒤인 해, 연말 정리 날이 시작일 앞인 해, 12월 일봉이 결제 일수만큼 이어지지 않는 해는 뺀다")
    void skipsYearsWithoutSettlingDay() {
      List<LocalDate> candles = List.of(LocalDate.of(2020, 12, 29), LocalDate.of(2020, 12, 30),
        LocalDate.of(2020, 12, 31), LocalDate.of(2021, 11, 30), LocalDate.of(2021, 12, 29), LocalDate.of(2021, 12, 30),
        LocalDate.of(2021, 12, 31), LocalDate.of(2022, 11, 29), LocalDate.of(2022, 11, 30), LocalDate.of(2022, 12, 30),
        LocalDate.of(2023, 12, 26), LocalDate.of(2023, 12, 27), LocalDate.of(2023, 12, 28));

      assertThat(YearEndHarvest.dates(candles, LocalDate.of(2021, 1, 4), LocalDate.of(2023, 12, 29)))
        .containsExactly(LocalDate.of(2021, 12, 29));
    }

    @Test
    @DisplayName("쓸 수 없는 일봉 날은 거래일로 세지 않는다")
    void countsUsableCandlesOnly() {
      Map<LocalDate, OHLCPriceDto> priceMap = Stream.of(
          candle(LocalDate.of(2024, 12, 27), BigDecimal.TEN), candle(LocalDate.of(2024, 12, 30), BigDecimal.TEN),
          candle(DECEMBER, BigDecimal.ZERO))
        .collect(Collectors.toMap(OHLCPriceDto::date, price -> price));

      assertThat(YearEndHarvest.dates(priceMap, LocalDate.of(2024, 1, 2), DECEMBER))
        .containsExactly(LocalDate.of(2024, 12, 27));
    }
  }

  @Nested
  @DisplayName("팔 주식 수")
  class Plan {

    @Test
    @DisplayName("그해 실현 이익이 공제보다 적고 이익 난 보유가 있으면 공제를 채울 만큼 소수 여덟째에서 내려 판다")
    void fillsDeduction() {
      // 1000만원어치 100주가 160달러, 주당 6만원 이익, 250만원 ÷ 6만원
      assertThat(plan(List.of(buy(null, "100", "10000000")), Map.of("", "160"), "0"))
        .containsExactly(new YearEndHarvest.Sale("", new BigDecimal("41.66666666"), true));
    }

    @Test
    @DisplayName("수수료가 있으면 판 돈에서 수수료를 빼고 1센트 × 환율만큼 아래를 목표로 판다")
    void fillsBelowDeductionWithFee() {
      // 주당 160 × 0.9975 × 1000 - 10만원 = 5만 9,600원, (250만원 - 10원) ÷ 5만 9,600원
      assertThat(plan(List.of(buy(null, "100", "10000000")), Map.of("", "160"), "0.0025"))
        .containsExactly(new YearEndHarvest.Sale("", new BigDecimal("41.94614093"), true));
    }

    @Test
    @DisplayName("그해 실현 이익이 공제보다 많고 손실 난 보유가 있으면 공제까지만 내려가도록 판다")
    void offsetsLoss() {
      // 50주를 1만 달러에 팔아 500만원 이익, 남은 50주(원가 500만원)가 40달러라 주당 6만원 손실
      assertThat(plan(List.of(buy(null, "100", "10000000"), sell("50", "10000")), Map.of("", "40"), "0"))
        .containsExactly(new YearEndHarvest.Sale("", new BigDecimal("41.66666666"), false));
    }

    @Test
    @DisplayName("팔 수가 보유보다 많으면 보유 전부를 판다")
    void capsAtHolding() {
      // 주당 4만원 손실 × 50주 = 200만원, 내릴 250만원보다 적음
      assertThat(plan(List.of(buy(null, "100", "10000000"), sell("50", "10000")), Map.of("", "60"), "0"))
        .containsExactly(new YearEndHarvest.Sale("", new BigDecimal("50"), false));
    }

    @Test
    @DisplayName("공제를 채울 때 이익 난 보유가 없거나 손실을 상계할 때 손실 난 보유가 없으면 팔지 않는다")
    void nothingToSell() {
      assertThat(plan(List.of(buy(null, "100", "10000000")), Map.of("", "90"), "0")).isEmpty();
      assertThat(plan(List.of(buy(null, "100", "10000000"), sell("50", "10000")), Map.of("", "120"), "0")).isEmpty();
    }

    @Test
    @DisplayName("그해 실현 이익과 공제의 차이가 1달러 × 환율보다 작으면 공제 채우기도 손실 상계도 하지 않는다")
    void nothingLeftUnderOneDollar() {
      // 50주를 7500달러에 팔아 250만원 이익(수수료가 있으면 내릴 10원), 7499.5달러면 남은 목표 500원, 7500.9달러면 내릴 900원
      assertThat(plan(List.of(buy(null, "100", "10000000"), sell("50", "7500")), Map.of("", "160"), "0")).isEmpty();
      assertThat(plan(List.of(buy(null, "100", "10000000"), sell("50", "7500")), Map.of("", "40"), "0.0025")).isEmpty();
      assertThat(plan(List.of(buy(null, "100", "10000000"), sell("50", "7499.5")), Map.of("", "160"), "0"))
        .isEmpty();
      assertThat(plan(List.of(buy(null, "100", "10000000"), sell("50", "7500.9")), Map.of("", "40"), "0"))
        .isEmpty();
    }

    @Test
    @DisplayName("앞 종목을 판 뒤 남은 목표가 1달러 × 환율보다 작으면 다음 종목은 팔지 않는다")
    void stopsWhenLeftUnderOneDollar() {
      List<StrategyTransaction> bought = List.of(buy("AAA", "10", "1000000"), buy("BBB", "10", "1000000"));

      // BBB 10주 249만 9,500원 이익을 다 팔면 남은 목표 500원
      assertThat(plan(bought, Map.of("AAA", "200", "BBB", "349.95"), "0"))
        .containsExactly(new YearEndHarvest.Sale("BBB", new BigDecimal("10"), true));
    }

    @Test
    @DisplayName("공제 채우기는 평가 이익 큰 종목부터 팔고 그 날짜 값이 없거나 보유가 없는 종목은 건너뛴다")
    void ordersBySymbolGain() {
      List<StrategyTransaction> bought = List.of(buy("AAA", "10", "1000000"), buy("BBB", "10", "1000000"),
        buy("CCC", "10", "1000000"), buy("DDD", "10", "1000000"));

      // BBB 200만원 · AAA 100만원 · DDD 50만원 이익이라 AAA 까지 250만원, CCC 는 그 날짜 값 없음 · EEE 는 보유 없음
      assertThat(plan(bought, Map.of("AAA", "200", "BBB", "300", "DDD", "150", "EEE", "10"), "0")).containsExactly(
        new YearEndHarvest.Sale("BBB", new BigDecimal("10"), true),
        new YearEndHarvest.Sale("AAA", new BigDecimal("5.00000000"), true));
    }

    @Test
    @DisplayName("손실 상계는 평가 손실 큰 종목부터 팔고 다 판 종목은 건너뛴다")
    void offsetsBiggestLossFirst() {
      StrategyTransaction gain = sell("10", "6000");
      gain.setSymbol("CCC");
      List<StrategyTransaction> transactions = List.of(buy("AAA", "10", "1000000"), buy("BBB", "10", "1000000"),
        buy("CCC", "10", "1000000"), gain);

      // CCC 를 다 팔아 500만원 이익, AAA 50만원 · BBB 80만원 손실이라 BBB 전부 · AAA 전부
      assertThat(plan(transactions, Map.of("AAA", "50", "BBB", "20", "CCC", "700"), "0")).containsExactly(
        new YearEndHarvest.Sale("BBB", new BigDecimal("10"), false),
        new YearEndHarvest.Sale("AAA", new BigDecimal("10"), false));
    }
  }

  @Test
  @DisplayName("판 거래는 매도 수수료를 뺀 판 돈과 판 몫 원가 대비 실현 손익을, 다시 산 거래는 판 돈 전부로 매수 수수료를 빼고 산 주식을 남긴다")
  void tradeSellsAndRebuys() {
    YearEndHarvest.Trade trade = YearEndHarvest.trade(DECEMBER, new YearEndHarvest.Sale("", new BigDecimal("40"), true),
      new BigDecimal("160"), new BigDecimal("100"), new BigDecimal("10000"), new BigDecimal("0.0025"),
      new BigDecimal("1000"));

    // 40주 × 160달러 = 6400달러 - 수수료 16달러, 원가 4000달러
    StrategyTransaction sale = trade.sale();
    assertThat(sale.getShares()).isEqualByComparingTo("-40");
    assertThat(sale.getFee()).isEqualByComparingTo("16");
    assertThat(sale.getProceeds()).isEqualByComparingTo("6384");
    assertThat(sale.getRealizedProfit()).isEqualByComparingTo("2384");
    assertThat(sale.getTrigger()).isEqualTo("공제 채우기 매도");
    assertThat(sale.getFxRate()).isEqualByComparingTo("1000");
    // 6384달러 - 수수료 15.96달러 ÷ 160달러
    StrategyTransaction rebuy = trade.rebuy();
    assertThat(rebuy.getShares()).isEqualByComparingTo("39.80025");
    assertThat(rebuy.getFee()).isEqualByComparingTo("15.96");
    assertThat(rebuy.getCashUsed()).isEqualByComparingTo("6384");
    assertThat(rebuy.getAmount()).isEqualByComparingTo("0");
    assertThat(rebuy.getTrigger()).isEqualTo("공제 채우기 재매수");
    assertThat(rebuy.cashChange().add(sale.cashChange())).isEqualByComparingTo("0");
    assertThat(sale.isYearEndHarvest()).isTrue();
    assertThat(rebuy.isYearEndHarvest()).isTrue();
  }

  @Test
  @DisplayName("손실 상계 거래는 손실 상계 이유로 남기고 보유보다 많이 팔면 보유 전부 · 원가 전부다")
  void lossTradeSellsAll() {
    YearEndHarvest.Trade trade = YearEndHarvest.trade(DECEMBER, new YearEndHarvest.Sale("", new BigDecimal("50"), false),
      new BigDecimal("40"), new BigDecimal("50"), new BigDecimal("5000"), BigDecimal.ZERO, new BigDecimal("1000"));

    assertThat(trade.sale().getShares()).isEqualByComparingTo("-50");
    assertThat(trade.sale().getRealizedProfit()).isEqualByComparingTo("-3000");
    assertThat(trade.sale().getTrigger()).isEqualTo("손실 상계 매도");
    assertThat(trade.rebuy().getShares()).isEqualByComparingTo("50");
    assertThat(trade.rebuy().getTrigger()).isEqualTo("손실 상계 재매수");
  }

  @Test
  @DisplayName("종목 하나는 수수료율이 없어도 연말 정리하고, 기본 환율 날이면 판 거래 · 다시 산 거래에 그 날짜를 남긴다")
  void singleMarksDefaultFxDate() {
    YearEndHarvest.Trade trade = YearEndHarvest.single(DECEMBER, List.of(buy(null, "100", "10000000")),
      new BigDecimal("160"), new BigDecimal("100"), new BigDecimal("10000"), null, new BigDecimal("1000"), true);

    assertThat(trade.sale().getShares()).isEqualByComparingTo("-41.66666666");
    assertThat(trade.sale().getFee()).isEqualByComparingTo("0");
    assertThat(trade.sale().getDefaultFxDate()).isEqualTo(DECEMBER);
    assertThat(trade.rebuy().getDefaultFxDate()).isEqualTo(DECEMBER);
    assertThat(trade.sharesChange()).isEqualByComparingTo("0");
    // 판 몫 원가 4166.666666달러를 빼고 다시 산 6666.6666656달러를 더함
    assertThat(trade.costChange()).isEqualByComparingTo("2499.9999996");
  }

  @Test
  @DisplayName("종목 하나에 팔 것이 없으면 거래가 없다")
  void singleWithoutSale() {
    assertThat(YearEndHarvest.single(DECEMBER, List.of(buy(null, "100", "10000000")), new BigDecimal("90"),
      new BigDecimal("100"), new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("1000"), false)).isNull();
  }

  @Test
  @DisplayName("양도세와 연말 정리를 둘 다 켰을 때만 켠다")
  void enabledOnlyWithCapitalGainsTax() {
    assertThat(YearEndHarvest.enabled(true, true)).isTrue();
    assertThat(YearEndHarvest.enabled(false, true)).isFalse();
    assertThat(YearEndHarvest.enabled(null, true)).isFalse();
    assertThat(YearEndHarvest.enabled(true, null)).isFalse();
  }
}
