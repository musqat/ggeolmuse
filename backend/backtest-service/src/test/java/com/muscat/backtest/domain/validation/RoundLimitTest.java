package com.muscat.backtest.domain.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.backtest.common.constants.BacktestConstants.RequestLimit;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.PortfolioMethod;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaCrossStrategyRequest;
import com.muscat.backtest.domain.dto.request.PortfolioAsset;
import com.muscat.backtest.domain.dto.request.PortfolioRequest;
import com.muscat.backtest.domain.dto.request.StrategyRequest;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("RoundLimit 적립 · 매수 회차 상한")
class RoundLimitTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);
  private static final LocalDate END = LocalDate.of(2025, 12, 31);

  // 회당 10만원으로 나눈 몫이 long 범위를 넘는 예산(아래 64비트만 남기면 1 · 음수)
  private static final List<BigDecimal> HUGE_BUDGETS = List.of(
    new BigDecimal(BigInteger.TWO.pow(64).add(BigInteger.ONE)).multiply(new BigDecimal("100000")),
    new BigDecimal(BigInteger.TWO.pow(63)).multiply(new BigDecimal("100000")));

  // END 까지 평일이 n 일이 되는 시작일
  private static LocalDate startFor(long n) {
    LocalDate day = END;
    long count = 0;
    while (true) {
      if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY && ++count == n) {
        return day;
      }
      day = day.minusDays(1);
    }
  }

  private static DcaStrategyRequest.DcaStrategyRequestBuilder<?, ?> dca(DcaFrequency frequency, LocalDate start) {
    return DcaStrategyRequest.builder().symbol("SPY").startDate(start).endDate(END)
      .amountPerPurchase(new BigDecimal("100000")).frequency(frequency);
  }

  private static ConditionalStrategyRequest.ConditionalStrategyRequestBuilder<?, ?> conditional(LocalDate start) {
    return ConditionalStrategyRequest.builder().symbol("SPY").startDate(start).endDate(END)
      .amountPerPurchase(new BigDecimal("100000")).dropPercentage(new BigDecimal("0.05"));
  }

  private static void assertRejected(ThrowingCallable call, String message) {
    assertThatThrownBy(call)
      .isInstanceOf(BacktestException.class)
      .hasMessage(message)
      .extracting("code")
      .isEqualTo(BacktestResponse.TOO_MANY_ROUNDS);
  }

  @Nested
  @DisplayName("회차 수")
  class Counts {

    @ParameterizedTest(name = "{0} {1}개월 {2} ~ {3} 는 {4}회")
    @CsvSource({
      "DAILY, 1, 2023-01-02, 2023-01-13, 10",
      "DAILY, 1, 2023-01-07, 2023-01-08, 0",
      "WEEKLY, 1, 2023-01-01, 2023-01-31, 5",
      "WEEKLY, 1, 2023-01-04, 2023-01-31, 4",
      "WEEKLY, 1, 2023-01-07, 2023-01-08, 0",
      "MONTHLY, 1, 2023-01-31, 2023-05-31, 5",
      "MONTHLY, 1, 2023-01-31, 2023-03-02, 2",
      "MONTHLY, 1, 2023-01-15, 2023-05-14, 4",
      "MONTHLY, 3, 2023-01-15, 2023-12-31, 4",
      "MONTHLY, 1, 2023-01-15, 2023-01-14, 0",
      "MONTHLY, 1, 2024-05-15, 2024-06-16, 1",
      "MONTHLY, 1, 2024-05-15, 2024-06-17, 2",
      "MONTHLY, 1, 2024-07-31, 2024-08-31, 2",
    })
    @DisplayName("휴장일도 평일로 세고 주말만 뺀 회차 수")
    void rounds(DcaFrequency frequency, int interval, LocalDate first, LocalDate end, long expected) {
      assertThat(RoundLimit.dcaRounds(frequency, interval, first, end, null, TODAY)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} 는 {1} 부터 {2}회")
    @CsvSource({"DAILY, 2026-10-05, 3", "WEEKLY, 2026-10-01, 1", "MONTHLY, 2026-09-08, 1"})
    @DisplayName("종가가 없는 오늘 회차는 세지 않음")
    void skipsToday(DcaFrequency frequency, LocalDate first, long expected) {
      assertThat(RoundLimit.dcaRounds(frequency, 1, first, TODAY, null, TODAY)).isEqualTo(expected);
      assertThat(RoundLimit.dcaRounds(frequency, 1, first, TODAY.minusDays(1), null, TODAY)).isEqualTo(expected);
    }

    @Test
    @DisplayName("매달 투자일은 첫 매수일의 날짜 대신 정한 날짜")
    void purchaseDay() {
      assertThat(RoundLimit.dcaRounds(DcaFrequency.MONTHLY, 1, LocalDate.of(2025, 4, 30), LocalDate.of(2025, 5, 30),
        31, TODAY)).isEqualTo(1);
    }

    @Test
    @DisplayName("평일 수는 어제까지")
    void weekdays() {
      assertThat(RoundLimit.weekdays(LocalDate.of(2023, 1, 2), LocalDate.of(2023, 1, 13), TODAY)).isEqualTo(10);
      assertThat(RoundLimit.weekdays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 12, 31), TODAY)).isEqualTo(3);
      assertThat(RoundLimit.weekdays(TODAY, TODAY, TODAY)).isZero();
    }
  }

  @Nested
  @DisplayName("적립식")
  class Dca {

    @Test
    @DisplayName("매일 회차 × 평일이 4,000 × 4,000 까지 통과")
    void dailyAtLimit() {
      assertThat(RequestLimit.MAX_ROUND_LOAD).isEqualTo(4_000L * 4_000L);
      assertThatCode(() -> RoundLimit.requireDca(dca(DcaFrequency.DAILY, startFor(4_000)).build(), TODAY))
        .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("매일 4,001회면 TOO_MANY_ROUNDS 와 최대 몇 년인지 안내")
    void dailyOverLimit() {
      assertRejected(() -> RoundLimit.requireDca(dca(DcaFrequency.DAILY, startFor(4_001)).build(), TODAY),
        "매일 적립은 최대 약 15년 — 기간 줄이기 · 주기 늘리기");
    }

    @Test
    @DisplayName("매주 · N개월마다는 회차가 적은 만큼 더 긴 기간까지 통과")
    void weeklyAndMonthly() {
      assertThatCode(() -> RoundLimit.requireDca(dca(DcaFrequency.WEEKLY, END.minusYears(34)).build(), TODAY))
        .doesNotThrowAnyException();
      assertRejected(() -> RoundLimit.requireDca(dca(DcaFrequency.WEEKLY, END.minusYears(36)).build(), TODAY),
        "매주 적립은 최대 약 34년 — 기간 줄이기 · 주기 늘리기");
      assertThatCode(() -> RoundLimit.requireDca(dca(DcaFrequency.MONTHLY, END.minusYears(70)).build(), TODAY))
        .doesNotThrowAnyException();
      assertRejected(() -> RoundLimit.requireDca(
          dca(DcaFrequency.MONTHLY, END.minusYears(75)).investmentInterval(1).build(), TODAY),
        "매달 적립은 최대 약 71년 — 기간 줄이기 · 주기 늘리기");
      assertRejected(() -> RoundLimit.requireDca(
          dca(DcaFrequency.MONTHLY, END.minusYears(130)).investmentInterval(3).build(), TODAY),
        "3개월마다 적립은 최대 약 123년 — 기간 줄이기 · 주기 늘리기");
    }

    @Test
    @DisplayName("첫 매수일부터 셈")
    void fromFirstPurchase() {
      DcaStrategyRequest request = dca(DcaFrequency.DAILY, END.minusYears(30)).firstPurchaseDate(startFor(4_000)).build();

      assertThatCode(() -> RoundLimit.requireDca(request, TODAY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("회당 금액이면 예산으로 살 수 있는 회차까지만 셈, 주식 수는 예산을 보지 않음")
    void budget() {
      DcaStrategyRequest byAmount = dca(DcaFrequency.DAILY, END.minusYears(30))
        .totalInvestmentLimit(new BigDecimal("100000000")).build();
      DcaStrategyRequest byShares = dca(DcaFrequency.DAILY, END.minusYears(30)).amountPerPurchase(null)
        .sharesPerPurchase(1).totalInvestmentLimit(new BigDecimal("100000000")).build();

      assertThat(RoundLimit.load(byAmount, TODAY)).isEqualTo(1_000L * RoundLimit.weekdays(END.minusYears(30), END, TODAY));
      assertThatCode(() -> RoundLimit.requireDca(byAmount, TODAY)).doesNotThrowAnyException();
      assertRejected(() -> RoundLimit.requireDca(byShares, TODAY),
        "매일 적립은 최대 약 15년 — 기간 줄이기 · 주기 늘리기");
    }

    @Test
    @DisplayName("예산 ÷ 회당 금액이 long 범위를 넘으면 예산 제한 없이 셈")
    void hugeBudget() {
      for (BigDecimal budget : HUGE_BUDGETS) {
        assertRejected(() -> RoundLimit.requireDca(
            dca(DcaFrequency.DAILY, startFor(4_001)).totalInvestmentLimit(budget).build(), TODAY),
          "매일 적립은 최대 약 15년 — 기간 줄이기 · 주기 늘리기");
      }
    }
  }

  @Nested
  @DisplayName("조건부")
  class Conditional {

    @Test
    @DisplayName("예산 · 최대 횟수가 없으면 평일마다 살 수 있다고 보고 4,000일까지 통과")
    void unlimited() {
      assertThatCode(() -> RoundLimit.requireConditional(conditional(startFor(4_000)).build(), TODAY))
        .doesNotThrowAnyException();
      assertRejected(() -> RoundLimit.requireConditional(conditional(startFor(5_000)).build(), TODAY),
        "이 기간이면 최대 매수 3,200회까지 — 최대 매수 횟수 정하기 · 기간 줄이기");
    }

    @Test
    @DisplayName("최대 횟수 · 예산으로 살 수 있는 회차까지만 셈, 주식 수는 예산을 보지 않음")
    void limits() {
      LocalDate start = END.minusYears(60);
      long days = RoundLimit.weekdays(start, END, TODAY);

      assertThat(RoundLimit.load(conditional(start).maxPurchases(300).build(), TODAY)).isEqualTo(300 * days);
      assertThat(RoundLimit.load(conditional(start).totalInvestment(new BigDecimal("1000000")).build(), TODAY))
        .isEqualTo(10 * days);
      assertThat(RoundLimit.load(conditional(start).amountPerPurchase(null).sharesPerPurchase(1)
        .totalInvestment(new BigDecimal("1000000")).build(), TODAY)).isEqualTo(days * days);
    }

    @Test
    @DisplayName("예산 ÷ 회당 금액이 long 범위를 넘으면 예산 제한 없이 셈")
    void hugeBudget() {
      for (BigDecimal budget : HUGE_BUDGETS) {
        assertRejected(() -> RoundLimit.requireConditional(
            conditional(startFor(5_000)).totalInvestment(budget).build(), TODAY),
          "이 기간이면 최대 매수 3,200회까지 — 최대 매수 횟수 정하기 · 기간 줄이기");
      }
    }

    @Test
    @DisplayName("매도 규칙이 있으면 판 돈으로 다시 사서 예산 · 최대 횟수와 상관없이 평일마다 셈")
    void sells() {
      ConditionalStrategyRequest request = conditional(END.minusYears(30)).maxPurchases(5)
        .totalInvestment(new BigDecimal("500000")).takeProfitRate(new BigDecimal("0.1")).build();

      assertRejected(() -> RoundLimit.requireConditional(request, TODAY),
        "매도 규칙이 있는 조건부 매수는 최대 약 15년 — 기간 줄이기");
    }
  }

  @Nested
  @DisplayName("포트폴리오")
  class Portfolio {

    private PortfolioRequest.PortfolioRequestBuilder portfolio(PortfolioMethod method, LocalDate start) {
      return PortfolioRequest.builder().startDate(start).endDate(END).method(method)
        .assets(List.of(new PortfolioAsset("SPY", new BigDecimal("100000"), null, null, null, null, null)));
    }

    @Test
    @DisplayName("적립식 줄의 같은 일정을 적립식과 같은 상한으로 셈")
    void dcaLines() {
      assertThatCode(() -> RoundLimit.requirePortfolio(
        portfolio(PortfolioMethod.DCA, startFor(4_000)).frequency(DcaFrequency.DAILY).build(), TODAY))
        .doesNotThrowAnyException();
      assertRejected(() -> RoundLimit.requirePortfolio(
          portfolio(PortfolioMethod.DCA, startFor(4_001)).frequency(DcaFrequency.DAILY).build(), TODAY),
        "매일 적립은 최대 약 15년 — 기간 줄이기 · 주기 늘리기");
    }

    @Test
    @DisplayName("적립식 줄이 없으면 세지 않음")
    void lumpSum() {
      assertThatCode(() -> RoundLimit.requirePortfolio(
        portfolio(PortfolioMethod.LUMP_SUM, END.minusYears(60)).frequency(DcaFrequency.DAILY).build(), TODAY))
        .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("전략 비교")
  class Comparison {

    private List<StrategyRequest> dailyChips(int count, long days) {
      return Collections.<StrategyRequest>nCopies(count, dca(DcaFrequency.DAILY, startFor(days)).build());
    }

    @Test
    @DisplayName("칩 합은 칩 하나 상한의 3배까지 통과")
    void total() {
      assertThat(RequestLimit.MAX_COMPARISON_ROUND_LOAD).isEqualTo(3 * RequestLimit.MAX_ROUND_LOAD);
      assertThatCode(() -> RoundLimit.requireComparison(dailyChips(3, 4_000), TODAY)).doesNotThrowAnyException();
      assertRejected(() -> RoundLimit.requireComparison(dailyChips(4, 4_000), TODAY),
        "칩 회차 합이 너무 많음 — 칩 빼기 · 기간 줄이기 · 주기 늘리기");
    }

    @Test
    @DisplayName("칩 하나가 상한을 넘으면 넘은 칩 안내, 회차 일정이 없는 칩은 세지 않음")
    void chip() {
      StrategyRequest maCross = MaCrossStrategyRequest.builder().symbol("SPY").startDate(END.minusYears(60))
        .endDate(END).investmentAmount(new BigDecimal("1000000")).build();

      assertThat(RoundLimit.load(maCross, TODAY)).isZero();
      assertRejected(() -> RoundLimit.requireComparison(List.of(maCross, dailyChips(1, 4_001).getFirst()), TODAY),
        "매일 적립은 최대 약 15년 — 기간 줄이기 · 주기 늘리기");
      assertRejected(() -> RoundLimit.requireComparison(List.of(conditional(startFor(5_000)).build()), TODAY),
        "이 기간이면 최대 매수 3,200회까지 — 최대 매수 횟수 정하기 · 기간 줄이기");
    }

    @Test
    @DisplayName("투자금 ÷ 회당 금액이 long 범위를 넘는 칩도 칩 상한 · 칩 합 상한으로 셈")
    void hugeBudget() {
      for (BigDecimal budget : HUGE_BUDGETS) {
        StrategyRequest over = dca(DcaFrequency.DAILY, startFor(4_001)).totalInvestmentLimit(budget).build();
        StrategyRequest atLimit = dca(DcaFrequency.DAILY, startFor(4_000)).totalInvestmentLimit(budget).build();

        assertRejected(() -> RoundLimit.requireComparison(List.of(over), TODAY),
          "매일 적립은 최대 약 15년 — 기간 줄이기 · 주기 늘리기");
        assertRejected(() -> RoundLimit.requireComparison(Collections.nCopies(4, atLimit), TODAY),
          "칩 회차 합이 너무 많음 — 칩 빼기 · 기간 줄이기 · 주기 늘리기");
      }
    }

    @Test
    @DisplayName("상한을 넘은 요청은 400")
    void badRequest() {
      assertThat(BacktestResponse.TOO_MANY_ROUNDS.getHttpStatus().value()).isEqualTo(400);
    }
  }
}
