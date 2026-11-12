package com.muscat.backtest.common.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.domain.dto.response.TradeStats;
import com.muscat.backtest.domain.model.StrategyTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TradeStatsCalculator 사이클 매매 통계")
class TradeStatsCalculatorTest {

  private static final LocalDate START = LocalDate.of(2024, 1, 1);

  private static LocalDate on(int month, int day) {
    return LocalDate.of(2024, month, day);
  }

  // 새 돈(KRW)으로 산 거래(원가 = 원화 ÷ 환율), cashUsed 가 있으면 판 돈도 씀
  private static StrategyTransaction buy(LocalDate date, String shares, String amount, String fx, String cashUsed) {
    return StrategyTransaction.builder().date(date).actualDate(date).shares(new BigDecimal(shares))
      .price(BigDecimal.ONE).amount(new BigDecimal(amount)).fxRate(new BigDecimal(fx))
      .cashUsed(cashUsed == null ? null : new BigDecimal(cashUsed)).trigger("매수").build();
  }

  // 원화 130만원(환율 1300, 1000달러)으로 산 거래
  private static StrategyTransaction buy(LocalDate date, String shares) {
    return buy(date, shares, "1300000", "1300", null);
  }

  // 판 거래(실현 손익 USD)
  private static StrategyTransaction sell(LocalDate date, String shares, String realized) {
    return sell(date, shares, realized, "매도");
  }

  private static StrategyTransaction sell(LocalDate date, String shares, String realized, String trigger) {
    return StrategyTransaction.builder().date(date).actualDate(date).shares(new BigDecimal(shares).negate())
      .price(BigDecimal.ONE).amount(BigDecimal.ZERO).proceeds(BigDecimal.ONE).fxRate(new BigDecimal("1300"))
      .realizedProfit(new BigDecimal(realized)).trigger(trigger).build();
  }

  private static StrategyTransaction taxPayment(LocalDate date) {
    return StrategyTransaction.builder().date(date).actualDate(date).shares(BigDecimal.ZERO).price(BigDecimal.ZERO)
      .amount(BigDecimal.ZERO).fxRate(new BigDecimal("1300")).taxPaid(BigDecimal.TEN)
      .trigger(TransactionTrigger.CAPITAL_GAINS_TAX_PAYMENT).build();
  }

  private static StrategyTransaction reinvestment(LocalDate date, String shares) {
    return StrategyTransaction.builder().date(date).actualDate(date).shares(new BigDecimal(shares))
      .price(BigDecimal.TEN).amount(BigDecimal.ZERO).fxRate(new BigDecimal("1300"))
      .trigger(TransactionTrigger.DIVIDEND_REINVESTMENT).build();
  }

  private static StrategyTransaction symbol(String symbol, StrategyTransaction tx) {
    tx.setSymbol(symbol);
    return tx;
  }

  private static void assertStats(TradeStats actual, TradeStats expected) {
    assertThat(actual).usingRecursiveComparison()
      .withComparatorForType(Comparator.nullsFirst(BigDecimal::compareTo), BigDecimal.class)
      .isEqualTo(expected);
  }

  private static BigDecimal number(String value) {
    return new BigDecimal(value);
  }

