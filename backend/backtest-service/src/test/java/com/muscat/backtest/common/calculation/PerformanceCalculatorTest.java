package com.muscat.backtest.common.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.backtest.domain.dto.response.PerformanceMetrics;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PerformanceCalculator 수익 · 위험 지표")
class PerformanceCalculatorTest {

  private static final LocalDate D0 = LocalDate.of(2024, 3, 4);

  private static List<LocalDate> days(int n) {
    List<LocalDate> dates = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      dates.add(D0.plusDays(i));
    }
    return dates;
  }

  // 일별 수익률을 차례로 곱한 지수(첫 값 1)
  private static List<Double> indexOf(double... returns) {
    List<Double> index = new ArrayList<>(List.of(1.0));
    for (double r : returns) {
      index.add(index.getLast() * (1 + r));
    }
    return index;
  }

  // 요청 시작일이 첫 점과 같은 계산
  private static PerformanceMetrics compute(List<LocalDate> dates, List<Double> index) {
    return PerformanceCalculator.compute(dates, index, dates.getFirst());
  }

  @Test
  @DisplayName("점이 둘보다 적으면 지표가 모두 비어 있다")
  void tooFewPoints() {
    PerformanceMetrics metrics = compute(days(1), List.of(1.0));

    assertThat(metrics).isEqualTo(PerformanceMetrics.empty());
  }

  @Test
  @DisplayName("날짜와 지수의 길이가 다르면 받지 않는다")
  void sizeMismatch() {
    assertThatThrownBy(() -> compute(days(3), List.of(1.0, 1.1)))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("5년에 두 배면 연평균 수익률은 14.86%")
  void cagrOverFiveYears() {
    List<LocalDate> dates = List.of(LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1));

    PerformanceMetrics metrics = compute(dates, List.of(1.0, 2.0));

    // 1,827일 = 5.002년, 2^(1/5.002) - 1
    assertThat(metrics.cagr()).isEqualByComparingTo("14.86");
  }

  @Test
  @DisplayName("1년을 요청하면 첫 거래일 ~ 평가일이 1년이 안 돼도 연평균 수익률을 낸다")
  void cagrForOneYearRequest() {
    // 2023-01-01 ~ 12-31 요청, 첫 거래일 1/3
    List<LocalDate> dates = List.of(LocalDate.of(2023, 1, 3), LocalDate.of(2023, 12, 31));

    PerformanceMetrics metrics = PerformanceCalculator.compute(dates, List.of(1.0, 1.1), LocalDate.of(2023, 1, 1));

    // 362일, 1.1^(365.25/362) - 1
    assertThat(metrics.cagr()).isEqualByComparingTo("10.09");
  }

  @Test
  @DisplayName("첫 점이 요청 시작보다 7일 넘게 늦으면 1년을 첫 점부터 잰다")
  void cagrFromLateFirstPoint() {
    // 3년 요청 중 마지막 반년만 보유
    List<LocalDate> dates = List.of(LocalDate.of(2022, 7, 1), LocalDate.of(2022, 12, 31));

    PerformanceMetrics metrics = PerformanceCalculator.compute(dates, List.of(1.0, 1.1), LocalDate.of(2020, 1, 1));

    assertThat(metrics.cagr()).isNull();
  }

  @Test
  @DisplayName("첫 점이 원금보다 낮아도 낙폭으로 세지 않고, 연평균 수익률은 원금 1 을 기준으로 낸다")
  void firstPointBelowCapital() {
    // 매수일 평가가 원금의 99.75%
    List<LocalDate> dates = List.of(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 1));

    PerformanceMetrics metrics = compute(dates, List.of(0.9975, 1.1, 1.21));

    assertThat(metrics.mdd()).isEqualByComparingTo("0.00");
    assertThat(metrics.mddPeakDate()).isNull();
    // 731일에 1.21 배
    assertThat(metrics.cagr()).isEqualByComparingTo("9.99");
  }

  @Test
  @DisplayName("반올림하면 0.00% 인 변동성이면 샤프를 내지 않는다")
  void roundedZeroVolatility() {
    PerformanceMetrics metrics = compute(days(4), List.of(1.0, 0.999998, 0.999998, 0.999998));

    assertThat(metrics.volatility()).isEqualByComparingTo("0.00");
    assertThat(metrics.sharpe()).isNull();
  }

  @Test
  @DisplayName("원금을 모두 잃으면 연평균 수익률을 내지 않고 최대 낙폭은 -100%")
  void totalLoss() {
    List<LocalDate> dates = List.of(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 6, 1));

    PerformanceMetrics metrics = compute(dates, List.of(1.0, 0.0));

    assertThat(metrics.cagr()).isNull();
    assertThat(metrics.mdd()).isEqualByComparingTo("-100.00");
  }

  @Test
  @DisplayName("원금을 모두 잃은 뒤 날이 이어지면 변동성 · 샤프를 내지 않는다")
  void totalLossThenFlat() {
    PerformanceMetrics metrics = compute(days(3), List.of(1.0, 0.0, 0.0));

    assertThat(metrics.mdd()).isEqualByComparingTo("-100.00");
    assertThat(metrics.volatility()).isNull();
    assertThat(metrics.sharpe()).isNull();
  }

  @Test
  @DisplayName("고점 가치로 돌아온 뒤 더 깊이 내려가면 돌아온 날이 고점이다")
  void peakAfterReturningToHigh() {
    PerformanceMetrics metrics = compute(days(5), List.of(1.0, 1.5, 0.9, 1.5, 0.8));

    assertThat(metrics.mdd()).isEqualByComparingTo("-46.67");
    assertThat(metrics.mddPeakDate()).isEqualTo(D0.plusDays(3));
    assertThat(metrics.mddTroughDate()).isEqualTo(D0.plusDays(4));
    assertThat(metrics.mddRecoveryDate()).isNull();
  }

  @Test
  @DisplayName("반올림하면 0.00% 인 낙폭이면 고점 · 저점 날짜가 없다")
  void roundedZeroDrawdown() {
    PerformanceMetrics metrics = compute(days(3), List.of(1.0, 1.00001, 1.0));

    assertThat(metrics.mdd()).isEqualByComparingTo("0.00");
    assertThat(metrics.mddPeakDate()).isNull();
    assertThat(metrics.mddTroughDate()).isNull();
  }

  @Test
  @DisplayName("1년이 안 되는 기간은 연평균 수익률을 내지 않는다")
  void cagrUnderOneYear() {
    PerformanceMetrics metrics = compute(days(4), indexOf(0.01, 0.02, -0.01));

    assertThat(metrics.cagr()).isNull();
    assertThat(metrics.volatility()).isNotNull();
  }

  @Test
  @DisplayName("고점 1.5 에서 0.9 로 내려가면 최대 낙폭 -40%, 2.0 에서 회복")
  void maxDrawdownRecovered() {
    PerformanceMetrics metrics = compute(days(4), List.of(1.0, 1.5, 0.9, 2.0));

    assertThat(metrics.mdd()).isEqualByComparingTo("-40.00");
    assertThat(metrics.mddPeakDate()).isEqualTo(D0.plusDays(1));
    assertThat(metrics.mddTroughDate()).isEqualTo(D0.plusDays(2));
    assertThat(metrics.mddRecoveryDate()).isEqualTo(D0.plusDays(3));
  }

  @Test
  @DisplayName("고점 가치로 돌아오지 못하면 회복일이 비어 있다")
  void maxDrawdownNotRecovered() {
    PerformanceMetrics metrics = compute(days(4), List.of(1.0, 1.2, 0.6, 1.0));

    assertThat(metrics.mdd()).isEqualByComparingTo("-50.00");
    assertThat(metrics.mddPeakDate()).isEqualTo(D0.plusDays(1));
    assertThat(metrics.mddTroughDate()).isEqualTo(D0.plusDays(2));
    assertThat(metrics.mddRecoveryDate()).isNull();
  }

  @Test
  @DisplayName("내내 오르면 최대 낙폭 0 이고 고점 · 저점 · 회복일이 없다")
  void noDrawdown() {
    PerformanceMetrics metrics = compute(days(3), List.of(1.0, 1.1, 1.2));

    assertThat(metrics.mdd()).isEqualByComparingTo("0.00");
    assertThat(metrics.mddPeakDate()).isNull();
    assertThat(metrics.mddTroughDate()).isNull();
    assertThat(metrics.mddRecoveryDate()).isNull();
  }

  @Test
  @DisplayName("하루 +1% · -1% 를 번갈아 내면 연 변동성 18.33%, 샤프 -0.16")
  void volatilityAndSharpe() {
    PerformanceMetrics metrics = compute(days(5), indexOf(0.01, -0.01, 0.01, -0.01));

    // 표본 표준편차 0.011547 × √252 = 0.1833, 평균 0 이라 (0 - 3%) / 18.33%
    assertThat(metrics.volatility()).isEqualByComparingTo("18.33");
    assertThat(metrics.sharpe()).isEqualByComparingTo("-0.16");
  }

  @Test
  @DisplayName("날마다 같은 수익률이면 변동성 0 이고 샤프는 내지 않는다")
  void zeroVolatility() {
    PerformanceMetrics metrics = compute(days(4), indexOf(0.001, 0.001, 0.001));

    assertThat(metrics.volatility()).isEqualByComparingTo("0.00");
    assertThat(metrics.sharpe()).isNull();
  }

  @Test
  @DisplayName("날마다 그때까지 고점 대비 낙폭을 퍼센트로 낸다")
  void drawdownPercents() {
    List<java.math.BigDecimal> drawdowns = PerformanceCalculator.drawdownPercents(List.of(1.0, 1.5, 0.9, 2.0));

    assertThat(drawdowns).usingElementComparator(java.math.BigDecimal::compareTo)
      .containsExactly(new java.math.BigDecimal("0"), new java.math.BigDecimal("0"),
        new java.math.BigDecimal("-40"), new java.math.BigDecimal("0"));
  }

  @Test
  @DisplayName("수익률이 하나뿐이면 변동성 · 샤프를 내지 않는다")
  void singleReturn() {
    PerformanceMetrics metrics = compute(days(2), List.of(1.0, 0.9));

    assertThat(metrics.mdd()).isEqualByComparingTo("-10.00");
    assertThat(metrics.volatility()).isNull();
    assertThat(metrics.sharpe()).isNull();
  }
}
