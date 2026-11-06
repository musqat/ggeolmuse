package com.muscat.backtest.common.calculation;

import com.muscat.backtest.domain.dto.response.PerformanceMetrics;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// 일별 가치 지수로 연평균 수익률 · 최대 낙폭 · 변동성 · 샤프 비율을 내는 계산기
public final class PerformanceCalculator {

  // 샤프 비율의 무위험 수익률(연)
  public static final double RISK_FREE_RATE = 0.03;
  // 연환산 거래일 수
  public static final int TRADING_DAYS = 252;
  private static final double DAYS_PER_YEAR = 365.25;
  // 반올림하면 0.00% 인 크기(샤프 · 낙폭 날짜를 내지 않는 기준)
  private static final double ROUNDS_TO_ZERO = 0.00005;
  // 요청 시작 뒤 첫 거래일을 찾는 일수
  private static final int FIRST_TRADE_DAYS = 7;

  private PerformanceCalculator() {
  }

  // 일별 지수(원금 1 기준, 첫 점 매수일 · 마지막 점 평가일)의 수익 · 위험 지표
  public static PerformanceMetrics compute(List<LocalDate> dates, List<Double> index, LocalDate requestStart) {
    if (dates.size() != index.size()) {
      throw new IllegalArgumentException("날짜와 지수의 길이가 다름: " + dates.size() + " · " + index.size());
    }
    if (index.size() < 2) {
      return PerformanceMetrics.empty();
    }

    Drawdown drawdown = drawdown(dates, index);
    double[] returns = dailyReturns(index);
    Double volatility = volatility(returns);
    Double sharpe = volatility == null || volatility < ROUNDS_TO_ZERO
      ? null
      : (mean(returns) * TRADING_DAYS - RISK_FREE_RATE) / volatility;

    return new PerformanceMetrics(
      percent(cagr(dates, index, requestStart)),
      percent(drawdown.mdd()),
      drawdown.peak(),
      drawdown.trough(),
      drawdown.recovery(),
      percent(volatility),
      ratio(sharpe));
  }

  // 지수의 날마다 그때까지 고점 대비 낙폭(%)
  public static List<BigDecimal> drawdownPercents(List<Double> index) {
    List<BigDecimal> result = new ArrayList<>();
    double peak = Double.NEGATIVE_INFINITY;
    for (double value : index) {
      peak = Math.max(peak, value);
      result.add(percent(value / peak - 1));
    }
    return result;
  }

  // 원금 1 대비 마지막 지수의 연환산, 요청 시작(첫 점이 7일 넘게 늦으면 첫 점) ~ 평가일이 1년 이상일 때만
  private static Double cagr(List<LocalDate> dates, List<Double> index, LocalDate requestStart) {
    LocalDate first = dates.getFirst();
    LocalDate last = dates.getLast();
    LocalDate from = first.isAfter(requestStart.plusDays(FIRST_TRADE_DAYS)) ? first : requestStart;
    long days = ChronoUnit.DAYS.between(first, last);
    double growth = index.getLast();
    if (from.plusYears(1).isAfter(last.plusDays(1)) || days <= 0 || growth <= 0) {
      return null;
    }
    return Math.pow(growth, DAYS_PER_YEAR / days) - 1;
  }

  // 그때까지 고점 대비 가장 깊은 하락과 그 고점 · 저점 · 고점 가치로 돌아온 날
  private static Drawdown drawdown(List<LocalDate> dates, List<Double> index) {
    int peak = 0;
    int worstPeak = -1;
    int worstTrough = -1;
    double worst = 0;
    for (int i = 1; i < index.size(); i++) {
      if (index.get(i) >= index.get(peak)) {
        peak = i;
      }
      double fall = index.get(i) / index.get(peak) - 1;
      if (fall < worst) {
        worst = fall;
        worstPeak = peak;
        worstTrough = i;
      }
    }
    if (worstTrough < 0 || worst > -ROUNDS_TO_ZERO) {
      return new Drawdown(0.0, null, null, null);
    }
    LocalDate recovery = null;
    for (int i = worstTrough + 1; i < index.size(); i++) {
      if (index.get(i) >= index.get(worstPeak)) {
        recovery = dates.get(i);
        break;
      }
    }
    return new Drawdown(worst, dates.get(worstPeak), dates.get(worstTrough), recovery);
  }

  private static double[] dailyReturns(List<Double> index) {
    double[] returns = new double[index.size() - 1];
    for (int i = 1; i < index.size(); i++) {
      returns[i - 1] = index.get(i) / index.get(i - 1) - 1;
    }
    return returns;
  }

  // 표본 표준편차 × √252(수익률이 둘 이상일 때)
  private static Double volatility(double[] returns) {
    if (returns.length < 2) {
      return null;
    }
    double mean = mean(returns);
    double sum = 0;
    for (double r : returns) {
      sum += (r - mean) * (r - mean);
    }
    return Math.sqrt(sum / (returns.length - 1)) * Math.sqrt(TRADING_DAYS);
  }

  private static double mean(double[] values) {
    double sum = 0;
    for (double v : values) {
      sum += v;
    }
    return sum / values.length;
  }

  // 소수 둘째 자리 퍼센트(0 으로 나눈 값은 없음)
  private static BigDecimal percent(Double value) {
    return value == null || !Double.isFinite(value) ? null : BigDecimal.valueOf(value * 100).setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal ratio(Double value) {
    return value == null || !Double.isFinite(value) ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
  }

  private record Drawdown(double mdd, LocalDate peak, LocalDate trough, LocalDate recovery) {
  }
}
