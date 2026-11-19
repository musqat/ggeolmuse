package com.muscat.trade.domain.service;

import com.muscat.trade.common.enums.type.PriceType;
import java.math.BigDecimal;
import java.time.LocalDate;

public interface MarketDataService {

  // 주문 날짜에서 체결할 거래일(미국 동부 오늘보다 뒤면 거절, 주말은 직전 거래일, 평일 휴장은 거절)
  LocalDate resolveTradeDate(String symbol, LocalDate requested);

  // 거래 가격 결정 (MANUAL인 경우 검증, OHLC인 경우 조회)
  BigDecimal determineTradePrice(String symbol, LocalDate tradeDate, PriceType priceType,
                                 BigDecimal manualPrice);

  // OHLC 가격 조회 (OPEN, HIGH, LOW, CLOSE)
  BigDecimal getOHLCPrice(String symbol, LocalDate tradeDate, PriceType priceType);

  // 직접 입력 가격 검증 (당일 High-Low 범위 내 확인)
  BigDecimal validateManualPrice(String symbol, LocalDate tradeDate, BigDecimal manualPrice);
}
