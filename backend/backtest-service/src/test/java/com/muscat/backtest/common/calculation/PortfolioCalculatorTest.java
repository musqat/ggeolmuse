package com.muscat.backtest.common.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.common.engine.PortfolioEngine;
import com.muscat.backtest.common.engine.StrategyPerformance;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.PortfolioMethod;
import com.muscat.backtest.common.enums.type.TradeReason;
import com.muscat.backtest.domain.dto.request.PortfolioAsset;
import com.muscat.backtest.domain.dto.request.PortfolioRequest;
import com.muscat.backtest.domain.dto.response.CapitalGainsTax;
import com.muscat.backtest.domain.dto.response.DailyValue;
import com.muscat.backtest.domain.dto.response.PerformanceMetrics;
import com.muscat.backtest.domain.dto.response.PortfolioResponse;
import com.muscat.backtest.domain.dto.response.TradeStats;
import com.muscat.backtest.domain.model.StrategyTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PortfolioCalculator 포트폴리오 결과 · 종목 줄")
class PortfolioCalculatorTest {

  private static final LocalDate START = LocalDate.of(2024, 3, 4);
  private static final LocalDate END = LocalDate.of(2024, 12, 31);
  private static final LocalDate JUNE = LocalDate.of(2024, 6, 3);

  private static final RecursiveComparisonConfiguration NUMBERS = RecursiveComparisonConfiguration.builder()
    .withComparatorForType(Comparator.nullsFirst(BigDecimal::compareTo), BigDecimal.class)
    .build();

