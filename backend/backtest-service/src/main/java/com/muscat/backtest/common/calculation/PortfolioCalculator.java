package com.muscat.backtest.common.calculation;

import com.muscat.backtest.common.engine.PortfolioEngine;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.PortfolioMethod;
import com.muscat.backtest.common.enums.type.TradeReason;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.domain.dto.request.PortfolioRequest;
import com.muscat.backtest.domain.dto.response.PortfolioResponse;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// 포트폴리오 엔진 결과로 합계 · 종목 줄(넣은 돈 · 지금 가치 · 판 돈 · 배당 · 이익 · 기여 · 비중) · 매매 통계 · 양도세를 내는 계산기
public final class PortfolioCalculator {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private PortfolioCalculator() {}

  // 총 수익 = 총 자산 - 넣은 돈, 종목 이익 밖의 이익 = 총 수익 - 종목 이익 합, 판 거래가 있으면 매매 통계, 양도세를 켜면 포트폴리오 전체 양도세
  public static PortfolioResponse of(PortfolioRequest request, PortfolioEngine.Result result) {
    List<StrategyTransaction> transactions = result.transactions();
    BigDecimal invested = MoneyUtils.roundKrw(result.totalInvestedKrw());
    BigDecimal totalAsset = result.totalAssetKrw();
    BigDecimal totalReturn = totalAsset.subtract(invested);
    BigDecimal plannedSum = result.positions().stream()
      .map(PortfolioEngine.Position::plannedKrw)
      .reduce(BigDecimal.ZERO, BigDecimal::add);
    List<PortfolioResponse.Asset> assets = result.positions().stream()
      .map(position -> asset(position, result, totalReturn, plannedSum))
      .toList();
    List<CapitalGainsTaxCalculator.Gain> gains = CapitalGainsTaxCalculator.realizedGains(transactions);
    boolean sold = transactions.stream().anyMatch(StrategyTransaction::isSell);
    return PortfolioResponse.builder()
      .startDate(result.startDate())
      .endDate(result.valuationDate())
      .method(PortfolioMethod.orLumpSum(request.getMethod()))
      .periodEnd(request.getPeriodEnd() != null ? request.getPeriodEnd() : PeriodEnd.HOLD)
      .totalInvestedKrw(invested)
      .totalAssetKrw(totalAsset)
      .stockValueKrw(result.stockValueKrw())
      .cashKrw(result.cashKrw())
      .cashUsd(MoneyUtils.roundUsd(result.cashUsd()))
      .totalReturnKrw(totalReturn)
      .totalReturnPercent(Decimals.isPositive(invested) ? percent(totalReturn, invested) : BigDecimal.ZERO)
      .realizedProfitKrw(MoneyUtils.roundKrw(sum(gains)))
      .otherProfitKrw(totalReturn.subtract(assets.stream()
        .map(PortfolioResponse.Asset::profitKrw)
        .reduce(BigDecimal.ZERO, BigDecimal::add)))
      .currentFxRate(result.currentFxRate())
      .buyCount(assets.stream().mapToInt(PortfolioResponse.Asset::buyCount).sum())
      .sellCount(assets.stream().mapToInt(PortfolioResponse.Asset::sellCount).sum())
      .metricsKrw(result.performance().metricsKrw())
      .metricsUsd(result.performance().metricsUsd())
      .dailyValues(result.performance().dailyValues())
      .assets(assets)
      .transactions(transactions)
      .tradeStats(sold ? TradeStatsCalculator.of(transactions, request.getStartDate(), result.valuationDate()) : null)
      .capitalGainsTax(Boolean.TRUE.equals(request.getCapitalGainsTax())
        ? CapitalGainsTaxCalculator.of(gains, result.taxPayments(), totalAsset, result.stockValueKrw(), invested)
        : null)
      .defaultFxDates(result.defaultFxDates())
      .build();
  }

  // 종목 줄 하나(넣은 돈 = 매수마다 새 돈 + 쓴 현금 × 그 날짜 환율, 판 돈 = 판 돈 × 판 날 환율, 배당 = 세후 배당 × 배당락일 환율)
  private static PortfolioResponse.Asset asset(PortfolioEngine.Position position, PortfolioEngine.Result result,
    BigDecimal totalReturn, BigDecimal plannedSum) {
    List<StrategyTransaction> own = result.transactions().stream()
      .filter(tx -> position.symbol().equals(tx.getSymbol()))
      .toList();
    BigDecimal invested = MoneyUtils.roundKrw(own.stream()
      .filter(tx -> !tx.isSell())
      .map(PortfolioCalculator::costKrw)
      .reduce(BigDecimal.ZERO, BigDecimal::add));
    BigDecimal sold = MoneyUtils.roundKrw(own.stream()
      .filter(StrategyTransaction::isSell)
      .map(tx -> tx.getProceeds().multiply(tx.getFxRate()))
      .reduce(BigDecimal.ZERO, BigDecimal::add));
    BigDecimal dividend = MoneyUtils.roundKrw(result.dividends().stream()
      .filter(paid -> position.symbol().equals(paid.symbol()))
      .map(paid -> paid.amountUsd().multiply(paid.fxRate()))
      .reduce(BigDecimal.ZERO, BigDecimal::add));
    BigDecimal value = position.valueKrw();
    BigDecimal profit = value.add(sold).add(dividend).subtract(invested);
    return PortfolioResponse.Asset.builder()
      .symbol(position.symbol())
      .investedKrw(invested)
      .valueKrw(value)
      .soldKrw(sold)
      .dividendKrw(dividend)
      .profitKrw(profit)
      .returnPercent(Decimals.isPositive(invested) ? percent(profit, invested) : null)
      .contributionPercent(Decimals.isZero(totalReturn) ? null : percent(profit, totalReturn))
      .startWeightPercent(percent(position.plannedKrw(), plannedSum))
      .endWeightPercent(Decimals.isPositive(result.totalAssetKrw())
        ? percent(value, result.totalAssetKrw())
        : BigDecimal.ZERO)
      .shares(position.shares().setScale(6, RoundingMode.HALF_UP))
      .realizedProfitKrw(MoneyUtils.roundKrw(sum(CapitalGainsTaxCalculator.realizedGains(own))))
      .buyCount((int) own.stream()
        .filter(tx -> !tx.isSell() && tx.getReason() != TradeReason.DIVIDEND_REINVESTMENT)
        .count())
      .sellCount((int) own.stream().filter(StrategyTransaction::isSell).count())
      .build();
  }

  // 새 돈 원화 + 쓴 현금 × 그 날짜 환율
  private static BigDecimal costKrw(StrategyTransaction tx) {
    BigDecimal cash = tx.getCashUsed() != null ? tx.getCashUsed().multiply(tx.getFxRate()) : BigDecimal.ZERO;
    return tx.getAmount().add(cash);
  }

  private static BigDecimal sum(List<CapitalGainsTaxCalculator.Gain> gains) {
    return gains.stream().map(CapitalGainsTaxCalculator.Gain::amountKrw).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  // 값 ÷ 기준 × 100(소수 둘째)
  private static BigDecimal percent(BigDecimal value, BigDecimal base) {
    return value.multiply(HUNDRED).divide(base, 2, RoundingMode.HALF_UP);
  }
}
