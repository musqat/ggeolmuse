package com.muscat.trade.domain.service;

import com.muscat.trade.common.enums.type.PriceType;
import java.math.BigDecimal;
import java.time.LocalDate;

public interface MarketDataService {

  // 거래 가격 결정 (MANUAL인 경우 검증, OHLC인 경우 조회)
  BigDecimal determineTradePrice(String symbol, LocalDate tradeDate, PriceType priceType,
    BigDecimal manualPrice);

  // OHLC 가격 조회 (OPEN, HIGH, LOW, CLOSE)
  BigDecimal getOHLCPrice(String symbol, LocalDate tradeDate, PriceType priceType);

  // 직접 입력 가격 검증 (당일 High-Low 범위 내 확인)
  BigDecimal validateManualPrice(String symbol, LocalDate tradeDate, BigDecimal manualPrice);
}
