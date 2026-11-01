package com.muscat.trade.domain.service;

import com.muscat.commonlib.constants.CommonConstants;
import com.muscat.trade.common.constants.TradeConstants;
import java.math.BigDecimal;

// 배당 한 건의 금액(세전 · 세금은 센트에서 반올림, 세후는 그 차이)
public record DividendAmounts(
  BigDecimal dividendPerShare,
  BigDecimal shares,
  BigDecimal grossAmount,
  BigDecimal taxAmount,
  BigDecimal netAmount
) {

  // 몫 표 dividend_per_share 칸의 소수 자리
  private static final int PER_SHARE_SCALE = 6;

  public static DividendAmounts of(BigDecimal dividendPerShare, BigDecimal shares) {
    // 저장한 값과 다시 계산한 값이 같도록 칸보다 긴 주당 배당만 반올림
    BigDecimal perShare = dividendPerShare.scale() > PER_SHARE_SCALE
      ? dividendPerShare.setScale(PER_SHARE_SCALE, CommonConstants.DEFAULT_ROUNDING_MODE)
      : dividendPerShare;
    BigDecimal gross = perShare.multiply(shares)
      .setScale(CommonConstants.DEFAULT_SCALE, CommonConstants.DEFAULT_ROUNDING_MODE);
    BigDecimal tax = gross.multiply(TradeConstants.DIVIDEND_TAX_RATE)
      .setScale(CommonConstants.DEFAULT_SCALE, CommonConstants.DEFAULT_ROUNDING_MODE);
    return new DividendAmounts(perShare, shares, gross, tax, gross.subtract(tax));
  }
}
