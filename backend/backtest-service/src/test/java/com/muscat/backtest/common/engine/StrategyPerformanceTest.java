package com.muscat.backtest.common.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.common.constants.BacktestConstants;
import com.muscat.backtest.domain.dto.response.DailyValue;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.dto.DividendDto;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 기본 환율 1300 · 한 번 130만원(1,000달러) 매수
@DisplayName("StrategyPerformance 일별 가치 · 시간가중 지표")
class StrategyPerformanceTest {

  private static final LocalDate D1 = LocalDate.of(2024, 1, 2);
  private static final LocalDate D2 = LocalDate.of(2024, 1, 3);
  private static final LocalDate D3 = LocalDate.of(2024, 1, 4);
  private static final LocalDate D4 = LocalDate.of(2024, 1, 5);
  private static final LocalDate D5 = LocalDate.of(2024, 1, 8);
  private static final BigDecimal FX = new BigDecimal("1300");
  private static final Function<LocalDate, BigDecimal> FIXED_FX = date -> FX;
  private static final DividendHistoryDto NO_DIVIDENDS = DividendHistoryDto.of("AAPL", List.of());

  private static StrategyTransaction buy(LocalDate date, String price, String shares) {
    return StrategyTransaction.builder().date(date).actualDate(date).price(new BigDecimal(price))
      .shares(new BigDecimal(shares)).amount(new BigDecimal("1300000")).fxRate(FX).trigger("월정액")
      .fee(BigDecimal.ZERO).build();
  }

  private static OHLCPriceDto candle(LocalDate date, String close) {
    BigDecimal price = new BigDecimal(close);
    return new OHLCPriceDto("AAPL", date, price, price, price, price, null, 1000L, "USD", true);
  }

  private static Map<LocalDate, OHLCPriceDto> prices(OHLCPriceDto... candles) {
    return Arrays.stream(candles).collect(Collectors.toMap(OHLCPriceDto::date, c -> c));
  }

  private static BigDecimal totalKrw(String usd) {
    return new BigDecimal(usd).multiply(FX);
  }

