package com.muscat.backtest.common.util;

import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;

// 한 회차 매수: 사는 주식 수 · 수수료(USD) · 쓰는 달러(수수료 포함) · 넣는 원화
public record RoundPurchase(BigDecimal shares, BigDecimal feeUsd, BigDecimal costUsd, BigDecimal amountKrw) {

  // 원화 금액만큼 삼(수수료를 뺀 달러로 소수 주)
  public static RoundPurchase byAmount(BigDecimal amountKrw, BigDecimal priceUsd, BigDecimal fxRate,
    BigDecimal feeRate) {
    BigDecimal fee = BacktestCalculationUtils.calculatePurchaseFee(amountKrw, fxRate, feeRate);
    BigDecimal shares = BacktestCalculationUtils.calculateSharesAfterFee(amountKrw, fxRate, priceUsd, fee);
    return new RoundPurchase(shares, fee, amountKrw.divide(fxRate, 8, RoundingMode.HALF_UP), amountKrw);
  }

  // 정한 주식 수만큼 삼(주가 × 주식 수 + 수수료를 그 환율로 원화)
  public static RoundPurchase byShares(int shares, BigDecimal priceUsd, BigDecimal fxRate, BigDecimal feeRate) {
    BigDecimal count = BigDecimal.valueOf(shares);
    BigDecimal stockUsd = priceUsd.multiply(count);
    BigDecimal fee = feeOn(stockUsd, feeRate);
    BigDecimal costUsd = stockUsd.add(fee);
    return new RoundPurchase(count, fee, costUsd, MoneyUtils.convertUsdToKrw(costUsd, fxRate));
  }

  // 가진 달러로 살 수 있는 만큼(많아야 shares 주, 한 주도 못 사면 null)
  public static RoundPurchase withinUsd(int shares, BigDecimal usd, BigDecimal priceUsd, BigDecimal fxRate,
    BigDecimal feeRate) {
    BigDecimal rate = feeRate != null && Decimals.isPositive(feeRate) ? feeRate : BigDecimal.ZERO;
    BigDecimal perShare = priceUsd.multiply(BigDecimal.ONE.add(rate));
    // 수수료가 반올림으로 내려 한 주 더 살 수 있는 경우까지
    int most = usd.divide(perShare, 0, RoundingMode.DOWN).add(BigDecimal.ONE)
      .min(BigDecimal.valueOf(shares)).intValue();
    for (int count = most; count > 0; count--) {
      RoundPurchase purchase = byShares(count, priceUsd, fxRate, feeRate);
      if (purchase.costUsd().compareTo(usd) <= 0) {
        return purchase;
      }
    }
    return null;
  }

  // 거래 달러 × 수수료율(USD 두 자리, 수수료율이 없으면 0)
  private static BigDecimal feeOn(BigDecimal usd, BigDecimal feeRate) {
    return feeRate != null && Decimals.isPositive(feeRate)
      ? usd.multiply(feeRate).setScale(MoneyUtils.USD_SCALE, RoundingMode.HALF_UP)
      : BigDecimal.ZERO;
  }
}
