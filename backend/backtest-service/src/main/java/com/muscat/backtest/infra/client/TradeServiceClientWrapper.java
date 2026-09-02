package com.muscat.backtest.infra.client;

import com.muscat.backtest.infra.client.dto.HoldingDto;
import com.muscat.backtest.infra.client.dto.TradeDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Resilience4j 패턴 적용 Wrapper
 * Circuit Breaker: 연속된 실패 시 호출 차단
 * Retry: 일시적 실패 시 재시도
 * Time Limiter: 타임아웃 설정
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeServiceClientWrapper {

  private final TradeServiceClient tradeServiceClient;

  @CircuitBreaker(name = "tradeService", fallbackMethod = "getPortfolioFallback")
  @Retry(name = "tradeService")
  @TimeLimiter(name = "tradeService")
  public List<HoldingDto> getPortfolio(String authorization) {
    log.debug("Calling trade-service getPortfolio");
    return tradeServiceClient.getPortfolio(authorization);
  }

  @CircuitBreaker(name = "tradeService", fallbackMethod = "getTradeHistoryBySymbolFallback")
  @Retry(name = "tradeService")
  @TimeLimiter(name = "tradeService")
  public List<TradeDto> getTradeHistoryBySymbol(String authorization, String symbol) {
    log.debug("Calling trade-service getTradeHistoryBySymbol for symbol: {}", symbol);
    return tradeServiceClient.getTradeHistoryBySymbol(authorization, symbol);
  }

  // Fallback 메서드
  private List<HoldingDto> getPortfolioFallback(String authorization, Exception ex) {
    log.error("Fallback triggered for getPortfolio. Error: {}", ex.getMessage());
    throw new RuntimeException("Trade service is currently unavailable. Cannot retrieve portfolio.", ex);
  }

  private List<TradeDto> getTradeHistoryBySymbolFallback(String authorization, String symbol, Exception ex) {
    log.error("Fallback triggered for getTradeHistoryBySymbol. Symbol: {}, Error: {}",
        symbol, ex.getMessage());
    return Collections.emptyList();
  }
}
