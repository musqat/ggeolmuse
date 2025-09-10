package com.muscat.backtest.domain.mapper;

import com.muscat.backtest.common.calculation.ComparisonCalculationResult;
import com.muscat.backtest.common.calculation.StrategyCalculationResult;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.backtest.domain.dto.request.BaseComparisonRequest;
import com.muscat.backtest.domain.dto.request.InvestmentRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.request.StrategyRequest;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.InvestmentResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.model.ComparisonItem;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.dto.HoldingDto;
import com.muscat.backtest.infra.client.dto.StockPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ResponseMapper {

  public StrategyResponse toStrategyResponse(
      StrategyRequest request,
      List<StrategyTransaction> transactions,
      StrategyCalculationResult calculation,
      StockPriceDto currentPrice) {

    return StrategyResponse.builder()
        .symbol(request.getSymbol())
        .startDate(request.getStartDate())
        .endDate(request.getEndDate())
        .strategyType(request.getStrategyType())
        .transactions(transactions)
        .totalTransactions(transactions.size())
        .totalInvested(MoneyUtils.roundKrw(calculation.getTotalInvested()))
        .totalShares(calculation.getTotalShares().setScale(6, MoneyUtils.ROUND_MODE))
        .averagePrice(MoneyUtils.roundUsd(calculation.getAveragePrice()))
        .currentPrice(currentPrice.getCurrentPrice())
        .currentValue(MoneyUtils.roundUsd(calculation.getCurrentValue()))
        .currentValueKrw(MoneyUtils.roundKrw(calculation.getCurrentValueKrw()))
        .totalReturn(MoneyUtils.roundUsd(calculation.getTotalReturnUsd()))
        .totalReturnPercent(
            MoneyUtils.roundUsd(calculation.getTotalReturnPercent()))
        .totalReturnKrw(MoneyUtils.roundKrw(calculation.getTotalReturnKrw()))
        .averageFxRate(calculation.getAverageFxRate())
        .currentFxRate(calculation.getCurrentFxRate())
        .fxReturn(MoneyUtils.roundUsd(calculation.getFxReturn()))
        .fxReturnPercent(MoneyUtils.roundUsd(calculation.getFxReturnPercent()))
        .totalDividends(BigDecimal.ZERO)
        .dividendYield(BigDecimal.ZERO)
        .strategyDetails(createStrategyDetails(request.getStrategyType(), transactions.size()))
        .performanceSummary(createPerformanceSummary(calculation))
        .build();
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
        .bestPerformer(calculation.getBestPerformer())
        .worstPerformer(calculation.getWorstPerformer())
        .averageReturn(calculation.getAverageReturn())
        .medianReturn(calculation.getMedianReturn())
        .summary(calculation.getSummary())
        .analysisDetails(calculation.getAnalysisDetails())
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
      BigDecimal totalReturnKrw,
      BigDecimal totalReturnPercent) {

    return SimulationResponse.builder()
        .symbol(request.getSymbol())
        .purchaseDate(request.getPurchaseDate())
        .currentDate(LocalDate.now())
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
        .totalReturn(MoneyUtils.roundUsd(totalReturnKrw))
        .totalReturnPercent(MoneyUtils.roundUsd(totalReturnPercent))
        .currentValueKrw(MoneyUtils.roundKrw(currentValueKrw))
        .totalReturnKrw(MoneyUtils.roundKrw(totalReturnKrw))
        .remainingCashKrw(MoneyUtils.roundKrw(
            MoneyUtils.calculateUsdToKrw(remainingCash, currentFxRate)))
        .build();
  }

  public InvestmentResponse toInvestmentResponse(
      InvestmentRequest request,
      SimulationResponse backtestResult) {

    return InvestmentResponse.builder()
        .simulation(backtestResult)
        .symbol(backtestResult.getSymbol())
        .purchaseDate(backtestResult.getPurchaseDate())
        .investmentAmount(backtestResult.getInvestmentAmount())
        .purchasePrice(backtestResult.getPurchasePrice())
        .shares(backtestResult.getShares())
        .totalCost(backtestResult.getInvestmentAmount())
        .status("SUCCESS")
        .message("과거 매수 백테스트가 완료되었습니다")
        .portfolioCreated(false)
        .portfolioStatus("BACKTEST_COMPLETED")
        .build();
  }

  public InvestmentResponse toInvestmentResponse(
      HoldingDto holding,
      SimulationResponse backtestResult) {

    return InvestmentResponse.builder()
        .simulation(backtestResult)
        .holdingId(holding.getHoldingId())
        .symbol(holding.getSymbol())
        .purchaseDate(holding.getPurchaseDate())
        .investmentAmount(holding.getTotalInvested())
        .purchasePrice(backtestResult.getPurchasePrice())
        .shares(holding.getShares())
        .totalCost(holding.getTotalInvested())
        .status("SUCCESS")
        .message("보유 주식 백테스트가 완료되었습니다")
        .portfolioCreated(false)
        .portfolioStatus("BACKTEST_COMPLETED")
        .build();
  }

  private String createStrategyDetails(StrategyType strategyType,
      int transactionCount) {
    return switch (strategyType) {
      case DCA -> String.format("DCA 전략 - %d회 투자 실행", transactionCount);
      case CONDITIONAL_PURCHASE -> String.format("조건부매수 전략 - %d회 투자 실행", transactionCount);
    };
  }

  private String createPerformanceSummary(StrategyCalculationResult calculation) {
    return String.format("수익률: %.2f%%, 환차익: %.2f%%",
        calculation.getTotalReturnPercent(), calculation.getFxReturnPercent());
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
        .totalReturn(simulation.getTotalReturn())
        .totalReturnPercent(simulation.getTotalReturnPercent())
        .totalReturnKrw(simulation.getTotalReturnKrw())
        .fxReturn(simulation.getFxReturn())
        .fxReturnPercent(simulation.getFxReturnPercent())
        .dividends(simulation.getTotalDividends())
        .additionalData(simulation)
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
        .totalReturn(strategy.getTotalReturn())
        .totalReturnPercent(strategy.getTotalReturnPercent())
        .totalReturnKrw(strategy.getTotalReturnKrw())
        .fxReturn(strategy.getFxReturn())
        .fxReturnPercent(strategy.getFxReturnPercent())
        .dividends(strategy.getTotalDividends())
        .additionalData(strategy)
        .performanceNote(strategy.getPerformanceSummary())
        .build();
  }
}