  @Test
  @DisplayName("보유 0 에서 산 날부터 다시 0 이 된 날까지를 사이클로 보고 승률 · 평균 익절 · 손절 · 손익비 · 평균 보유일 · 1년 사이클 수를 낸다")
  void cyclesAndRates() {
    List<StrategyTransaction> transactions = List.of(
      // 1000달러로 사서 30일 뒤 200달러 이익(+20%)
      buy(on(1, 10), "10"), sell(on(2, 9), "10", "200"),
      // 판 돈 1200달러로 사서 31일 뒤 60달러 손실(-5%)
      buy(on(2, 20), "12", "0", "1300", "1200"), sell(on(3, 22), "12", "-60"),
      // 500달러 + (1000달러 + 판 돈 500달러)로 두 번 사고 두 번 나눠 팔아 60일 뒤 200달러 이익(+10%)
      buy(on(4, 1), "5", "650000", "1300", null), buy(on(4, 11), "15", "1300000", "1300", "500"),
      sell(on(5, 1), "8", "100"), sell(on(5, 31), "12", "100"),
      // 평가일에 들고 있음
      buy(on(6, 3), "10"));

    TradeStats stats = TradeStatsCalculator.of(transactions, START, on(7, 1));

    // 시작한 사이클 4 ÷ (182일 ÷ 365.25), 보유일 (30 + 31 + 60) ÷ 3
    assertStats(stats, new TradeStats(5, 4, 3, true, 2, 1, number("66.67"), number("15.00"), number("5.00"),
      number("3.00"), number("40.3"), number("8.03"), 0, 0));
  }

  @Test
  @DisplayName("양도세 납부 · 배당 재투자는 매수 · 매도 수에 세지 않고, 재투자한 주식까지 기간 끝에 판 날 사이클이 끝난다")
  void skipsTaxAndReinvestment() {
    // 거래일 순서가 아닌 목록(배당 재투자가 양도세 납부 뒤)
    List<StrategyTransaction> transactions = List.of(
      buy(on(1, 10), "10"), taxPayment(on(5, 2)), reinvestment(on(3, 15), "0.5"), sell(on(6, 28), "10.5", "50"));

    TradeStats stats = TradeStatsCalculator.of(transactions, START, on(6, 28));

    // 재투자 주식은 원가 0 이라 1000달러 대비 +5%, 1년 사이클 수는 179일 기준
    assertStats(stats, new TradeStats(1, 1, 1, false, 1, 0, number("100.00"), number("5.00"), null, null,
      number("170.0"), number("2.04"), 0, 0));
  }

  @Test
  @DisplayName("실현 손익 합이 0 인 사이클은 진 것으로 세고, 이긴 사이클이 없으면 평균 익절 · 손익비를 비운다")
  void breakEvenIsLoss() {
    List<StrategyTransaction> transactions = List.of(
      buy(on(1, 10), "10"), sell(on(1, 20), "10", "0"), buy(on(2, 1), "10"), sell(on(2, 11), "10", "-100"));

    TradeStats stats = TradeStatsCalculator.of(transactions, START, on(12, 31));

    // 0% · 10% 손실 평균, 365일 동안 시작한 사이클 2
    assertStats(stats, new TradeStats(2, 2, 2, false, 0, 2, number("0.00"), null, number("5.00"), null,
      number("10.0"), number("2.00"), 0, 0));
  }

  @Test
  @DisplayName("진 사이클이 없거나 평균 손절이 0 이면 손익비를 비운다")
  void noRatioWithoutLoss() {
    TradeStats onlyWins = TradeStatsCalculator.of(
      List.of(buy(on(1, 10), "10"), sell(on(1, 20), "10", "100")), START, on(12, 31));
    TradeStats zeroLoss = TradeStatsCalculator.of(
      List.of(buy(on(1, 10), "10"), sell(on(1, 20), "10", "100"), buy(on(2, 1), "10"), sell(on(2, 2), "10", "0")),
      START, on(12, 31));

    assertThat(onlyWins.averageLossPercent()).isNull();
    assertThat(onlyWins.profitLossRatio()).isNull();
    assertThat(zeroLoss.averageLossPercent()).isEqualByComparingTo("0");
    assertThat(zeroLoss.profitLossRatio()).isNull();
  }

