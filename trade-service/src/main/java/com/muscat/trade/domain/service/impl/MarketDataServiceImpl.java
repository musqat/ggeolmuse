package com.muscat.trade.domain.service.impl;

import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.common.exception.MarketDataException;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.domain.service.MarketDataService;
import com.muscat.trade.infra.client.MarketServiceClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataServiceImpl implements MarketDataService {

  private final MarketServiceClient marketServiceClient;
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
  @Retryable(
      retryFor = {Exception.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 500, multiplier = 2.0)
  )
  public BigDecimal getOHLCPrice(String symbol, LocalDate tradeDate, PriceType priceType) {
    try {
      log.info("시장 데이터 조회 시도: symbol={}, date={}, priceType={}", symbol, tradeDate, priceType);
      var response = marketServiceClient.getOHLCPrice(symbol, tradeDate.toString());
      log.info("Market data raw response: {}", response);

      if (response != null && response.getAvailable()) {
        BigDecimal price = switch (priceType) {
          case OPEN -> response.getOpenPrice();
          case HIGH -> response.getHighPrice();
          case LOW -> response.getLowPrice();
          case CLOSE -> response.getClosePrice();
          default -> throw new IllegalArgumentException("Unsupported price type: " + priceType);
        };

        if (price == null) {
          String errorMsg = "Price data is null for type: " + priceType;
          tradeLogger.logMarketDataRequest(symbol, priceType.name(), false, errorMsg);
          throw new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR);
        }

        tradeLogger.logMarketDataRequest(symbol, priceType.name(), true, null);
        log.info("시장 가격 조회 성공: symbol={}, date={}, priceType={}, price={}",
            symbol, tradeDate, priceType, price);
        return price;
      } else {
        String errorMsg = "Market data not available";
        tradeLogger.logMarketDataRequest(symbol, priceType.name(), false, errorMsg);
        throw new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR);
      }
    } catch (Exception e) {
      tradeLogger.logMarketDataRequest(symbol, priceType.name(), false, e.getMessage());
      log.error("시장 가격 조회 실패: symbol={}, date={}, priceType={}, error={}",
          symbol, tradeDate, priceType, e.getMessage());
      throw new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR);
    }
  }

  @Override
  @Retryable(
      retryFor = {Exception.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 500, multiplier = 2.0)
  )
  public BigDecimal validateManualPrice(String symbol, LocalDate tradeDate,
      BigDecimal manualPrice) {
    try {
      log.info("수동 가격 검증을 위한 시장 데이터 조회 시도: symbol={}, date={}", symbol, tradeDate);
      var response = marketServiceClient.getOHLCPrice(symbol, tradeDate.toString());

      if (response != null && response.getAvailable()) {
        BigDecimal lowPrice = response.getLowPrice();
        BigDecimal highPrice = response.getHighPrice();

        if (lowPrice == null || highPrice == null) {
          String errorMsg = "High/Low price data is null";
          tradeLogger.logMarketDataRequest(symbol, "MANUAL_VALIDATION", false, errorMsg);
          throw new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR);
        }

        if (manualPrice.compareTo(lowPrice) < 0 || manualPrice.compareTo(highPrice) > 0) {
          String errorMsg = String.format("입력 가격이 범위를 벗어남: 입력=%s, 범위=%s~%s",
              manualPrice, lowPrice, highPrice);
          tradeLogger.logMarketDataRequest(symbol, "MANUAL_VALIDATION", false, errorMsg);
          throw new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR);
        }

        tradeLogger.logMarketDataRequest(symbol, "MANUAL_VALIDATION", true, null);
        log.info("직접입력 가격 검증 성공: symbol={}, date={}, price={}, 범위={}~{}",
            symbol, tradeDate, manualPrice, lowPrice, highPrice);
        return manualPrice;
      } else {
        String errorMsg = "Market data not available";
        tradeLogger.logMarketDataRequest(symbol, "MANUAL_VALIDATION", false, errorMsg);
        throw new MarketDataException();
      }
    } catch (Exception e) {
      tradeLogger.logMarketDataRequest(symbol, "MANUAL_VALIDATION", false, e.getMessage());
      log.error("직접입력 가격 검증 실패: symbol={}, date={}, price={}, error={}",
          symbol, tradeDate, manualPrice, e.getMessage());
      throw new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR);
    }
  }
}