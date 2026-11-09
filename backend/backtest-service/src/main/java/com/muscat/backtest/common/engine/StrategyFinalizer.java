package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.calculation.CapitalGainsTaxCalculator;
import com.muscat.backtest.common.calculation.StrategyCalculationResult;
import com.muscat.backtest.common.calculation.StrategyCalculator;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.common.util.FxPath;
import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.domain.dto.response.CapitalGainsTax;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// 전략(DCA · 조건부) 공통 마무리: 평가일 시세 · 환율 → 총투자 · 환율 합 → 배당 재투자 → 기간 끝 매도 → 계산
public final class StrategyFinalizer {

  /** 마무리 결과(거래내역 + 계산결과 + 평가일 시세 + 일별 가치 · 지표) */
  public record Result(List<StrategyTransaction> transactions,
                       StrategyCalculationResult calculation,
                       StockPriceDto currentPrice,
                       StrategyPerformance.Result performance) {

    // 응답에 일별 평가 가치 · 통화별 지표를 채움
    public StrategyResponse fill(StrategyResponse response) {
      response.setDailyValues(performance.dailyValues());
      response.setMetricsKrw(performance.metricsKrw());
      response.setMetricsUsd(performance.metricsUsd());
      return response;
    }

    // 판 거래의 연도별 예상 양도세와 총 자산 · 투자금 기준 세후 평가
    public CapitalGainsTax capitalGainsTax() {
      return CapitalGainsTaxCalculator.of(CapitalGainsTaxCalculator.realizedGains(transactions),
        calculation.getTotalAssetKrw(), calculation.getTotalInvested());
    }
  }

  private StrategyFinalizer() {}

