package com.muscat.backtest.common.enums.type;

import com.muscat.backtest.common.constants.BacktestConstants.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

// 양도세 낼 현금이 모자랄 때 처리(단일 종목)
public enum TaxSale {
  NONE,  // 현금이 생길 때 냄
  SELL;  // 그 날짜 종가에 모자란 만큼 팔아 냄

  // 수수료를 센트로 반올림해 빼도 판 돈이 모자라지 않게 더하는 달러
  private static final BigDecimal FEE_ROUNDING_USD = new BigDecimal("0.01");

  // 모자라면 파는지(비우면 팔지 않음)
  public static boolean sells(TaxSale taxSale) {
    return taxSale == SELL;
  }

  // 판 돈(수수료 뺌)이 모자란 달러 이상이 되는 주식 수: 모자란 달러(수수료가 있으면 + 1센트) ÷ (가격 × (1 - 수수료율)) 을 소수 여덟째에서 올림, 보유보다 많으면 보유 전부
  public static BigDecimal shares(BigDecimal shortfallUsd, BigDecimal price, BigDecimal feeRate, BigDecimal held) {
    boolean charged = feeRate != null && feeRate.signum() > 0;
    BigDecimal need = charged ? shortfallUsd.add(FEE_ROUNDING_USD) : shortfallUsd;
    BigDecimal net = charged ? price.multiply(BigDecimal.ONE.subtract(feeRate)) : price;
    return need.divide(net, Money.SHARES_SCALE, RoundingMode.CEILING).min(held);
  }
}
