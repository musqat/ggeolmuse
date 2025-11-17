package com.muscat.backtest.infra.client;

import com.muscat.backtest.infra.client.dto.DividendDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resilience4j 패턴 적용
 * Wrapper Circuit Breaker: 연속된 실패 시 호출 차단
 * Retry: 일시적 실패 시 재시도
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataClientWrapper {

  private final MarketDataClient marketDataClient;

  @CircuitBreaker(name = "marketDataService", fallbackMethod = "getOHLCPriceFallback")
  @Retry(name = "marketDataService")
  public OHLCPriceDto getOHLCPrice(String symbol, String date) {
    log.debug("Calling market-data-service getOHLCPrice for symbol: {}, date: {}", symbol, date);
    return marketDataClient.getOHLCPrice(symbol, date);
  }

  @CircuitBreaker(name = "marketDataService", fallbackMethod = "getOHLCPriceRangeFallback")
  @Retry(name = "marketDataService")
  public List<OHLCPriceDto> getOHLCPriceRange(String symbol, String startDate, String endDate) {
    log.debug("Calling market-data-service getOHLCPriceRange for symbol: {}, period: {} to {}",
      symbol, startDate, endDate);
    return marketDataClient.getOHLCPriceRange(symbol, startDate, endDate);
  }

  @CircuitBreaker(name = "marketDataService", fallbackMethod = "getCurrentPriceFallback")
  @Retry(name = "marketDataService")
  public StockPriceDto getCurrentPrice(String symbol) {
    log.debug("Calling market-data-service getCurrentPrice for symbol: {}", symbol);
    return marketDataClient.getCurrentPrice(symbol);
  }

  @CircuitBreaker(name = "marketDataService", fallbackMethod = "getDividendHistoryFallback")
  @Retry(name = "marketDataService")
  public List<DividendDto> getDividendHistory(String symbol, String startDate, String endDate) {
    log.debug("Calling market-data-service getDividendHistory for symbol: {}, period: {} to {}",
      symbol, startDate, endDate);
    return marketDataClient.getDividendHistory(symbol, startDate, endDate);
  }

  @CircuitBreaker(name = "marketDataService", fallbackMethod = "getFxRateFallback")
  @Retry(name = "marketDataService")
  public MarketDataClient.FxRate getFxRate(String date) {
    log.debug("Calling market-data-service getFxRate for date: {}", date);
    return marketDataClient.getFxRate(date);
  }

  @CircuitBreaker(name = "marketDataService", fallbackMethod = "getLatestFxRateFallback")
  @Retry(name = "marketDataService")
  public MarketDataClient.FxRate getLatestFxRate() {
    log.debug("Calling market-data-service getLatestFxRate");
    return marketDataClient.getLatestFxRate();
  }

  // Fallback 메서드
  private OHLCPriceDto getOHLCPriceFallback(String symbol, String date, Exception ex) {
    log.error("Fallback triggered for getOHLCPrice. Symbol: {}, Date: {}, Error: {}",
      symbol, date, ex.getMessage());
    throw new RuntimeException(
      "Market data service is currently unavailable. Cannot retrieve price data.", ex);
  }

  private List<OHLCPriceDto> getOHLCPriceRangeFallback(String symbol, String startDate,
    String endDate, Exception ex) {
    log.error("Fallback triggered for getOHLCPriceRange. Symbol: {}, Error: {}",
      symbol, ex.getMessage());
    return Collections.emptyList();
  }

  private StockPriceDto getCurrentPriceFallback(String symbol, Exception ex) {
    log.error("Fallback triggered for getCurrentPrice. Symbol: {}, Error: {}",
      symbol, ex.getMessage());
    throw new RuntimeException("Market data service is currently unavailable for symbol: " + symbol,
      ex);
  }

  private List<DividendDto> getDividendHistoryFallback(String symbol, String startDate,
    String endDate, Exception ex) {
    log.error("Fallback triggered for getDividendHistory. Symbol: {}, Error: {}",
      symbol, ex.getMessage());
    return Collections.emptyList();
  }

  private MarketDataClient.FxRate getFxRateFallback(String date, Exception ex) {
    log.error("Fallback triggered for getFxRate. Date: {}, Error: {}. Using default FX rate: 1300",
      date, ex.getMessage());
    return new MarketDataClient.FxRate(java.time.LocalDate.parse(date),
      java.math.BigDecimal.valueOf(1300));
  }

  private MarketDataClient.FxRate getLatestFxRateFallback(Exception ex) {
    log.error("Fallback triggered for getLatestFxRate. Error: {}. Using default FX rate: 1300",
      ex.getMessage());
    return new MarketDataClient.FxRate(java.time.LocalDate.now(),
      java.math.BigDecimal.valueOf(1300));
  }
}
