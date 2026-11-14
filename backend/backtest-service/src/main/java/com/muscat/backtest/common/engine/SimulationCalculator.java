package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.calculation.StrategyCalculator;
import com.muscat.backtest.common.constants.BacktestConstants;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.common.util.RoundPurchase;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.model.SimulationCalculationResult;
import com.muscat.backtest.domain.model.SimulationContext;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NavigableSet;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

// 단일 매수 손익: 산 날 주식 수 · 수수료 → 배당(재투자 또는 현금) · 연말 정리 → 기간 끝 매도 → 평가일 총자산 · 수익률
@Slf4j
public final class SimulationCalculator {

  // 산 날 원금(KRW) · 쓴 달러 · 주식 수 · 수수료(USD) · 남은 달러
  record Purchase(BigDecimal investmentKrw, BigDecimal usdAmount, BigDecimal shares, BigDecimal fee,
                  BigDecimal remainingCash) {}

  private SimulationCalculator() {}

  // 일별 환율 fx 와 배당락일 종가 closeOn(휴장일이면 직전 거래일)으로 낸 손익
  public static SimulationCalculationResult calculate(SimulationContext context, Function<LocalDate, BigDecimal> fx,
    Function<LocalDate, BigDecimal> closeOn) {
    SimulationRequest request = context.request();
    BigDecimal purchasePriceUsd = PriceLookup.effectiveClose(context.purchaseData());
    BigDecimal purchaseFxRate = context.purchaseFxRate().rate();
    BigDecimal currentFxRate = context.currentFxRate().rate();
    BigDecimal currentPriceUsd = context.valuationPrice();
    Purchase purchase = purchase(request.getInvestmentAmount(), request.getSharesPerPurchase(), purchasePriceUsd,
      purchaseFxRate, request.getTradingFeeRate());
    log.debug("매수 계산: 원금 {}원 → {}달러, {}주 (환율 {})", purchase.investmentKrw(), purchase.usdAmount(),
      purchase.shares(), purchaseFxRate);
    BigDecimal remainingCash = purchase.remainingCash();
    BigDecimal tradingFee = purchase.fee();
    BigDecimal buyCostUsd = purchase.shares().multiply(purchasePriceUsd).add(tradingFee);

    // 매수일부터의 보유 단계 · 양도세 계산 거래(산 것 · 재투자 · 연말 정리 · 기간 끝 매도)
    LocalDate tradeDate = context.purchaseData().date();
    HoldingTrail trail = new HoldingTrail(harvestDates(context), context.priceMap(), request.getTradingFeeRate(), fx);
    trail.step(tradeDate, purchase.shares(), remainingCash);
    trail.taxTrade(StrategyTransaction.builder().date(tradeDate).actualDate(tradeDate).price(purchasePriceUsd)
      .shares(purchase.shares()).fee(tradingFee).cashUsed(buyCostUsd).fxRate(purchaseFxRate).build());

    BigDecimal stockReturn = currentPriceUsd.subtract(purchasePriceUsd);
    BigDecimal stockReturnPercent = MoneyUtils.calculateReturnRate(purchasePriceUsd, currentPriceUsd);
    BigDecimal fxReturn = currentFxRate.subtract(purchaseFxRate);
    BigDecimal fxReturnPercent = MoneyUtils.calculateReturnRate(purchaseFxRate, currentFxRate);

    boolean reinvested = Boolean.TRUE.equals(request.getReinvestDividends());
    SimulationDividends.Result dividends = reinvested
      ? SimulationDividends.reinvest(context, trail, purchase.shares(), remainingCash, closeOn)
      : SimulationDividends.keepAsCash(context, trail, purchase.shares(), remainingCash);
    BigDecimal shares = trail.harvestBefore(context.valuationDate().plusDays(1), dividends.shares(),
      dividends.cash());
    tradingFee = tradingFee.add(trail.harvestFees());
    // 받은 배당 총액(세전, 배당마다 배당락일 주식 수로)
    BigDecimal totalDividends = reinvested
      ? dividends.gross()
      : dividends.gross().setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);

    BigDecimal dividendYield = BacktestCalculationUtils.calculateDividendYield(totalDividends, shares,
      currentPriceUsd);

    // 재투자를 끄면 세후 배당을 현금으로(켜면 이미 주식 수에 포함)
    BigDecimal cashDividendKrw = !reinvested && Decimals.isPositive(totalDividends)
      ? MoneyUtils.convertUsdToKrw(
        BacktestCalculationUtils.afterDividendTax(totalDividends, request.getDividendTaxRate(), false), currentFxRate)
      : BigDecimal.ZERO;