  public static Result run(
      MarketDataClientWrapper marketData,
      String symbol,
      LocalDate requestStart,
      LocalDate valuationDate,
      BigDecimal manualCurrentFxRate,
      BigDecimal manualPurchaseFxRate,
      boolean reinvestEnabled,
      BigDecimal dividendTaxRate,
      List<StrategyTransaction> transactions,
      Map<LocalDate, OHLCPriceDto> priceMap,
      FxRates dailyFx,
      PeriodEnd periodEnd,
      BigDecimal tradingFeeRate) {

    StockPriceDto currentPrice = BacktestDataUtils.getPriceAt(
        marketData, symbol, valuationDate);

    FxRateDto currentFxRate = manualCurrentFxRate != null
        ? new FxRateDto(valuationDate, manualCurrentFxRate)
        : BacktestDataUtils.getFxRateAt(marketData, valuationDate);
    if (transactions.isEmpty()) {
      return empty(transactions, currentPrice, currentFxRate);
    }

    LocalDate firstPurchaseDate = transactions.getFirst().getDate();
    // 기간 끝에 팔면 판 날까지의 배당
    LocalDate dividendsUntil = PeriodEnd.sells(periodEnd) ? PeriodEndSale.saleDate(priceMap, valuationDate) : valuationDate;
    DividendHistoryDto dividendHistory = BacktestDataUtils.getDividendHistory(
        marketData, symbol, firstPurchaseDate, dividendsUntil);

    BigDecimal totalInvested = transactions.stream()
        .map(StrategyTransaction::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    // 가중평균 환율 합계 = Σ(투자금액 × 환율)
    BigDecimal totalFxRateSum = transactions.stream()
        .map(t -> t.getFxRate().multiply(t.getAmount()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal dividendsReinvested = DividendReinvestor.reinvest(
        marketData, dividendHistory, transactions, symbol,
        firstPurchaseDate, reinvestEnabled, dividendTaxRate, manualPurchaseFxRate, currentFxRate.rate());

    if (PeriodEnd.sells(periodEnd)) {
      sellAtEnd(transactions, priceMap, valuationDate, currentPrice, currentFxRate.rate(), tradingFeeRate);
    }

    // 배당 재투자 · 기간 끝 매도 후 총 보유 주식수
    BigDecimal totalShares = transactions.stream()
        .map(StrategyTransaction::getShares)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalDividends = BacktestCalculationUtils.calculateTotalDividends(
        dividendHistory, transactions, firstPurchaseDate, valuationDate);

    StrategyCalculationResult calculation = StrategyCalculator.calculate(
        transactions, totalInvested, totalShares, totalFxRateSum, currentPrice, currentFxRate,
        totalDividends, dividendsReinvested, dividendTaxRate);

    Function<LocalDate, BigDecimal> fxOn = fxPath(transactions, valuationDate, manualPurchaseFxRate,
        manualCurrentFxRate, currentFxRate.rate(), dailyFx);
    StrategyPerformance.Result performance = StrategyPerformance.of(transactions, priceMap, fxOn,
        dividendHistory, Decimals.isPositive(dividendsReinvested), dividendTaxRate, requestStart, valuationDate,
        calculation.getTotalAssetKrw(), currentFxRate.rate());

    return new Result(transactions, calculation, currentPrice, performance);
  }

  // 남은 주식 전량의 기간 끝 매도를 거래에 더함(환율은 평가일 환율, 실현 손익은 보유분 원가 대비, 남은 주식이 없으면 그대로)
  private static void sellAtEnd(List<StrategyTransaction> transactions, Map<LocalDate, OHLCPriceDto> priceMap,
      LocalDate valuationDate, StockPriceDto currentPrice, BigDecimal fxRate, BigDecimal feeRate) {
    BigDecimal shares = transactions.stream()
        .map(StrategyTransaction::getShares)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    StrategyTransaction sale = PeriodEndSale.of(shares, priceMap, valuationDate, currentPrice.currentPrice(), feeRate,
        fxRate);
    if (sale != null) {
      sale.setRealizedProfit(sale.getProceeds().subtract(StrategyCalculator.holdingCost(transactions)));
      transactions.add(sale);
    }
  }

  // 매수 0회 결과(투자 · 평가 · 수익 0, 일별 가치 · 지표 없음)
  private static Result empty(List<StrategyTransaction> transactions, StockPriceDto currentPrice,
      FxRateDto currentFxRate) {
    StrategyCalculationResult calculation = StrategyCalculationResult.builder()
        .totalInvested(BigDecimal.ZERO)
        .totalShares(BigDecimal.ZERO)
        .currentValue(BigDecimal.ZERO)
        .currentValueKrw(BigDecimal.ZERO)
        .totalDividends(BigDecimal.ZERO)
        .dividendsReinvested(BigDecimal.ZERO)
        .totalAssetKrw(BigDecimal.ZERO)
        .cashBalance(BigDecimal.ZERO)
        .realizedProfit(BigDecimal.ZERO)
        .totalReturnPercent(BigDecimal.ZERO)
        .totalReturnKrw(BigDecimal.ZERO)
        .fxReturn(BigDecimal.ZERO)
        .fxReturnPercent(BigDecimal.ZERO)
        .currentFxRate(currentFxRate.rate())
        .defaultFxValuationDate(currentFxRate.defaulted() ? currentFxRate.date() : null)
        .build();
    return new Result(transactions, calculation, currentPrice,
        new StrategyPerformance.Result(List.of(), null, null));
  }

  // 날마다 원화 환율: 수동 환율이거나 시장 기록이 없으면 첫 매수 · 평가일 환율을 날짜 비례로, 아니면 기본 환율 날을 뺀 시장 환율
  static Function<LocalDate, BigDecimal> fxPath(List<StrategyTransaction> transactions,
      LocalDate valuationDate, BigDecimal manualPurchaseFxRate, BigDecimal manualCurrentFxRate,
      BigDecimal currentFxRate, FxRates dailyFx) {
    Map<LocalDate, BigDecimal> records = new HashMap<>();
    dailyFx.rates().forEach((date, rate) -> {
      if (!dailyFx.isDefault(date)) {
        records.put(date, rate);
      }
    });
    if (manualPurchaseFxRate != null || manualCurrentFxRate != null || records.isEmpty()) {
      return transactions.stream()
          .filter(tx -> Decimals.isPositive(tx.getAmount()))
          .min(Comparator.comparing(StrategyTransaction::tradedOn))
          .map(first -> FxPath.interpolated(first.tradedOn(), first.getFxRate(), valuationDate, currentFxRate))
          .orElse(date -> currentFxRate);
    }
    return FxPath.market(records, currentFxRate);
  }
}
