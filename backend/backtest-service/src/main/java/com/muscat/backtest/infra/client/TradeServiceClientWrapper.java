package com.muscat.backtest.infra.client;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.infra.client.dto.HoldingDto;
import com.muscat.backtest.infra.client.dto.TradeDto;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

// Resilience4j 패턴 적용 Wrapper(서킷브레이커 · 재시도). 대체 동작은 재시도가 다 실패한 뒤
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeServiceClientWrapper {

  private final TradeServiceClient tradeServiceClient;

  @CircuitBreaker(name = "tradeService")
  @Retry(name = "tradeService", fallbackMethod = "getPortfolioFallback")
  public List<HoldingDto> getPortfolio(String authorization) {
    log.debug("Calling trade-service getPortfolio");
    return tradeServiceClient.getPortfolio(authorization);
  }

  @CircuitBreaker(name = "tradeService")
  @Retry(name = "tradeService", fallbackMethod = "getTradeHistoryBySymbolFallback")
  public List<TradeDto> getTradeHistoryBySymbol(String authorization, String symbol) {
    log.debug("Calling trade-service getTradeHistoryBySymbol for symbol: {}", symbol);
    return tradeServiceClient.getTradeHistoryBySymbol(authorization, symbol);
  }

  // Fallback 메서드
  private List<HoldingDto> getPortfolioFallback(String authorization, Exception ex) {
    // 4xx 는 장애가 아니다. 서비스 불가로 바꾸지 않고 그대로 올린다
    if (ex instanceof FeignException.FeignClientException clientError) {
      throw clientError;
    }
    log.error("Fallback triggered for getPortfolio. Error: {}", ex.getMessage());
    throw new BacktestException(BacktestResponse.TRADE_SERVICE_UNAVAILABLE, ex);
  }

  private List<TradeDto> getTradeHistoryBySymbolFallback(String authorization, String symbol, Exception ex) {
    log.error("Fallback triggered for getTradeHistoryBySymbol. Symbol: {}, Error: {}",
        symbol, ex.getMessage());
    return Collections.emptyList();
  }
}
