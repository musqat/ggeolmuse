package com.muscat.trade.domain.service.impl;

import com.muscat.commonlib.dto.StockPriceDto;
import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.domain.service.MarketDataService;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataServiceImpl implements MarketDataService {

  private static final String MANUAL_VALIDATION = "MANUAL_VALIDATION";

  private final MarketServiceClientWrapper marketServiceClientWrapper;
  private final TradeLogger tradeLogger;

  @Override
  public BigDecimal determineTradePrice(String symbol, LocalDate tradeDate, PriceType priceType,
    BigDecimal manualPrice) {
    if (priceType == PriceType.MANUAL) {
      // 직접입력인 경우 가격 범위 검증
      return validateManualPrice(symbol, tradeDate, manualPrice);
    } else {
      // OHLC 가격 조회
      return getOHLCPrice(symbol, tradeDate, priceType);
    }
  }

  @Override
  public BigDecimal getOHLCPrice(String symbol, LocalDate tradeDate, PriceType priceType) {
    String label = priceType.name();
    StockPriceDto ohlc = fetchAvailable(symbol, tradeDate, label);
    BigDecimal price = switch (priceType) {
      case OPEN -> ohlc.openPrice();
      case HIGH -> ohlc.highPrice();
      case LOW -> ohlc.lowPrice();
      case CLOSE -> ohlc.closePrice();
      case MANUAL -> throw failure(symbol, label, "Unsupported price type: " + priceType,
        TradeResponse.MARKET_DATA_SERVICE_ERROR);
    };
    if (price == null) {
      throw failure(symbol, label, "Price data is null for type: " + priceType,
        TradeResponse.MARKET_DATA_SERVICE_ERROR);
    }

    tradeLogger.logMarketDataRequest(symbol, label, true, null);
    log.info("시장 가격 조회 성공: symbol={}, date={}, priceType={}, price={}",
      symbol, tradeDate, priceType, price);
    return price;
  }

  @Override
  public BigDecimal validateManualPrice(String symbol, LocalDate tradeDate,
    BigDecimal manualPrice) {
    StockPriceDto ohlc = fetchAvailable(symbol, tradeDate, MANUAL_VALIDATION);
    BigDecimal lowPrice = ohlc.lowPrice();
    BigDecimal highPrice = ohlc.highPrice();
    if (lowPrice == null || highPrice == null) {
      throw failure(symbol, MANUAL_VALIDATION, "High/Low price data is null",
        TradeResponse.MARKET_DATA_SERVICE_ERROR);
    }
    if (manualPrice.compareTo(lowPrice) < 0 || manualPrice.compareTo(highPrice) > 0) {
      throw failure(symbol, MANUAL_VALIDATION, String.format("입력 가격이 범위를 벗어남: 입력=%s, 범위=%s~%s",
        manualPrice, lowPrice, highPrice), TradeResponse.MANUAL_PRICE_OUT_OF_RANGE);
    }

    tradeLogger.logMarketDataRequest(symbol, MANUAL_VALIDATION, true, null);
    log.info("직접입력 가격 검증 성공: symbol={}, date={}, price={}, 범위={}~{}",
      symbol, tradeDate, manualPrice, lowPrice, highPrice);
    return manualPrice;
  }

  // 그 날짜 시세를 받아 쓸 수 있는 것만(없으면 시세 없음, 조회 실패는 시세 서비스 오류)
  private StockPriceDto fetchAvailable(String symbol, LocalDate tradeDate, String label) {
    log.info("시장 데이터 조회 시도: symbol={}, date={}, 용도={}", symbol, tradeDate, label);
    StockPriceDto response;
    try {
      response = marketServiceClientWrapper.getOHLCPrice(symbol, tradeDate.toString());
    } catch (TradeException e) {
      throw e;
    } catch (RuntimeException e) {
      log.error("시장 데이터 조회 실패: symbol={}, date={}, 용도={}, error={}",
        symbol, tradeDate, label, e.getMessage());
      throw failure(symbol, label, e.getMessage(), TradeResponse.MARKET_DATA_SERVICE_ERROR);
    }
    if (response == null || !response.available()) {
      throw failure(symbol, label, "Market data not available", TradeResponse.PRICE_DATA_UNAVAILABLE);
    }
    return response;
  }

  // 실패 로그를 남기고 돌려줄 예외
  private TradeException failure(String symbol, String label, String reason, TradeResponse response) {
    tradeLogger.logMarketDataRequest(symbol, label, false, reason);
    return new TradeException(response);
  }
}