  @Test
  @DisplayName("입금을 뺀 수익률로 지수를 만들어 두 번째 매수의 입금을 수익으로 보지 않는다")
  void timeWeightedIgnoresDeposits() {
    // 10주 @100 → 110 → 10주 더 @100 → 평가일 120
    List<StrategyTransaction> txs = List.of(buy(D1, "100", "10"), buy(D3, "100", "10"));

    StrategyPerformance.Result result = StrategyPerformance.of(txs,
      prices(candle(D1, "100"), candle(D2, "110"), candle(D3, "100")), FIXED_FX, NO_DIVIDENDS, false, null,
      D1, D4, totalKrw("2400"), FX);

    assertThat(result.dailyValues()).extracting(DailyValue::valueUsd)
      .usingElementComparator(BigDecimal::compareTo)
      .containsExactly(new BigDecimal("1000"), new BigDecimal("1100"), new BigDecimal("2000"), new BigDecimal("2400"));
    // 가치는 내내 늘지만 1100 → (2000 - 1000 입금) 은 -9.09%
    assertThat(result.metricsUsd().mdd()).isEqualByComparingTo("-9.09");
    assertThat(result.metricsUsd().mddPeakDate()).isEqualTo(D2);
    assertThat(result.metricsUsd().mddTroughDate()).isEqualTo(D3);
    assertThat(result.metricsUsd().mddRecoveryDate()).isEqualTo(D4);
    assertThat(result.metricsKrw().mdd()).isEqualByComparingTo("-9.09");
    // 날마다 낙폭도 입금을 뺀 지수로 셈
    assertThat(result.dailyValues().get(2).drawdownUsd()).isEqualByComparingTo("-9.09");
    assertThat(result.dailyValues().getLast().drawdownKrw()).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("재투자를 끄면 배당락일부터 세후 배당이 현금으로 더해진다")
  void cashDividendFromExDate() {
    DividendHistoryDto dividends = DividendHistoryDto.of("AAPL",
      List.of(new DividendDto("AAPL", D2, D3, null, BigDecimal.ONE, "USD", "test")));

    StrategyPerformance.Result result = StrategyPerformance.of(List.of(buy(D1, "100", "10")),
      prices(candle(D1, "100"), candle(D2, "110")), FIXED_FX, dividends, false, new BigDecimal("0.15"),
      D1, D3, totalKrw("1108.50"), FX);

    // 10주 × 110 + 배당 10달러의 세후 8.5달러
    assertThat(result.dailyValues().get(1).valueUsd()).isEqualByComparingTo("1108.50");
  }

  @Test
  @DisplayName("배당 재투자 거래는 주식 수에만 들어가고 입금으로 보지 않는다")
  void reinvestmentIsNotDeposit() {
    StrategyTransaction reinvest = StrategyTransaction.builder().date(D2).actualDate(D2).price(new BigDecimal("100"))
      .shares(new BigDecimal("0.1")).amount(BigDecimal.ZERO).fxRate(FX)
      .trigger(BacktestConstants.TransactionTrigger.DIVIDEND_REINVESTMENT).build();

    StrategyPerformance.Result result = StrategyPerformance.of(List.of(buy(D1, "100", "10"), reinvest),
      prices(candle(D1, "100"), candle(D2, "100")), FIXED_FX, NO_DIVIDENDS, true, null,
      D1, D3, totalKrw("1010"), FX);

    // 10.1주 × 100, 입금이 없으니 하루 +1%
    assertThat(result.dailyValues().get(1).valueUsd()).isEqualByComparingTo("1010");
    assertThat(result.metricsUsd().mdd()).isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("일봉이 없는 날에 산 입금은 다음 일봉에서 뺀다")
  void depositOnMissingCandleDay() {
    // D3 일봉이 없고 가격은 내내 100 → 입금을 빼면 수익률 0
    List<StrategyTransaction> txs = List.of(buy(D1, "100", "10"), buy(D3, "100", "10"));

    StrategyPerformance.Result result = StrategyPerformance.of(txs,
      prices(candle(D1, "100"), candle(D2, "100"), candle(D4, "100")), FIXED_FX, NO_DIVIDENDS, false, null,
      D1, D5, totalKrw("2000"), FX);

    assertThat(result.dailyValues()).extracting(DailyValue::date).containsExactly(D1, D2, D4, D5);
    assertThat(result.metricsUsd().volatility()).isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("일봉이 없으면 평가일 한 점만 싣는다")
  void noCandles() {
    StrategyPerformance.Result result = StrategyPerformance.of(List.of(buy(D1, "100", "10")), Map.of(),
      FIXED_FX, NO_DIVIDENDS, false, null, D1, D2, totalKrw("1100"), FX);

    assertThat(result.dailyValues()).extracting(DailyValue::date).containsExactly(D2);
    // 중간 경로를 모르면 지표를 내지 않음
    assertThat(result.metricsUsd().mdd()).isNull();
  }

  @Test
  @DisplayName("원화 지수는 달러 지수에 그날 환율 / 첫날 환율을 곱해, 환율만 내린 날은 원화에만 낙폭이 난다")
  void krwFollowsFxPath() {
    // 주가는 내내 100, 환율 1300 → 1170(-10%) → 평가일 1300
    Function<LocalDate, BigDecimal> fx = date -> date.equals(D2) ? new BigDecimal("1170") : FX;

    StrategyPerformance.Result result = StrategyPerformance.of(List.of(buy(D1, "100", "10")),
      prices(candle(D1, "100"), candle(D2, "100")), fx, NO_DIVIDENDS, false, null, D1, D3, totalKrw("1000"), FX);

    assertThat(result.metricsUsd().mdd()).isEqualByComparingTo("0.00");
    assertThat(result.metricsKrw().mdd()).isEqualByComparingTo("-10.00");
    assertThat(result.dailyValues().get(1).valueKrw()).isEqualByComparingTo("1170000");
  }

  @Test
  @DisplayName("원화 지수의 기준은 첫 매수 환율이라 그날 경로 환율이 달라도 원화 연평균 수익률이 총 수익률과 같은 기준이다")
  void krwBaseIsFirstTradeFx() {
    // 기본 환율 1350 으로 10주를 사고 경로 · 평가일 환율은 1200, 주가는 내내 100
    LocalDate later = LocalDate.of(2025, 1, 2);
    StrategyTransaction defaulted = StrategyTransaction.builder().date(D1).actualDate(D1).price(new BigDecimal("100"))
      .shares(BigDecimal.TEN).amount(new BigDecimal("1350000")).fxRate(new BigDecimal("1350")).trigger("월정액")
      .fee(BigDecimal.ZERO).build();
    BigDecimal pathFx = new BigDecimal("1200");

    StrategyPerformance.Result result = StrategyPerformance.of(List.of(defaulted),
      prices(candle(D1, "100"), candle(D2, "100")), date -> pathFx, NO_DIVIDENDS, false, null,
      D1, later, new BigDecimal("1200000"), pathFx);

    // 366일에 1,200,000 / 1,350,000
    assertThat(result.metricsKrw().cagr()).isEqualByComparingTo("-11.09");
  }

  @Test
  @DisplayName("회당 금액이 적어도 지수는 반올림 전 가치 · 입금으로 세어 주가가 그대로면 낙폭이 없다")
  void smallDepositsUnrounded() {
    // 5,000원 = 3.846154달러씩 두 번, 화면 값은 3.85 · 7.69
    List<StrategyTransaction> txs = List.of(small(D1, "5000", "0.03846154"), small(D2, "5000", "0.03846154"));

    StrategyPerformance.Result result = StrategyPerformance.of(txs,
      prices(candle(D1, "100"), candle(D2, "100")), FIXED_FX, NO_DIVIDENDS, false, null,
      D1, D3, new BigDecimal("10000"), FX);

    assertThat(result.dailyValues().get(1).valueUsd()).isEqualByComparingTo("7.69");
    assertThat(result.metricsUsd().mdd()).isEqualByComparingTo("0.00");
    assertThat(result.metricsUsd().sharpe()).isNull();
  }

  @Test
  @DisplayName("회당 금액이 아주 적어 화면 값이 0 달러여도 지수는 반올림 전 값으로 센다")
  void tinyDeposit() {
    StrategyPerformance.Result result = StrategyPerformance.of(List.of(small(D1, "5", "0.00003846")),
      prices(candle(D1, "100"), candle(D2, "100")), FIXED_FX, NO_DIVIDENDS, false, null,
      D1, D3, new BigDecimal("5"), FX);

    assertThat(result.dailyValues().getFirst().valueKrw()).isEqualByComparingTo("0");
    assertThat(result.metricsUsd().mdd()).isEqualByComparingTo("0.00");
  }

  private static StrategyTransaction small(LocalDate date, String amountKrw, String shares) {
    return StrategyTransaction.builder().date(date).actualDate(date).price(new BigDecimal("100"))
      .shares(new BigDecimal(shares)).amount(new BigDecimal(amountKrw)).fxRate(FX).trigger("월정액")
      .fee(BigDecimal.ZERO).build();
  }
}