    // 기간 끝에 팔면 주식 0 · 판 돈은 남은 현금으로(환율은 평가일 환율)
    StrategyTransaction endSale = PeriodEnd.sells(request.getPeriodEnd())
      ? PeriodEndSale.of(shares, context.priceMap(), context.valuationDate(), currentPriceUsd,
        request.getTradingFeeRate(), currentFxRate)
      : null;
    BigDecimal heldShares = shares;
    if (endSale != null) {
      endSale.setRealizedProfit(endSale.getProceeds().subtract(StrategyCalculator.holdingCost(trail.taxTransactions())));
      trail.taxTrade(endSale);
      heldShares = BigDecimal.ZERO;
      remainingCash = remainingCash.add(endSale.getProceeds());
      tradingFee = tradingFee.add(endSale.getFee());
      trail.step(endSale.tradedOn(), BigDecimal.ZERO, trail.steps().getLast().cashUsd().add(endSale.getProceeds()));
    }

    // 재투자 · 기간 끝 매도 후 현재 가치
    BigDecimal currentValueUsd = heldShares.multiply(currentPriceUsd);
    BigDecimal currentValueKrw = Decimals.isPositive(currentValueUsd)
      ? MoneyUtils.convertUsdToKrw(currentValueUsd, currentFxRate)
      : BigDecimal.ZERO;

    // 남은 현금도 자산에 포함 (USD → KRW 환산)
    BigDecimal remainingCashKrw = Decimals.isPositive(remainingCash)
      ? MoneyUtils.convertUsdToKrw(remainingCash, currentFxRate)
      : BigDecimal.ZERO;

    BigDecimal totalAssetKrw = currentValueKrw.add(remainingCashKrw).add(cashDividendKrw);

    // 총 수익 = 총 자산 - 투자금
    BigDecimal totalReturnKrw = totalAssetKrw.subtract(purchase.investmentKrw());

    // 총 수익률 = (총 자산 / 투자금) - 1
    BigDecimal totalReturnPercent = MoneyUtils.calculateReturnRate(purchase.investmentKrw(), totalAssetKrw);

    return new SimulationCalculationResult(purchase.investmentKrw(),
      purchasePriceUsd, shares, currentPriceUsd, currentValueUsd, currentValueKrw,
      stockReturn, stockReturnPercent, purchaseFxRate, currentFxRate, fxReturn, fxReturnPercent,
      totalDividends, dividendYield, tradingFee, remainingCash, totalAssetKrw, totalReturnKrw,
      totalReturnPercent,
      dividends.reinvested(), dividends.reinvestDates(), trail.steps(), trail.taxTransactions(),
      trail.harvestTransactions(endSale));
  }

  // 투자 금액이면 환전한 달러로 수수료를 빼고 사고, 주식 수면 정한 주식 수 × 종가 + 수수료를 원화로
  static Purchase purchase(BigDecimal investmentKrw, Integer shares, BigDecimal priceUsd, BigDecimal fxRate,
    BigDecimal feeRate) {
    if (shares != null) {
      RoundPurchase bought = RoundPurchase.byShares(shares, priceUsd, fxRate, feeRate);
      return new Purchase(bought.amountKrw(), bought.costUsd(), bought.shares(), bought.feeUsd(), BigDecimal.ZERO);
    }
    BigDecimal usdAmount = MoneyUtils.convertKrwToUsd(investmentKrw, fxRate);
    BigDecimal boughtShares = BacktestCalculationUtils.calculateSharesWithFee(usdAmount, priceUsd, feeRate);
    BigDecimal fee = BacktestCalculationUtils.calculateTradingFee(usdAmount, feeRate);
    BigDecimal totalCost = BacktestCalculationUtils.calculateTotalCost(boughtShares, priceUsd, fee);
    return new Purchase(investmentKrw, usdAmount, boughtShares, fee,
      BacktestCalculationUtils.calculateRemainingCash(usdAmount, totalCost));
  }

  // 양도세와 연말 정리를 켜면 매수일 뒤 연말 정리 날, 아니면 null
  private static NavigableSet<LocalDate> harvestDates(SimulationContext context) {
    SimulationRequest request = context.request();
    return YearEndHarvest.enabled(request.getCapitalGainsTax(), request.getYearEndHarvest())
      ? YearEndHarvest.dates(context.priceMap(), context.purchaseData().date(), context.valuationDate())
      : null;
  }
}
