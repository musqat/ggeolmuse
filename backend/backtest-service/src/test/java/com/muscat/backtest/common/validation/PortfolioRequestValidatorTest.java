package com.muscat.backtest.common.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.PortfolioAllocation;
import com.muscat.backtest.common.enums.type.PortfolioMethod;
import com.muscat.backtest.common.enums.type.PortfolioStrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.PortfolioAsset;
import com.muscat.backtest.domain.dto.request.PortfolioRequest;
import com.muscat.backtest.domain.dto.request.PortfolioStrategy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PortfolioRequestValidator 포트폴리오 요청 검사")
class PortfolioRequestValidatorTest {

  private static final LocalDate START = LocalDate.of(2024, 1, 1);
  private static final LocalDate END = LocalDate.of(2024, 12, 31);

  private static BigDecimal number(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  private static PortfolioAsset amount(String symbol, String amount) {
    return new PortfolioAsset(symbol, number(amount), null, null, null, null);
  }

  private static PortfolioAsset weight(String symbol, String weight) {
    return new PortfolioAsset(symbol, null, number(weight), null, null, null);
  }

  private static PortfolioRequest.PortfolioRequestBuilder request(PortfolioAsset... assets) {
    return PortfolioRequest.builder().startDate(START).endDate(END).assets(new ArrayList<>(Arrays.asList(assets)));
  }

  // 총액 1000만원 비율 방식
  private static PortfolioRequest.PortfolioRequestBuilder weighted(PortfolioAsset... assets) {
    return request(assets).allocation(PortfolioAllocation.WEIGHT).totalAmount(number("10000000"));
  }

  private static void assertCode(PortfolioRequest request, BacktestResponse code) {
    assertThatThrownBy(() -> PortfolioRequestValidator.validate(request))
      .isInstanceOf(BacktestException.class)
      .extracting("code")
      .isEqualTo(code);
  }

  @Test
  @DisplayName("요청이 없으면 STRATEGY_REQUEST_NULL")
  void nullRequest() {
    assertCode(null, BacktestResponse.STRATEGY_REQUEST_NULL);
  }

  @Test
  @DisplayName("시작일 · 종료일은 단일 전략과 같은 기간 검사를 한다")
  void dateRange() {
    assertCode(request(amount("SPY", "1000")).startDate(null).build(), BacktestResponse.STRATEGY_START_DATE_REQUIRED);
    assertCode(request(amount("SPY", "1000")).startDate(END).endDate(START).build(),
      BacktestResponse.STRATEGY_DATE_RANGE_INVALID);
  }

  @Nested
  @DisplayName("종목 줄")
  class Assets {

    @Test
    @DisplayName("1 ~ 10개 줄 · 대문자로 서로 다른 종목이면 통과한다")
    void accepts() {
      PortfolioAsset[] ten = IntStream.range(0, 10).mapToObj(i -> amount("S" + i, "1000")).toArray(PortfolioAsset[]::new);

      assertThatCode(() -> PortfolioRequestValidator.validate(request(amount("SPY", "1000")).build()))
        .doesNotThrowAnyException();
      assertThatCode(() -> PortfolioRequestValidator.validate(request(ten).build())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("줄이 없거나 비었거나 10개를 넘으면 INVALID_PORTFOLIO")
    void count() {
      PortfolioAsset[] eleven = IntStream.range(0, 11).mapToObj(i -> amount("S" + i, "1000"))
        .toArray(PortfolioAsset[]::new);

      assertCode(request().assets(null).build(), BacktestResponse.INVALID_PORTFOLIO);
      assertCode(request().build(), BacktestResponse.INVALID_PORTFOLIO);
      assertCode(request(eleven).build(), BacktestResponse.INVALID_PORTFOLIO);
    }

    @Test
    @DisplayName("대문자로 같은 종목 · 지수 · 빈 줄이면 INVALID_PORTFOLIO")
    void duplicateIndexOrEmptyLine() {
      assertCode(request(amount("SPY", "1000"), amount(" spy", "1000")).build(), BacktestResponse.INVALID_PORTFOLIO);
      assertCode(request(amount("SPY", "1000"), amount("^GSPC", "1000")).build(), BacktestResponse.INVALID_PORTFOLIO);
      assertCode(request(amount("SPY", "1000"), null).build(), BacktestResponse.INVALID_PORTFOLIO);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "  "})
    @DisplayName("종목 코드가 비면 STRATEGY_SYMBOL_REQUIRED")
    void blankSymbol(String symbol) {
      assertCode(request(amount(symbol, "1000")).build(), BacktestResponse.STRATEGY_SYMBOL_REQUIRED);
      assertCode(request(amount(null, "1000")).build(), BacktestResponse.STRATEGY_SYMBOL_REQUIRED);
    }
  }

  @Nested
  @DisplayName("금액 · 비율")
  class Amounts {

    @Test
    @DisplayName("금액 방식에서 줄 금액이 비거나 0 이하이거나 비율이 있으면 INVALID_PORTFOLIO_AMOUNT")
    void amountAllocation() {
      assertCode(request(amount("SPY", null)).build(), BacktestResponse.INVALID_PORTFOLIO_AMOUNT);
      assertCode(request(amount("SPY", "0")).build(), BacktestResponse.INVALID_PORTFOLIO_AMOUNT);
      assertCode(request(new PortfolioAsset("SPY", number("1000"), number("100"), null, null, null)).build(),
        BacktestResponse.INVALID_PORTFOLIO_AMOUNT);
    }

    @Test
    @DisplayName("비율 방식은 총액과 줄마다 0 보다 큰 비율, 합 100 에서 0.01 안이면 통과한다")
    void weightAllocationAccepts() {
      assertThatCode(() -> PortfolioRequestValidator.validate(
        weighted(weight("SPY", "33.33"), weight("QQQ", "33.33"), weight("TLT", "33.33")).build()))
        .doesNotThrowAnyException();
      assertThatCode(() -> PortfolioRequestValidator.validate(weighted(weight("SPY", "100.01")).build()))
        .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("비율 방식의 총액 · 비율이 비거나 0 이하이거나 합이 100 에서 0.01 넘게 다르면 INVALID_PORTFOLIO_AMOUNT")
    void weightAllocationRejects() {
      assertCode(weighted(weight("SPY", "100")).totalAmount(null).build(), BacktestResponse.INVALID_PORTFOLIO_AMOUNT);
      assertCode(weighted(weight("SPY", "100")).totalAmount(number("0")).build(),
        BacktestResponse.INVALID_PORTFOLIO_AMOUNT);
      assertCode(weighted(weight("SPY", "100"), weight("QQQ", null)).build(), BacktestResponse.INVALID_PORTFOLIO_AMOUNT);
      assertCode(weighted(weight("SPY", "100"), weight("QQQ", "0")).build(), BacktestResponse.INVALID_PORTFOLIO_AMOUNT);
      assertCode(weighted(weight("SPY", "60"), weight("QQQ", "39.98")).build(), BacktestResponse.INVALID_PORTFOLIO_AMOUNT);
    }

    @Test
    @DisplayName("줄 금액 · 총액에 원 미만이 있으면 INVALID_PORTFOLIO_AMOUNT")
    void subWonAmounts() {
      assertCode(request(amount("SPY", "1000000.5"), amount("QQQ", "1000000")).build(),
        BacktestResponse.INVALID_PORTFOLIO_AMOUNT);
      assertCode(weighted(weight("SPY", "100")).totalAmount(number("10000000.5")).build(),
        BacktestResponse.INVALID_PORTFOLIO_AMOUNT);
    }

    @Test
    @DisplayName("줄 금액 · 총액의 소수 자리가 0 뿐이면 통과한다")
    void wholeWonWithZeroDecimals() {
      assertThatCode(() -> PortfolioRequestValidator.validate(request(amount("SPY", "1000000.00")).build()))
        .doesNotThrowAnyException();
      assertThatCode(() -> PortfolioRequestValidator.validate(
        weighted(weight("SPY", "100")).totalAmount(number("10000000.0")).build())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("비율 방식의 줄 금액이 원 단위 반올림으로 0 이면 INVALID_PORTFOLIO_AMOUNT")
    void weightRoundsToZero() {
      // 총액 10원의 1% = 0.1원
      assertCode(weighted(weight("SPY", "99"), weight("QQQ", "1")).totalAmount(number("10")).build(),
        BacktestResponse.INVALID_PORTFOLIO_AMOUNT);
    }
  }

  @Nested
  @DisplayName("익절 · 손절")
  class SellRates {

    private PortfolioAsset selling(String takeProfitRate, String stopLossRate) {
      return new PortfolioAsset("QQQ", number("1000"), null, number(takeProfitRate), number(stopLossRate), null);
    }

    @Test
    @DisplayName("줄마다 익절 비율은 비우거나 0 초과, 손절 비율은 비우거나 0 초과 1 미만이면 통과한다")
    void accepts() {
      assertThatCode(() -> PortfolioRequestValidator.validate(
        request(amount("SPY", "1000"), selling("2", "0.99")).build())).doesNotThrowAnyException();
      assertThatCode(() -> PortfolioRequestValidator.validate(request(selling("0.01", null)).build()))
        .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("줄 하나라도 익절 비율이 0 이하이거나 손절 비율이 0 이하 · 1 이상이면 INVALID_SELL_RATE")
    void rejects() {
      assertCode(request(amount("SPY", "1000"), selling("0", null)).build(), BacktestResponse.INVALID_SELL_RATE);
      assertCode(request(amount("SPY", "1000"), selling(null, "1")).build(), BacktestResponse.INVALID_SELL_RATE);
      assertCode(request(selling(null, "0")).build(), BacktestResponse.INVALID_SELL_RATE);
    }

    @Test
    @DisplayName("금액 검사가 익절 · 손절 검사보다 먼저다")
    void amountsFirst() {
      assertCode(request(new PortfolioAsset("SPY", number("0"), null, number("0"), null, null)).build(),
        BacktestResponse.INVALID_PORTFOLIO_AMOUNT);
    }
  }

  @Nested
  @DisplayName("적립식 칸")
  class Schedule {

    private PortfolioRequest.PortfolioRequestBuilder dca() {
      return request(amount("SPY", "100000")).method(PortfolioMethod.DCA);
    }

    @ParameterizedTest(name = "투자일 {0}")
    @ValueSource(ints = {0, 32})
    @DisplayName("적립식 투자일이 1 ~ 31 밖이면 INVALID_PURCHASE_DAY")
    void purchaseDay(int day) {
      assertCode(dca().purchaseDay(day).build(), BacktestResponse.INVALID_PURCHASE_DAY);
    }

    @ParameterizedTest(name = "투자 주기 {0}")
    @ValueSource(ints = {0, 13})
    @DisplayName("적립식 투자 주기가 1 ~ 12 밖이면 INVALID_INVESTMENT_INTERVAL")
    void interval(int interval) {
      assertCode(dca().investmentInterval(interval).build(), BacktestResponse.INVALID_INVESTMENT_INTERVAL);
    }

    @Test
    @DisplayName("적립식 첫 매수일이 기간 밖이면 INVALID_FIRST_PURCHASE_DATE")
    void firstPurchaseDate() {
      assertCode(dca().firstPurchaseDate(END.plusDays(1)).build(), BacktestResponse.INVALID_FIRST_PURCHASE_DATE);
    }

    @Test
    @DisplayName("적립식 칸이 범위 안이면 통과하고 한 번에 사기는 적립식 칸을 보지 않는다")
    void acceptsOrIgnores() {
      assertThatCode(() -> PortfolioRequestValidator.validate(
        dca().purchaseDay(31).investmentInterval(12).firstPurchaseDate(END).build())).doesNotThrowAnyException();
      assertThatCode(() -> PortfolioRequestValidator.validate(request(amount("SPY", "100000")).purchaseDay(0)
        .investmentInterval(0).firstPurchaseDate(END.plusDays(1)).build())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("한 번에 사기 요청이라도 적립식 줄이 있으면 적립식 칸을 검사하고 적립식 요청의 줄이 모두 한 번에 사기면 보지 않는다")
    void lineStrategies() {
      assertCode(request(amount("SPY", "100000"), planned("QQQ", PortfolioStrategyType.DCA)).purchaseDay(32).build(),
        BacktestResponse.INVALID_PURCHASE_DAY);
      assertThatCode(() -> PortfolioRequestValidator.validate(request(planned("SPY", PortfolioStrategyType.LUMP_SUM))
        .method(PortfolioMethod.DCA).purchaseDay(0).build())).doesNotThrowAnyException();
    }

    private PortfolioAsset planned(String symbol, PortfolioStrategyType type) {
      return new PortfolioAsset(symbol, number("100000"), null, null, null,
        new PortfolioStrategy(type, null, null, null, null, null));
    }
  }

  @Nested
  @DisplayName("이동평균 · 밴드 줄")
  class SignalLines {

    private PortfolioAsset signal(PortfolioStrategy strategy) {
      return new PortfolioAsset("QQQ", number("1000"), null, null, null, strategy);
    }

    private PortfolioStrategy cross(Integer shortWindow, Integer longWindow) {
      return new PortfolioStrategy(PortfolioStrategyType.MA_CROSS, shortWindow, longWindow, null, null, null);
    }

    private PortfolioStrategy band(Integer window, String upperRate, String lowerRate) {
      return new PortfolioStrategy(PortfolioStrategyType.MA_BAND, null, null, window, number(upperRate),
        number(lowerRate));
    }

    @Test
    @DisplayName("이동평균 기간 · 밴드 기간과 비율을 비우거나 단일 전략과 같은 범위 안이면 통과한다")
    void accepts() {
      assertThatCode(() -> PortfolioRequestValidator.validate(request(amount("SPY", "1000"), signal(cross(null, null)))
        .build())).doesNotThrowAnyException();
      assertThatCode(() -> PortfolioRequestValidator.validate(request(signal(cross(2, 250))).build()))
        .doesNotThrowAnyException();
      assertThatCode(() -> PortfolioRequestValidator.validate(request(signal(band(null, null, null))).build()))
        .doesNotThrowAnyException();
      assertThatCode(() -> PortfolioRequestValidator.validate(request(signal(band(250, "0", "0.99"))).build()))
        .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이동평균 줄의 기간이 2 ~ 250 밖이거나 짧은 기간이 긴 기간보다 짧지 않으면 INVALID_MA_WINDOWS")
    void crossRejects() {
      assertCode(request(amount("SPY", "1000"), signal(cross(1, 60))).build(), BacktestResponse.INVALID_MA_WINDOWS);
      assertCode(request(signal(cross(20, 251))).build(), BacktestResponse.INVALID_MA_WINDOWS);
      assertCode(request(signal(cross(60, 60))).build(), BacktestResponse.INVALID_MA_WINDOWS);
      // 긴 기간을 비우면 60
      assertCode(request(signal(cross(70, null))).build(), BacktestResponse.INVALID_MA_WINDOWS);
    }

    @Test
    @DisplayName("밴드 줄의 기간이 2 ~ 250 밖이거나 위 · 아래 비율이 0 이상 1 미만 밖이면 INVALID_BAND")
    void bandRejects() {
      assertCode(request(signal(band(1, null, null))).build(), BacktestResponse.INVALID_BAND);
      assertCode(request(signal(band(251, null, null))).build(), BacktestResponse.INVALID_BAND);
      assertCode(request(signal(band(null, "-0.01", null))).build(), BacktestResponse.INVALID_BAND);
      assertCode(request(signal(band(null, null, "1"))).build(), BacktestResponse.INVALID_BAND);
    }

    @Test
    @DisplayName("한 번에 사기 · 적립식 줄은 이동평균 · 밴드 칸을 보지 않는다")
    void ignoresOtherLines() {
      assertThatCode(() -> PortfolioRequestValidator.validate(request(signal(new PortfolioStrategy(
        PortfolioStrategyType.LUMP_SUM, 1, 1, 1, number("-1"), number("1")))).build())).doesNotThrowAnyException();
    }
  }

  @Test
  @DisplayName("종목 줄 검사가 금액 검사보다 먼저다")
  void assetsBeforeAmounts() {
    assertCode(request(amount("SPY", "0"), amount("SPY", "0")).build(), BacktestResponse.INVALID_PORTFOLIO);
    assertCode(PortfolioRequest.builder().startDate(START).endDate(END).assets(List.of(amount("^NDX", "0"))).build(),
      BacktestResponse.INVALID_PORTFOLIO);
  }
}
