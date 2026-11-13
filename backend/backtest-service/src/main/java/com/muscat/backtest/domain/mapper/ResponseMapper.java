package com.muscat.backtest.domain.mapper;

import com.muscat.backtest.common.calculation.ComparisonCalculationResult;
import com.muscat.backtest.common.calculation.StrategyCalculationResult;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.domain.dto.request.BaseComparisonRequest;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.SignalStrategyRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.DailyValue;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.model.ComparisonItem;
import com.muscat.backtest.domain.model.SkippedRound;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.commonlib.dto.StockPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ResponseMapper {

  public StrategyResponse toStrategyResponse(
    DcaStrategyRequest request,
    List<StrategyTransaction> transactions,
    StrategyCalculationResult calculation,
    StockPriceDto currentPrice,
    List<SkippedRound> skippedRounds) {

    return buildStrategyResponse(StrategyType.DCA, request.getSymbol(), request.getStartDate(),
      request.getEndDate(), transactions, calculation, currentPrice, skippedRounds);
  }

  public StrategyResponse toStrategyResponse(
    ConditionalStrategyRequest request,
    List<StrategyTransaction> transactions,
    StrategyCalculationResult calculation,
    StockPriceDto currentPrice,
    List<SkippedRound> skippedRounds) {

    return buildStrategyResponse(StrategyType.CONDITIONAL_PURCHASE, request.getSymbol(),
      request.getStartDate(), request.getEndDate(), transactions, calculation, currentPrice, skippedRounds);
  }

  public StrategyResponse toStrategyResponse(
    SignalStrategyRequest request,
    List<StrategyTransaction> transactions,
    StrategyCalculationResult calculation,
    StockPriceDto currentPrice,
    List<SkippedRound> skippedRounds) {

    return buildStrategyResponse(request.getStrategyType(), request.getSymbol(), request.getStartDate(),
      request.getEndDate(), transactions, calculation, currentPrice, skippedRounds);
  }

  public ComparisonResponse toComparisonResponse(
    BaseComparisonRequest request,
    List<ComparisonItem> items,
    ComparisonCalculationResult calculation) {

    return ComparisonResponse.builder()
      .comparisonType(request.getComparisonType())
      .startDate(request.getStartDate())
      .endDate(request.getEndDate())
      .investmentAmount(request.getInvestmentAmount())
      .items(items)
      .bestPerformer(withoutDetail(calculation.getBestPerformer()))
      .worstPerformer(withoutDetail(calculation.getWorstPerformer()))
      .averageReturn(calculation.getAverageReturn())
      .medianReturn(calculation.getMedianReturn())
      .build();
  }

  public SimulationResponse toSimulationResponse(
    SimulationRequest request,
    BigDecimal purchasePriceUsd,
    BigDecimal shares,
    BigDecimal currentPriceUsd,
    BigDecimal currentValueUsd,
    BigDecimal currentValueKrw,
    BigDecimal stockReturn,
    BigDecimal stockReturnPercent,
    BigDecimal purchaseFxRate,
    BigDecimal currentFxRate,
    BigDecimal fxReturn,
    BigDecimal fxReturnPercent,
    BigDecimal totalDividends,
    BigDecimal dividendYield,
    BigDecimal tradingFee,
    BigDecimal remainingCash,
    BigDecimal totalAssetKrw,
    BigDecimal totalReturnKrw,
    BigDecimal totalReturnPercent,
    LocalDate optimalBuyDate,
    BigDecimal optimalBuyPrice,
    LocalDate optimalSellDate,
    BigDecimal optimalSellPrice,
    BigDecimal optimalReturnPercent,
    BigDecimal dividendsReinvested,
    List<LocalDate> dividendReinvestDates) {

    return SimulationResponse.builder()
      .symbol(request.getSymbol())
      .purchaseDate(request.getPurchaseDate())
      .currentDate(request.valuationDate(LocalDate.now()))
      .investmentAmount(request.getInvestmentAmount())
      .purchasePrice(purchasePriceUsd)
      .shares(shares.setScale(6, MoneyUtils.ROUND_MODE))
      .currentPrice(currentPriceUsd)
      .currentValue(MoneyUtils.roundUsd(currentValueUsd))
      .stockReturn(MoneyUtils.roundUsd(stockReturn))
      .stockReturnPercent(MoneyUtils.roundUsd(stockReturnPercent))
      .purchaseFxRate(purchaseFxRate)
      .currentFxRate(currentFxRate)
      .fxReturn(MoneyUtils.roundUsd(fxReturn))
      .fxReturnPercent(MoneyUtils.roundUsd(fxReturnPercent))
      .totalDividends(MoneyUtils.roundUsd(totalDividends))
      .dividendYield(MoneyUtils.roundUsd(dividendYield))
      .tradingFee(MoneyUtils.roundUsd(tradingFee))
      .remainingCash(MoneyUtils.roundUsd(remainingCash))
      .totalReturnPercent(MoneyUtils.roundUsd(totalReturnPercent))
      .currentValueKrw(MoneyUtils.roundKrw(currentValueKrw))
      .remainingCashKrw(MoneyUtils.roundKrw(
        Decimals.isPositive(remainingCash)
          ? MoneyUtils.convertUsdToKrw(remainingCash, currentFxRate)
          : BigDecimal.ZERO))
      .totalAssetKrw(MoneyUtils.roundKrw(totalAssetKrw))
      .totalReturnKrw(MoneyUtils.roundKrw(totalReturnKrw))
      .optimalBuyDate(optimalBuyDate)
      .optimalBuyPrice(optimalBuyPrice != null ? MoneyUtils.roundUsd(optimalBuyPrice) : null)
      .optimalSellDate(optimalSellDate)
      .optimalSellPrice(optimalSellPrice != null ? MoneyUtils.roundUsd(optimalSellPrice) : null)
      .optimalReturnPercent(
        optimalReturnPercent != null ? MoneyUtils.roundUsd(optimalReturnPercent) : null)
      .dividendsReinvested(
        dividendsReinvested != null ? MoneyUtils.roundUsd(dividendsReinvested) : null)
      .dividendReinvestDates(dividendReinvestDates)
      .build();
  }

  private StrategyResponse buildStrategyResponse(
    StrategyType strategyType,
    String symbol,
    LocalDate startDate,
    LocalDate endDate,
    List<StrategyTransaction> transactions,
    StrategyCalculationResult calculation,
    StockPriceDto currentPrice,
    List<SkippedRound> skippedRounds) {

    return StrategyResponse.builder()
      .symbol(symbol)
      .startDate(startDate)
      .endDate(endDate)
      .strategyType(strategyType)
      .transactions(transactions)
      .totalTransactions((int) transactions.stream().filter(tx -> !tx.isTaxPayment() && !tx.isYearEndHarvest()).count())
      .totalInvested(MoneyUtils.roundKrw(calculation.getTotalInvested()))
      .totalShares(calculation.getTotalShares().setScale(6, MoneyUtils.ROUND_MODE))
      .averagePrice(calculation.getAveragePrice() != null ? MoneyUtils.roundUsd(calculation.getAveragePrice()) : null)
      .currentPrice(currentPrice.currentPrice())
      .currentValue(MoneyUtils.roundUsd(calculation.getCurrentValue()))
      .currentValueKrw(MoneyUtils.roundKrw(calculation.getCurrentValueKrw()))
      .totalAssetKrw(MoneyUtils.roundKrw(calculation.getTotalAssetKrw()))
      .cashBalance(MoneyUtils.roundUsd(calculation.getCashBalance()))
      .realizedProfit(MoneyUtils.roundUsd(calculation.getRealizedProfit()))
      .totalReturnPercent(MoneyUtils.roundUsd(calculation.getTotalReturnPercent()))
      .totalReturnKrw(MoneyUtils.roundKrw(calculation.getTotalReturnKrw()))
      .averageFxRate(calculation.getAverageFxRate())
      .currentFxRate(calculation.getCurrentFxRate())
      .fxReturn(MoneyUtils.roundUsd(calculation.getFxReturn()))
      .fxReturnPercent(MoneyUtils.roundUsd(calculation.getFxReturnPercent()))
      .totalDividends(MoneyUtils.roundUsd(calculation.getTotalDividends()))
      .dividendYield(MoneyUtils.roundUsd(BacktestCalculationUtils.calculateDividendYield(
        calculation.getTotalDividends(), calculation.getTotalShares(),
        currentPrice.currentPrice())))
      .skippedRounds(skippedRounds)
      .defaultFxDates(defaultFxDates(transactions, calculation))
      .build();
  }

  // 거래 · 평가일 가운데 기본 환율로 계산한 날(겹치지 않게 날짜순)
  private static List<LocalDate> defaultFxDates(List<StrategyTransaction> transactions,
    StrategyCalculationResult calculation) {
    java.util.stream.Stream<LocalDate> traded = transactions.stream().map(StrategyTransaction::getDefaultFxDate);
    return java.util.stream.Stream.concat(traded, java.util.stream.Stream.of(calculation.getDefaultFxValuationDate()))
      .filter(java.util.Objects::nonNull)
      .distinct()
      .sorted()
      .toList();
  }

  // 원래 응답을 뺀 최고 · 최저 항목
  private static ComparisonItem withoutDetail(ComparisonItem item) {
    return item == null ? null : item.toBuilder().additionalData(null).build();
  }

  // ComparisonItem 생성 메서드들
  public ComparisonItem toComparisonItemFromSimulation(SimulationResponse simulation, String name) {
    return ComparisonItem.builder()
      .name(name)
      .code(simulation.getSymbol())
      .category("SYMBOL")
      .totalInvested(simulation.getInvestmentAmount())
      .totalShares(simulation.getShares())
      .averagePrice(simulation.getPurchasePrice())
      .currentValue(simulation.getCurrentValue())
      .currentValueKrw(simulation.getCurrentValueKrw())
      .totalReturnPercent(simulation.getTotalReturnPercent())
      .totalReturnKrw(simulation.getTotalReturnKrw())
      .fxReturn(simulation.getFxReturn())
      .fxReturnPercent(simulation.getFxReturnPercent())
      .dividends(simulation.getTotalDividends())
      .capitalGainsTax(simulation.getCapitalGainsTax())
      .optimalBuyDate(
        simulation.getOptimalBuyDate() != null ? simulation.getOptimalBuyDate().toString() : null)
      .optimalBuyPrice(simulation.getOptimalBuyPrice())
      .optimalSellDate(
        simulation.getOptimalSellDate() != null ? simulation.getOptimalSellDate().toString() : null)
      .optimalSellPrice(simulation.getOptimalSellPrice())
      .optimalReturnPercent(simulation.getOptimalReturnPercent())
      // 비교 항목의 원래 응답에서 매수일별 수익률 뺌
      .additionalData(simulation.toBuilder()
        .dailyValues(DailyValue.withoutDrawdowns(simulation.getDailyValues())).purchaseDayReturns(List.of()).build())
      .build();
  }

  public ComparisonItem toComparisonItemFromStrategy(StrategyResponse strategy, String name) {
    return ComparisonItem.builder()
      .name(name)
      .code(strategy.getSymbol())
      .category("STRATEGY")
      .totalInvested(strategy.getTotalInvested())
      .totalShares(strategy.getTotalShares())
      .averagePrice(strategy.getAveragePrice())
      .currentValue(strategy.getCurrentValue())
      .currentValueKrw(strategy.getCurrentValueKrw())
      .totalReturnPercent(strategy.getTotalReturnPercent())
      .totalReturnKrw(strategy.getTotalReturnKrw())
      .fxReturn(strategy.getFxReturn())
      .fxReturnPercent(strategy.getFxReturnPercent())
      .dividends(strategy.getTotalDividends())
      .capitalGainsTax(strategy.getCapitalGainsTax())
      // 비교 항목의 원래 응답에서 이동평균 차트 · 체결 시점 뺌
      .additionalData(strategy.toBuilder()
        .dailyValues(DailyValue.withoutDrawdowns(strategy.getDailyValues())).signalChart(null).executionTiming(null)
        .build())
      .build();
  }
}
