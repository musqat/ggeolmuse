package com.muscat.trade.domain.service;

import com.muscat.commonlib.constants.CommonConstants;
import com.muscat.trade.common.constants.TradeConstants;
import java.math.BigDecimal;

// 배당 한 건의 금액. 세전 · 세금은 센트에서 반올림, 세후는 그 차이
public record DividendAmounts(
  BigDecimal dividendPerShare,
  BigDecimal shares,
  BigDecimal grossAmount,
  BigDecimal taxAmount,
  BigDecimal netAmount
) {

  public static DividendAmounts of(BigDecimal dividendPerShare, BigDecimal shares) {
    BigDecimal gross = dividendPerShare.multiply(shares)
      .setScale(CommonConstants.DEFAULT_SCALE, CommonConstants.DEFAULT_ROUNDING_MODE);
    BigDecimal tax = gross.multiply(TradeConstants.DIVIDEND_TAX_RATE)
      .setScale(CommonConstants.DEFAULT_SCALE, CommonConstants.DEFAULT_ROUNDING_MODE);
    return new DividendAmounts(dividendPerShare, shares, gross, tax, gross.subtract(tax));
  }
}
