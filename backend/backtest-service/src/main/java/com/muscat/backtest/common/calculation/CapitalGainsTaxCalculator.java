package com.muscat.backtest.common.calculation;

import com.muscat.backtest.common.constants.BacktestConstants.CapitalGains;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.domain.dto.response.CapitalGainsTax;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// 판 거래의 원화 실현 이익으로 연도별 예상 양도소득세와 세후 평가를 내는 계산기
public final class CapitalGainsTaxCalculator {

  /** 판 날짜와 원화 실현 이익 */
  public record Gain(LocalDate date, BigDecimal amountKrw) {}

  private CapitalGainsTaxCalculator() {}

  // 판 거래마다 판 날 환율로 바꾼 판 돈(수수료 뺌) - 보유분 평균 원가(산 날 환율, 수수료 포함), 거래일 순서
  public static List<Gain> realizedGains(List<StrategyTransaction> transactions) {
    List<StrategyTransaction> ordered = transactions.stream()
      .sorted(Comparator.comparing(StrategyTransaction::tradedOn))
      .toList();
    List<Gain> gains = new ArrayList<>();
    BigDecimal shares = BigDecimal.ZERO;
    BigDecimal cost = BigDecimal.ZERO;
    for (StrategyTransaction tx : ordered) {
      if (!tx.isSell()) {
        shares = shares.add(tx.getShares());
        cost = cost.add(costKrw(tx));
        continue;
      }
      BigDecimal sold = tx.getShares().negate();
      BigDecimal soldCost = Decimals.isPositive(shares)
        ? cost.multiply(sold.min(shares)).divide(shares, 8, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;
      gains.add(new Gain(tx.tradedOn(), tx.getProceeds().multiply(tx.getFxRate()).subtract(soldCost)));
      shares = shares.subtract(sold);
      cost = cost.subtract(soldCost);
    }
    return gains;
  }

  // 산 날 환율 원가(넣은 원화 + 쓴 판 돈 × 그 날짜 환율, 배당 재투자는 주식 수 × 주가 × 그 날짜 환율)
  private static BigDecimal costKrw(StrategyTransaction tx) {
    BigDecimal deposit = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
    if (tx.getCashUsed() != null) {
      return deposit.add(tx.getCashUsed().multiply(tx.getFxRate()));
    }
    return Decimals.isPositive(deposit) ? deposit : tx.getShares().multiply(tx.getPrice()).multiply(tx.getFxRate());
  }

  // 판 해마다 이익 합(손실은 그해 안에서만 상계) · 공제 · 세금, 세후 평가는 평가 금액 - 세금 합, 세후 수익률은 기준 금액 대비(0 이면 0)
  public static CapitalGainsTax of(List<Gain> gains, BigDecimal valueKrw, BigDecimal basisKrw) {
    Map<Integer, BigDecimal> byYear = new TreeMap<>();
    gains.forEach(gain -> byYear.merge(gain.date().getYear(), gain.amountKrw(), BigDecimal::add));

    List<CapitalGainsTax.Year> years = new ArrayList<>();
    BigDecimal totalTax = BigDecimal.ZERO;
    for (Map.Entry<Integer, BigDecimal> entry : byYear.entrySet()) {
      BigDecimal realized = MoneyUtils.roundKrw(entry.getValue());
      BigDecimal deduction = Decimals.isPositive(realized)
        ? realized.min(CapitalGains.ANNUAL_DEDUCTION_KRW)
        : BigDecimal.ZERO;
      BigDecimal tax = realized.subtract(deduction).max(BigDecimal.ZERO).multiply(CapitalGains.TAX_RATE)
        .setScale(0, RoundingMode.DOWN);
      years.add(new CapitalGainsTax.Year(entry.getKey(), realized, deduction, tax));
      totalTax = totalTax.add(tax);
    }

    BigDecimal afterTax = valueKrw.subtract(totalTax);
    BigDecimal afterTaxReturn = Decimals.isPositive(basisKrw)
      ? MoneyUtils.calculateReturnRate(basisKrw, afterTax)
      : BigDecimal.ZERO;
    return new CapitalGainsTax(years, totalTax, MoneyUtils.roundKrw(afterTax), afterTaxReturn);
  }
}
