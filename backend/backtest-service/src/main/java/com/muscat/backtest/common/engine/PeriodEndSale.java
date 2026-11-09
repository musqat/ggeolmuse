package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

// 기간 끝 매도: 남은 주식 전량을 기간 마지막 거래일 종가에 팔고 수수료를 뺀 판 돈을 남김
public final class PeriodEndSale {

  private PeriodEndSale() {}

  // 판 날(평가일까지 마지막 일봉 날짜, 일봉이 없으면 평가일)
  public static LocalDate saleDate(Map<LocalDate, OHLCPriceDto> priceMap, LocalDate valuationDate) {
    return lastCandle(priceMap, valuationDate).map(OHLCPriceDto::date).orElse(valuationDate);
  }

  // 평가일까지 마지막 일봉 종가(일봉이 없으면 평가일 시세)에 판 거래, 남은 주식이 없으면 null
  public static StrategyTransaction of(BigDecimal shares, Map<LocalDate, OHLCPriceDto> priceMap, LocalDate valuationDate,
    BigDecimal valuationPrice, BigDecimal feeRate, BigDecimal fxRate) {
    if (!Decimals.isPositive(shares)) {
      return null;
    }
    Optional<OHLCPriceDto> last = lastCandle(priceMap, valuationDate);
    LocalDate date = last.map(OHLCPriceDto::date).orElse(valuationDate);
    BigDecimal price = last.map(PriceLookup::effectiveClose).orElse(valuationPrice);
    BigDecimal gross = shares.multiply(price).setScale(8, RoundingMode.HALF_UP);
    BigDecimal fee = BacktestCalculationUtils.calculateTradingFee(gross, feeRate);
    return StrategyTransaction.builder()
      .date(date)
      .actualDate(date)
      .price(price)
      .shares(shares.negate())
      .fee(fee)
      .amount(BigDecimal.ZERO)
      .proceeds(gross.subtract(fee))
      .fxRate(fxRate)
      .trigger(TransactionTrigger.PERIOD_END_SALE)
      .build();
  }

  private static Optional<OHLCPriceDto> lastCandle(Map<LocalDate, OHLCPriceDto> priceMap, LocalDate valuationDate) {
    return priceMap.values().stream()
      .filter(PriceLookup::isUsable)
      .filter(candle -> !candle.date().isAfter(valuationDate))
      .max(Comparator.comparing(OHLCPriceDto::date));
  }
}