  @Test
  @DisplayName("끝난 사이클이 없으면 승률 · 평균 · 손익비 · 평균 보유일을 비우고 수는 0, 진행 중인 사이클은 1년 사이클 수에 센다")
  void noClosedCycle() {
    TradeStats none = TradeStatsCalculator.of(List.of(), START, on(12, 31));
    TradeStats holding = TradeStatsCalculator.of(List.of(buy(on(1, 10), "10")), START, on(7, 1));

    assertStats(none, new TradeStats(0, 0, 0, false, 0, 0, null, null, null, null, null, number("0.00"), 0, 0));
    assertStats(holding, new TradeStats(1, 0, 0, true, 0, 0, null, null, null, null, null, number("2.01"), 0, 0));
  }

  @Test
  @DisplayName("트레일링 스탑으로 판 거래 수를 세고, 일부만 판 매도는 사이클을 끝내지 않는다")
  void trailingStopCount() {
    List<StrategyTransaction> transactions = List.of(
      // 1000달러로 사서 절반을 트레일링 스탑으로 팔고 22일 뒤 나머지를 팔아 40달러 이익(+4%)
      buy(on(1, 10), "10"), sell(on(1, 20), "5", "50", "고점 대비 하락 매도 10.0%"), sell(on(2, 1), "5", "-10", "데드 크로스"),
      // 1000달러로 사서 9일 뒤 트레일링 스탑으로 전량 팔아 100달러 손실(-10%)
      buy(on(3, 1), "10"), sell(on(3, 10), "10", "-100", "고점 대비 하락 매도 12.5%"));

    TradeStats stats = TradeStatsCalculator.of(transactions, START, on(12, 31));

    assertStats(stats, new TradeStats(2, 3, 2, false, 1, 1, number("50.00"), number("4.00"), number("10.00"),
      number("0.40"), number("15.5"), number("2.00"), 2, 0));
  }

  @Test
  @DisplayName("부분 익절로 판 거래 수를 트레일링 스탑과 따로 센다")
  void partialTakeProfitCount() {
    List<StrategyTransaction> transactions = List.of(
      // 1000달러로 사서 두 단계 부분 익절 뒤 나머지를 트레일링 스탑으로 팔아 170달러 이익(+17%)
      buy(on(1, 10), "10"), sell(on(1, 20), "5", "100", "부분 익절 +20%"), sell(on(1, 25), "2.5", "80", "부분 익절 +50%"),
      sell(on(2, 1), "2.5", "-10", "고점 대비 하락 매도 10.0%"));

    TradeStats stats = TradeStatsCalculator.of(transactions, START, on(12, 31));

    assertStats(stats, new TradeStats(1, 3, 1, false, 1, 0, number("100.00"), number("17.00"), null, null,
      number("22.0"), number("1.00"), 1, 2));
  }

  @Test
  @DisplayName("종목이 있는 거래는 종목마다 보유 0 에서 다시 0 이 될 때까지를 사이클로 보고 들고 있는 종목마다 진행 중으로 센다")
  void cyclesPerSymbol() {
    List<StrategyTransaction> transactions = List.of(
      // AAA · BBB 를 1000달러씩 사서 BBB 만 40일 뒤 전량 팔아 100달러 이익(+10%), AAA · CCC 는 평가일에 들고 있음
      symbol("AAA", buy(on(1, 10), "10")), symbol("BBB", buy(on(1, 10), "5")),
      symbol("BBB", sell(on(2, 19), "5", "100")), symbol("CCC", buy(on(3, 1), "10")));

    TradeStats stats = TradeStatsCalculator.of(transactions, START, on(12, 31));

    // 시작한 사이클 3(끝난 1 + 들고 있는 2) ÷ (365일 ÷ 365.25)
    assertStats(stats, new TradeStats(3, 1, 1, true, 1, 0, number("100.00"), number("10.00"), null, null,
      number("40.0"), number("3.00"), 0, 0));
  }

  @Test
  @DisplayName("시작일과 평가일이 같으면 1년 사이클 수를 비운다")
  void sameDayPeriod() {
    TradeStats stats = TradeStatsCalculator.of(List.of(buy(START, "10")), START, START);

    assertThat(stats.cyclesPerYear()).isNull();
  }
}
