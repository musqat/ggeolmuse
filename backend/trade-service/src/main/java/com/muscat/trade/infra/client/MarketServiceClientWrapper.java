package com.muscat.trade.infra.client;

import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.infra.client.dto.DividendDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
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
  @Retry(name = "marketService", fallbackMethod = "getDividendsFallback")
  public List<DividendDto> getDividends(String symbol, String startDate, String endDate) {
    log.debug("Calling market-data-service getDividends for symbol: {}", symbol);
    return marketServiceClient.getDividends(symbol, startDate, endDate);
  }

  @CircuitBreaker(name = "marketService")
  @Retry(name = "marketService", fallbackMethod = "getOHLCPriceRangeFallback")
  public List<OHLCPriceDto> getOHLCPriceRange(String symbol, String startDate, String endDate) {
    log.debug("Calling market-data-service getOHLCPriceRange for symbol: {}, {} ~ {}", symbol, startDate, endDate);
    return marketServiceClient.getOHLCPriceRange(symbol, startDate, endDate);
  }

  @CircuitBreaker(name = "marketService")
  @Retry(name = "marketService", fallbackMethod = "getBulkFxRatesFallback")
  public Map<String, BigDecimal> getBulkFxRates(List<String> dates) {
    log.debug("Calling market-data-service getBulkFxRates for dates: {}", dates.size());
    return marketServiceClient.getBulkFxRates(dates);
  }

  // Fallback 메서드
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

  // 못 받으면 빈 목록 대신 오류(배당 없음과 구별)
  private List<DividendDto> getDividendsFallback(String symbol, String startDate,
    String endDate, Exception ex) {
    log.error("Fallback triggered for getDividends. Symbol: {}, Error: {}",
      symbol, ex.getMessage());
    throw new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR, ex);
  }

  // 기간 시세 · 여러 날짜 환율도 못 받으면 빈 값 대신 오류(값 없음과 구별)
  private List<OHLCPriceDto> getOHLCPriceRangeFallback(String symbol, String startDate,
    String endDate, Exception ex) {
    log.error("Fallback triggered for getOHLCPriceRange. Symbol: {}, Error: {}",
      symbol, ex.getMessage());
    throw new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR, ex);
  }

  private Map<String, BigDecimal> getBulkFxRatesFallback(List<String> dates, Exception ex) {
    log.error("Fallback triggered for getBulkFxRates. Dates: {}, Error: {}",
      dates.size(), ex.getMessage());
    throw new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR, ex);
  }
}
