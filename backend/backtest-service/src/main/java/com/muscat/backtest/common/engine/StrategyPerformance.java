package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.calculation.PerformanceCalculator;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.domain.dto.response.DailyValue;
import com.muscat.backtest.domain.dto.response.PerformanceMetrics;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// 전략 거래 내역으로 일별 평가 가치와 입금을 뺀 시간가중 수익 · 위험 지표를 내는 계산기
public final class StrategyPerformance {

  /** 일별 평가 가치(마지막은 평가일 총자산)와 통화별 지표 */
  public record Result(List<DailyValue> dailyValues, PerformanceMetrics metricsKrw,
                       PerformanceMetrics metricsUsd) {}

  // 하루 평가 가치(화면 값 · 반올림 전 달러)와 앞 점 다음 날부터 그 날짜까지의 입금(달러) · 그 날짜 환율
  private record Point(DailyValue value, BigDecimal rawUsd, BigDecimal depositUsd, BigDecimal fxRate) {}

  private StrategyPerformance() {}

  public static Result of(
      List<StrategyTransaction> transactions,
      Map<LocalDate, OHLCPriceDto> priceMap,
      Function<LocalDate, BigDecimal> fxOn,
      DividendHistoryDto dividendHistory,
      boolean reinvested,
      BigDecimal dividendTaxRate,
      LocalDate requestStart,
      LocalDate valuationDate,
      BigDecimal totalAssetKrw,
      BigDecimal currentFxRate) {

    LocalDate start = transactions.stream().map(StrategyTransaction::tradedOn)
        .min(Comparator.naturalOrder()).orElse(valuationDate);
    List<CashDividend> cashDividends = reinvested
        ? List.of()
        : cashDividends(transactions, dividendHistory, dividendTaxRate, valuationDate);

    List<Point> points = new ArrayList<>();
    LocalDate previous = null;
    for (OHLCPriceDto candle : priceMap.values().stream()
        .filter(c -> !c.date().isBefore(start) && c.date().isBefore(valuationDate))
        .filter(PriceLookup::isUsable)
        .sorted(Comparator.comparing(OHLCPriceDto::date))
        .toList()) {
      LocalDate date = candle.date();
      BigDecimal rawUsd = PriceLookup.effectiveClose(candle)
          .multiply(sharesOn(transactions, date)).add(cashOn(cashDividends, date)).add(tradeCashOn(transactions, date));
      BigDecimal valueUsd = MoneyUtils.roundUsd(rawUsd);
      BigDecimal fxRate = fxOn.apply(date);
      points.add(new Point(new DailyValue(date, valueUsd, DailyValue.toKrw(valueUsd, fxRate)), rawUsd,
          depositUsd(transactions, previous, date), fxRate));
      previous = date;
    }
    BigDecimal lastRawUsd = totalAssetKrw.divide(currentFxRate, MathContext.DECIMAL64);
    DailyValue last = new DailyValue(valuationDate, lastRawUsd.setScale(2, RoundingMode.HALF_UP), totalAssetKrw);
    points.add(new Point(last, lastRawUsd, depositUsd(transactions, previous, valuationDate), currentFxRate));

    List<LocalDate> dates = points.stream().map(point -> point.value().date()).toList();
    List<Double> usdIndex = timeWeighted(points);
    // 원화 지수의 기준은 첫 매수 환율
    double firstFx = transactions.stream()
        .filter(tx -> Decimals.isPositive(tx.getAmount()))
        .min(Comparator.comparing(StrategyTransaction::tradedOn))
        .map(StrategyTransaction::getFxRate)
        .orElse(points.getFirst().fxRate())
        .doubleValue();
    List<Double> krwIndex = new ArrayList<>();
    for (int i = 0; i < points.size(); i++) {
      krwIndex.add(usdIndex.get(i) * points.get(i).fxRate().doubleValue() / firstFx);
    }

    return new Result(
        DailyValue.withDrawdowns(points.stream().map(Point::value).toList(),
            PerformanceCalculator.drawdownPercents(usdIndex), PerformanceCalculator.drawdownPercents(krwIndex)),
        PerformanceCalculator.compute(dates, krwIndex, requestStart),
        PerformanceCalculator.compute(dates, usdIndex, requestStart));
  }