  private static BigDecimal number(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  private static StrategyTransaction buy(String symbol, LocalDate date, String price, String shares, String amount,
    String fx) {
    return StrategyTransaction.builder().symbol(symbol).reason(TradeReason.BUY).date(date).actualDate(date)
      .price(number(price)).shares(number(shares)).amount(number(amount)).fee(BigDecimal.ZERO).fxRate(number(fx))
      .trigger(TransactionTrigger.LUMP_SUM_PURCHASE).build();
  }

  private static StrategyTransaction sale(String symbol, LocalDate date, String price, String shares, String proceeds,
    String realized, String fx) {
    return StrategyTransaction.builder().symbol(symbol).reason(TradeReason.PERIOD_END_SALE).date(date).actualDate(date)
      .price(number(price)).shares(number(shares).negate()).amount(BigDecimal.ZERO).fee(BigDecimal.ZERO)
      .proceeds(number(proceeds)).realizedProfit(number(realized)).fxRate(number(fx))
      .trigger(TransactionTrigger.PERIOD_END_SALE).build();
  }

  private static PortfolioEngine.Position position(String symbol, String planned, String shares, String close,
    String value) {
    return new PortfolioEngine.Position(symbol, number(planned), number(shares), number(close), number(value));
  }

  private static PortfolioRequest.PortfolioRequestBuilder request(PortfolioAsset... assets) {
    return PortfolioRequest.builder().startDate(START).endDate(END).assets(List.of(assets));
  }

  private static PortfolioAsset asset(String symbol, String amount) {
    return new PortfolioAsset(symbol, number(amount), null);
  }

  private static PortfolioResponse.Asset row(String symbol, String invested, String value, String sold, String dividend,
    String profit, String returnPercent, String contribution, String startWeight, String endWeight, String shares,
    String realized, int buys, int sells) {
    return new PortfolioResponse.Asset(symbol, number(invested), number(value), number(sold), number(dividend),
      number(profit), number(returnPercent), number(contribution), number(startWeight), number(endWeight),
      number(shares), number(realized), buys, sells);
  }

  // 환율 1000 에 AAA 60달러 100주 보유(배당 200달러, 1050) · BBB 40달러 100주를 6월 3일 50달러에 판매(1100), 평가일 AAA 66달러 · 1300
  private static PortfolioEngine.Result soldOne(StrategyPerformance.Result performance) {
    List<StrategyTransaction> transactions = List.of(
      buy("AAA", START, "60", "100", "6000000", "1000"), buy("BBB", START, "40", "100", "4000000", "1000"),
      sale("BBB", JUNE, "50", "100", "5000", "1000", "1100"));
    return new PortfolioEngine.Result(START, END, transactions,
      List.of(new PortfolioEngine.Dividend("AAA", LocalDate.of(2024, 9, 13), number("200"), number("1050"))),
      List.of(position("AAA", "6000000", "100", "66", "8580000"), position("BBB", "4000000", "0", "50", "0")),
      number("10000000"), number("5200"), number("6760000"), number("8580000"), number("15340000"), number("1300"),
      performance, CapitalGainsTaxCalculator.Payments.NONE, List.of(JUNE));
  }

  @Test
  @DisplayName("종목 줄마다 넣은 돈 · 지금 가치 · 판 돈 · 배당 · 이익 · 수익률 · 기여 · 처음과 지금 비중을 내고 나머지 이익은 따로 낸다")
  void assetRows() {
    PortfolioResponse response = PortfolioCalculator.of(
      request(asset("AAA", "6000000"), asset("BBB", "4000000")).periodEnd(PeriodEnd.SELL_AT_END).build(),
      soldOne(new StrategyPerformance.Result(List.of(), null, null)));

    assertThat(response.assets()).usingRecursiveFieldByFieldElementComparator(NUMBERS).containsExactly(
      row("AAA", "6000000", "8580000", "0", "210000", "2790000", "46.50", "52.25", "60.00", "55.93", "100", "0", 1, 0),
      row("BBB", "4000000", "0", "5500000", "0", "1500000", "37.50", "28.09", "40.00", "0.00", "0", "1500000", 1, 1));
    // 현금 5200달러의 환율 차: 1300원 평가 - 받은 날 원화(550만원 + 21만원)
    assertThat(response.otherProfitKrw()).isEqualByComparingTo("1050000");
    assertThat(response.totalReturnKrw()).isEqualByComparingTo("5340000");
    assertThat(response.totalReturnPercent()).isEqualByComparingTo("53.40");
    assertThat(response.realizedProfitKrw()).isEqualByComparingTo("1500000");
    assertThat(response.assets().getFirst().shares().scale()).isEqualTo(6);
  }

  @Test
  @DisplayName("합계 · 매수와 매도 수 · 지표 · 일별 가치 · 거래 · 기본 환율 날을 싣고 판 거래가 있으면 종목마다 사이클인 매매 통계를 낸다")
  void totalsAndTradeStats() {
    DailyValue first = new DailyValue(START, number("10000"), number("10000000"));
    PerformanceMetrics metrics = new PerformanceMetrics(number("7.33"), number("-24.10"), null, null, null, null, null);
    PortfolioEngine.Result result = soldOne(new StrategyPerformance.Result(List.of(first), metrics, metrics));

    PortfolioResponse response = PortfolioCalculator.of(
      request(asset("AAA", "6000000"), asset("BBB", "4000000")).periodEnd(PeriodEnd.SELL_AT_END).build(), result);

    assertThat(response.startDate()).isEqualTo(START);
    assertThat(response.endDate()).isEqualTo(END);
    assertThat(response.method()).isEqualTo(PortfolioMethod.LUMP_SUM);
    assertThat(response.periodEnd()).isEqualTo(PeriodEnd.SELL_AT_END);
    assertThat(response.totalInvestedKrw()).isEqualByComparingTo("10000000");
    assertThat(response.totalAssetKrw()).isEqualByComparingTo("15340000");
    assertThat(response.stockValueKrw()).isEqualByComparingTo("8580000");
    assertThat(response.cashKrw()).isEqualByComparingTo("6760000");
    assertThat(response.cashUsd()).isEqualByComparingTo("5200.00");
    assertThat(response.currentFxRate()).isEqualByComparingTo("1300");
    assertThat(response.buyCount()).isEqualTo(2);
    assertThat(response.sellCount()).isEqualTo(1);
    assertThat(response.metricsKrw()).isSameAs(metrics);
    assertThat(response.dailyValues()).containsExactly(first);
    assertThat(response.transactions()).isEqualTo(result.transactions());
    assertThat(response.defaultFxDates()).containsExactly(JUNE);
    assertThat(response.capitalGainsTax()).isNull();
    // BBB 사이클 끝(4000달러 대비 +25%, 91일), AAA 진행 중, 시작한 사이클 2 ÷ (302일 ÷ 365.25)
    assertThat(response.tradeStats()).usingRecursiveComparison(NUMBERS).isEqualTo(new TradeStats(2, 1, 1, true, 1, 0,
      number("100.00"), number("25.00"), null, null, number("91.0"), number("2.42"), 0, 0));
  }

  @Test
  @DisplayName("양도세를 켜면 포트폴리오 전체 판 거래로 연도별 세금 · 납부와 세후 평가를 낸다")
  void capitalGainsTax() {
    PortfolioResponse response = PortfolioCalculator.of(
      request(asset("AAA", "6000000"), asset("BBB", "4000000")).capitalGainsTax(true).build(),
      soldOne(new StrategyPerformance.Result(List.of(), null, null)));

    // BBB 이익 150만원은 공제 안이라 세금 0
    assertThat(response.capitalGainsTax()).usingRecursiveComparison(NUMBERS).isEqualTo(new CapitalGainsTax(
      List.of(new CapitalGainsTax.Year(2024, number("1500000"), number("1500000"), number("0"), null, number("0"))),
      number("0"), number("0"), number("0"), number("6760000"), number("8580000"), number("15340000"),
      number("53.40")));
  }

  @Test
  @DisplayName("배당 재투자는 넣은 돈 · 배당에 같이 들어가 이익에서 상쇄되고 매수 수에는 세지 않는다")
  void reinvestedDividendRow() {
    LocalDate exDate = START.plusDays(2);
    StrategyTransaction reinvest = StrategyTransaction.builder().symbol("AAA").reason(TradeReason.DIVIDEND_REINVESTMENT)
      .date(exDate).actualDate(exDate).price(number("12.5")).shares(number("4")).amount(BigDecimal.ZERO)
      .cashUsed(number("50")).fxRate(number("1300")).trigger(TransactionTrigger.DIVIDEND_REINVESTMENT).build();
    PortfolioEngine.Result result = new PortfolioEngine.Result(START, END,
      List.of(buy("AAA", START, "10", "100", "1300000", "1300"), reinvest),
      List.of(new PortfolioEngine.Dividend("AAA", exDate, number("50"), number("1300"))),
      List.of(position("AAA", "1300000", "104", "12.5", "1690000")), number("1300000"), BigDecimal.ZERO,
      BigDecimal.ZERO, number("1690000"), number("1690000"), number("1300"),
      new StrategyPerformance.Result(List.of(), null, null), CapitalGainsTaxCalculator.Payments.NONE, List.of());

    PortfolioResponse response = PortfolioCalculator.of(request(asset("AAA", "1300000")).build(), result);

    assertThat(response.assets()).usingRecursiveFieldByFieldElementComparator(NUMBERS).containsExactly(
      row("AAA", "1365000", "1690000", "0", "65000", "390000", "28.57", "100.00", "100.00", "100.00", "104", "0", 1, 0));
    assertThat(response.otherProfitKrw()).isEqualByComparingTo("0");
    assertThat(response.buyCount()).isEqualTo(1);
    assertThat(response.tradeStats()).isNull();
  }

  @Test
  @DisplayName("산 것이 없으면 합 0 · 수익률 0 이고 줄마다 수익률 · 기여는 null, 지금 비중은 0 이다")
  void noPurchases() {
    PortfolioEngine.Result result = new PortfolioEngine.Result(START, END, List.of(), List.of(),
      List.of(position("AAA", "130000", "0", "10", "0")), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
      BigDecimal.ZERO, BigDecimal.ZERO, number("1350"), new StrategyPerformance.Result(List.of(), null, null),
      CapitalGainsTaxCalculator.Payments.NONE, List.of(END));

    PortfolioResponse response = PortfolioCalculator.of(
      request(asset("AAA", "130000")).method(PortfolioMethod.DCA).build(), result);

    assertThat(response.assets()).usingRecursiveFieldByFieldElementComparator(NUMBERS).containsExactly(
      row("AAA", "0", "0", "0", "0", "0", null, null, "100.00", "0", "0", "0", 0, 0));
    assertThat(response.totalReturnKrw()).isEqualByComparingTo("0");
    assertThat(response.totalReturnPercent()).isEqualByComparingTo("0");
    assertThat(response.method()).isEqualTo(PortfolioMethod.DCA);
    assertThat(response.periodEnd()).isEqualTo(PeriodEnd.HOLD);
    assertThat(response.dailyValues()).isEmpty();
    assertThat(response.metricsUsd()).isNull();
    assertThat(response.tradeStats()).isNull();
  }
}
