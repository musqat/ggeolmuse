package com.muscat.user.infra.client;

import com.muscat.commonlib.dto.FxRateDto;
import com.muscat.commonlib.dto.FxRateRangeDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// Resilience4j 서킷브레이커 · 재시도 Wrapper(대체 동작은 재시도가 다 실패한 뒤)
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataServiceClientWrapper {

  private final MarketDataServiceClient marketDataServiceClient;

  @CircuitBreaker(name = "marketDataService")
  @Retry(name = "marketDataService", fallbackMethod = "getLatestFxRateFallback")
  public FxRateDto getLatestFxRate() {
    log.debug("Calling market-data-service getLatestFxRate");
    return marketDataServiceClient.getLatestFxRate();
  }

  @CircuitBreaker(name = "marketDataService")
  @Retry(name = "marketDataService", fallbackMethod = "getFxRateRangeFallback")
  public FxRateRangeDto getFxRateRange() {
    log.debug("Calling market-data-service getFxRateRange");
    return marketDataServiceClient.getFxRateRange();
  }

  // 범위를 못 받으면 null(쓰는 쪽이 설정의 대체 범위로 검사)
  private FxRateRangeDto getFxRateRangeFallback(Exception ex) {
    log.warn("Fallback triggered for getFxRateRange. Error: {}", ex.getMessage());
    return null;
  }

  // 최신 환율을 못 받으면 null
  private FxRateDto getLatestFxRateFallback(Exception ex) {
    log.warn("Fallback triggered for getLatestFxRate. Error: {}", ex.getMessage());
    return null;
  }
}