  // 날마다 (반올림 전 가치 - 앞 점 뒤 입금) / 앞 점 가치 를 곱한 달러 지수(입금 원금 1 기준, 보유 전 날은 1)
  private static List<Double> timeWeighted(List<Point> points) {
    List<Double> index = new ArrayList<>();
    double level = 1;
    double before = 0;
    for (Point point : points) {
      double today = point.rawUsd().doubleValue();
      double in = point.depositUsd().doubleValue();
      if (before > 0) {
        level *= (today - in) / before;
      } else if (in > 0) {
        level *= today / in;
      }
      index.add(level);
      before = today;
    }
    return index;
  }

  // 그 날짜 종가 기준 보유 주식 수(그 날짜에 산 것 포함)
  private static BigDecimal sharesOn(List<StrategyTransaction> transactions, LocalDate date) {
    return transactions.stream()
        .filter(tx -> !tx.tradedOn().isAfter(date))
        .map(StrategyTransaction::getShares)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  // 그 날짜까지 매도로 받고 매수에 쓰고 남은 현금(USD)
  private static BigDecimal tradeCashOn(List<StrategyTransaction> transactions, LocalDate date) {
    return transactions.stream()
        .filter(tx -> !tx.tradedOn().isAfter(date))
        .map(StrategyTransaction::cashChange)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  // (from, to] 에 들어온 입금을 산 날 환율로 바꾼 반올림 전 달러
  private static BigDecimal depositUsd(List<StrategyTransaction> transactions, LocalDate from, LocalDate to) {
    return depositsBetween(transactions, from, to).stream()
        .map(tx -> tx.getAmount().divide(tx.getFxRate(), MathContext.DECIMAL64))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static List<StrategyTransaction> depositsBetween(List<StrategyTransaction> transactions,
      LocalDate from, LocalDate to) {
    return transactions.stream()
        .filter(tx -> Decimals.isPositive(tx.getAmount()))
        .filter(tx -> (from == null || tx.tradedOn().isAfter(from)) && !tx.tradedOn().isAfter(to))
        .toList();
  }

  // 배당락일과 세후 현금 배당(USD)
  private record CashDividend(LocalDate exDate, BigDecimal amountUsd) {}

  // 배당락일마다 그때 보유 주식 수로 받은 세후 배당
  private static List<CashDividend> cashDividends(List<StrategyTransaction> transactions,
      DividendHistoryDto dividendHistory, BigDecimal taxRate, LocalDate valuationDate) {
    if (dividendHistory == null || dividendHistory.getDividends() == null || transactions.isEmpty()) {
      return List.of();
    }
    LocalDate firstPlanned = transactions.getFirst().getDate();
    List<CashDividend> result = new ArrayList<>();
    for (DividendHistoryDto.DividendPayment dividend : dividendHistory.getDividends()) {
      LocalDate exDate = dividend.getExDate();
      if (exDate == null || dividend.getAmount() == null
          || exDate.isBefore(firstPlanned) || exDate.isAfter(valuationDate)) {
        continue;
      }
      BigDecimal shares = transactions.stream()
          .filter(tx -> BacktestCalculationUtils.receivesDividend(tx.tradedOn(), exDate))
          .map(StrategyTransaction::getShares)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      BigDecimal gross = dividend.getAmount().multiply(shares);
      BigDecimal afterTax = taxRate != null && Decimals.isPositive(taxRate)
          ? gross.subtract(gross.multiply(taxRate))
          : gross;
      result.add(new CashDividend(exDate, afterTax));
    }
    return result;
  }

  private static BigDecimal cashOn(List<CashDividend> cashDividends, LocalDate date) {
    return cashDividends.stream()
        .filter(d -> !d.exDate().isAfter(date))
        .map(CashDividend::amountUsd)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
