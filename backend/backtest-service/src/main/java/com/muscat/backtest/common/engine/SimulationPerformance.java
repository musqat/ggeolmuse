package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.calculation.PerformanceCalculator;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.domain.dto.response.DailyValue;
import com.muscat.backtest.domain.model.HoldingStep;
import com.muscat.backtest.domain.model.SimulationCalculationResult;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// 단일 매수 일별 가치와 통화별 지표: 원금을 1 로 둔 원화 · 달러 지수로 지표 · 낙폭을 냄
public final class SimulationPerformance {

  private SimulationPerformance() {}

  // 평가일 전 일봉마다 가치와 평가일 총자산, 원화 원금은 투자금 · 달러 원금은 투자금을 매수 환율로 바꾼 값
  public static StrategyPerformance.Result of(List<OHLCPriceDto> candles, Function<LocalDate, BigDecimal> fx,
    SimulationCalculationResult calculation, LocalDate valuationDate, LocalDate requestStart) {
    List<DailyValue> dailyValues = dailyValues(candles, fx, calculation, valuationDate);
    BigDecimal krwCapital = calculation.investmentKrw();
    BigDecimal usdCapital = MoneyUtils.convertKrwToUsd(krwCapital, calculation.purchaseFxRate());
    List<Double> usdIndex = index(dailyValues, usdCapital, DailyValue::valueUsd);
    List<Double> krwIndex = index(dailyValues, krwCapital, DailyValue::valueKrw);
    List<LocalDate> dates = dailyValues.stream().map(DailyValue::date).toList();
    return new StrategyPerformance.Result(
      DailyValue.withDrawdowns(dailyValues, PerformanceCalculator.drawdownPercents(usdIndex),
        PerformanceCalculator.drawdownPercents(krwIndex)),
      PerformanceCalculator.compute(dates, krwIndex, requestStart),
      PerformanceCalculator.compute(dates, usdIndex, requestStart));
  }

  // 일봉마다 보유 주식 × 종가 + 현금(원화는 일봉 날짜 경로 환율), 평가일은 결과의 총자산
  private static List<DailyValue> dailyValues(List<OHLCPriceDto> candles, Function<LocalDate, BigDecimal> fx,
    SimulationCalculationResult calculation, LocalDate valuationDate) {
    List<HoldingStep> steps = calculation.holdingSteps();
    List<DailyValue> values = new ArrayList<>();
    int step = 0;
    for (OHLCPriceDto candle : candles) {
      if (!candle.date().isBefore(valuationDate)) {
        break;
      }
      if (!PriceLookup.isUsable(candle)) {
        continue;
      }
      while (step + 1 < steps.size() && !steps.get(step + 1).from().isAfter(candle.date())) {
        step++;
      }
      HoldingStep holding = steps.get(step);
      BigDecimal valueUsd = MoneyUtils.roundUsd(
        PriceLookup.effectiveClose(candle).multiply(holding.shares()).add(holding.cashUsd()));
      values.add(new DailyValue(candle.date(), valueUsd, DailyValue.toKrw(valueUsd, fx.apply(candle.date()))));
    }
    values.add(new DailyValue(valuationDate,
      calculation.totalAssetKrw().divide(calculation.currentFxRate(), 2, RoundingMode.HALF_UP),
      calculation.totalAssetKrw()));
    return values;
  }

  // 원금을 1 로 둔 일별 지수
  private static List<Double> index(List<DailyValue> values, BigDecimal capital,
    Function<DailyValue, BigDecimal> value) {
    List<Double> index = new ArrayList<>();
    double base = capital.doubleValue();
    for (DailyValue daily : values) {
      index.add(value.apply(daily).doubleValue() / base);
    }
    return index;
  }
}
