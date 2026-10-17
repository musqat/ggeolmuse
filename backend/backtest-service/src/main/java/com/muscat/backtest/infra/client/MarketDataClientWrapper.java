package com.muscat.backtest.infra.client;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.config.CacheConfig;
import com.muscat.backtest.infra.client.dto.DividendDto;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

// Resilience4j 패턴 적용 Wrapper(서킷브레이커 · 재시도)
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
  public FxRateDto getFxRate(String date) {
    log.debug("Calling market-data-service getFxRate for date: {}", date);
    return marketDataClient.getFxRate(date);
  }

  /**
   * 최신 환율은 요청과 무관하게 값이 하나다.
   */
  @Cacheable(cacheNames = CacheConfig.LATEST_FX_RATE, key = "'latest'")
  @CircuitBreaker(name = "marketDataService", fallbackMethod = "getLatestFxRateFallback")
  @Retry(name = "marketDataService")
  public FxRateDto getLatestFxRate() {
    log.debug("Calling market-data-service getLatestFxRate");
    return marketDataClient.getLatestFxRate();
  }

  // Fallback 메서드
  private OHLCPriceDto getOHLCPriceFallback(String symbol, String date, Exception ex) {
    rethrowClientError(ex);
    log.error("Fallback triggered for getOHLCPrice. Symbol: {}, Date: {}, Error: {}",
      symbol, date, ex.getMessage());
    throw new BacktestException(BacktestResponse.MARKET_DATA_UNAVAILABLE, ex);
  }

  private List<OHLCPriceDto> getOHLCPriceRangeFallback(String symbol, String startDate,
    String endDate, Exception ex) {
    log.error("Fallback triggered for getOHLCPriceRange. Symbol: {}, Error: {}",
      symbol, ex.getMessage());
    return Collections.emptyList();
  }

  private StockPriceDto getCurrentPriceFallback(String symbol, Exception ex) {
    rethrowClientError(ex);
    log.error("Fallback triggered for getCurrentPrice. Symbol: {}, Error: {}",
      symbol, ex.getMessage());
    throw new BacktestException(BacktestResponse.MARKET_DATA_UNAVAILABLE, ex);
  }

  // 4xx 는 장애가 아니다. 서비스 불가로 바꾸지 않고 그대로 올린다
  private static void rethrowClientError(Exception ex) {
    if (ex instanceof FeignException.FeignClientException clientError) {
      throw clientError;
    }
  }

  private List<DividendDto> getDividendHistoryFallback(String symbol, String startDate,
    String endDate, Exception ex) {
    log.error("Fallback triggered for getDividendHistory. Symbol: {}, Error: {}",
      symbol, ex.getMessage());
    return Collections.emptyList();
  }

  private FxRateDto getFxRateFallback(String date, Exception ex) {
    log.error("Fallback triggered for getFxRate. Date: {}, Error: {}. Using default FX rate: 1300",
      date, ex.getMessage());
    return new FxRateDto(LocalDate.parse(date), BigDecimal.valueOf(1300));
  }

  private FxRateDto getLatestFxRateFallback(Exception ex) {
    log.error("Fallback triggered for getLatestFxRate. Error: {}. Using default FX rate: 1300",
      ex.getMessage());
    return new FxRateDto(LocalDate.now(), BigDecimal.valueOf(1300));
  }
}
