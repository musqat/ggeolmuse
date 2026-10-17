package com.muscat.trade.infra.client;

import com.muscat.commonlib.dto.StockPriceDto;
import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.infra.client.dto.DividendDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// Resilience4j 패턴 적용 Wrapper(서킷브레이커 · 재시도)
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketServiceClientWrapper {

  private final MarketServiceClient marketServiceClient;

  @CircuitBreaker(name = "marketService")
  @Retry(name = "marketService", fallbackMethod = "getCurrentPriceFallback")
  public StockPriceDto getCurrentPrice(String symbol) {
    log.debug("Calling market-data-service getCurrentPrice for symbol: {}", symbol);
    return marketServiceClient.getCurrentPrice(symbol);
  }

  @CircuitBreaker(name = "marketService")
  @Retry(name = "marketService", fallbackMethod = "getCurrentPricesFallback")
  public Map<String, StockPriceDto> getCurrentPrices(List<String> symbols) {
    log.debug("Calling market-data-service getCurrentPrices for symbols: {}", symbols);
    return marketServiceClient.getCurrentPrices(symbols);
  }

  @CircuitBreaker(name = "marketService")
  @Retry(name = "marketService", fallbackMethod = "getOHLCPriceFallback")
  public StockPriceDto getOHLCPrice(String symbol, String date) {
    log.debug("Calling market-data-service getOHLCPrice for symbol: {}, date: {}", symbol, date);
    return marketServiceClient.getOHLCPrice(symbol, date);
  }

  @CircuitBreaker(name = "marketService")
  @Retry(name = "marketService", fallbackMethod = "getHistoricalPricesFallback")
  public List<StockPriceDto> getHistoricalPrices(String symbol, String startDate, String endDate) {
    log.debug("Calling market-data-service getHistoricalPrices for symbol: {}, period: {} to {}",
      symbol, startDate, endDate);
    return marketServiceClient.getHistoricalPrices(symbol, startDate, endDate);
  }

  @CircuitBreaker(name = "marketService")
  @Retry(name = "marketService", fallbackMethod = "getDividendsFallback")
  public List<DividendDto> getDividends(String symbol, String startDate, String endDate) {
    log.debug("Calling market-data-service getDividends for symbol: {}", symbol);
    return marketServiceClient.getDividends(symbol, startDate, endDate);
  }

  // Fallback 메서드
  private StockPriceDto getCurrentPriceFallback(String symbol, Exception ex) {
    log.error("Fallback triggered for getCurrentPrice. Symbol: {}, Error: {}",
      symbol, ex.getMessage());
    throw new RuntimeException("Market data service is currently unavailable for symbol: " + symbol,
      ex);
  }

  private Map<String, StockPriceDto> getCurrentPricesFallback(List<String> symbols, Exception ex) {
    log.error("Fallback triggered for getCurrentPrices. Symbols: {}, Error: {}",
      symbols, ex.getMessage());
    return Collections.emptyMap();
  }

  private StockPriceDto getOHLCPriceFallback(String symbol, String date, Exception ex) {
    log.error("Fallback triggered for getOHLCPrice. Symbol: {}, Date: {}, Error: {}",
      symbol, date, ex.getMessage());
    throw new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR, ex);
  }

  private List<StockPriceDto> getHistoricalPricesFallback(String symbol, String startDate,
    String endDate, Exception ex) {
    log.error("Fallback triggered for getHistoricalPrices. Symbol: {}, Error: {}",
      symbol, ex.getMessage());
    return Collections.emptyList();
  }

  private List<DividendDto> getDividendsFallback(String symbol, String startDate,
    String endDate, Exception ex) {
    log.error("Fallback triggered for getDividends. Symbol: {}, Error: {}",
      symbol, ex.getMessage());
    return Collections.emptyList();
  }
}
