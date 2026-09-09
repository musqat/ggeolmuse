package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.calculation.StrategyCalculationResult;
import com.muscat.backtest.common.calculation.StrategyCalculator;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.StockPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 전략(DCA/조건부) 공통 마무리 처리. 매수내역 생성 이후의 동일 로직을 단일화:
 * 평가일 시세/환율 조회 → 총투자·환율합 → 배당 재투자 → 총배당 → StrategyCalculator.calculate.
 * (전략별로 다른 응답 매핑 toStrategyResponse 호출은 각 전략 유지 )
 */
public final class StrategyFinalizer {

  /** 마무리 결과: 거래내역 + 계산결과 + 평가일 시세. */
  public record Result(List<StrategyTransaction> transactions,
                       StrategyCalculationResult calculation,
                       StockPriceDto currentPrice) {}

  private StrategyFinalizer() {}

  public static Result run(
      MarketDataClient marketDataClient,
      String symbol,
      LocalDate valuationDate,
      BigDecimal manualCurrentFxRate,
      BigDecimal manualPurchaseFxRate,
      boolean reinvestEnabled,
      BigDecimal dividendTaxRate,
      List<StrategyTransaction> transactions) {

    StockPriceDto currentPrice = BacktestDataUtils.getPriceAt(
        marketDataClient, symbol, valuationDate);

    FxRateDto currentFxRate = manualCurrentFxRate != null
        ? new FxRateDto(valuationDate, manualCurrentFxRate)
        : BacktestDataUtils.getFxRateAt(marketDataClient, valuationDate);

    LocalDate firstPurchaseDate = transactions.getFirst().getDate();
    DividendHistoryDto dividendHistory = BacktestDataUtils.getDividendHistory(
        marketDataClient, symbol, firstPurchaseDate, valuationDate);

    BigDecimal totalInvested = transactions.stream()
        .map(StrategyTransaction::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    // 가중평균 환율 합계 = Σ(투자금액 × 환율)
    BigDecimal totalFxRateSum = transactions.stream()
        .map(t -> t.getFxRate().multiply(t.getAmount()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal dividendsReinvested = DividendReinvestor.reinvest(
        marketDataClient, dividendHistory, transactions, symbol,
        firstPurchaseDate, reinvestEnabled, dividendTaxRate, manualPurchaseFxRate, currentFxRate.rate());

    // 배당 재투자 후 총 보유 주식수
    BigDecimal totalShares = transactions.stream()
        .map(StrategyTransaction::getShares)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalDividends = BacktestCalculationUtils.calculateTotalDividends(
        dividendHistory, transactions, firstPurchaseDate, valuationDate);

    StrategyCalculationResult calculation = StrategyCalculator.calculate(
        transactions, totalInvested, totalShares, totalFxRateSum, currentPrice, currentFxRate,
        totalDividends, dividendsReinvested, dividendTaxRate);

    return new Result(transactions, calculation, currentPrice);
  }
}
