package com.muscat.user.infra.client;

import com.muscat.commonlib.dto.FxRateDto;
import com.muscat.commonlib.dto.FxRateRangeDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resilience4j Wrapper Circuit Breaker: 연속된 실패 시 호출 차단 Retry: 일시적 실패 시 재시도 Time Limiter: 타임아웃 설정
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataServiceClientWrapper {

  private final MarketDataServiceClient marketDataServiceClient;
  private static final BigDecimal DEFAULT_FX_RATE = BigDecimal.valueOf(1300); // 기본 환율

  @CircuitBreaker(name = "marketDataService", fallbackMethod = "getFxRateFallback")
  @Retry(name = "marketDataService")
  public FxRateDto getFxRate(String date) {
    log.debug("Calling market-data-service getFxRate for date: {}", date);
    return marketDataServiceClient.getFxRate(date);
  }

  @CircuitBreaker(name = "marketDataService", fallbackMethod = "getLatestFxRateFallback")
  @Retry(name = "marketDataService")
  public FxRateDto getLatestFxRate() {
    log.debug("Calling market-data-service getLatestFxRate");
    return marketDataServiceClient.getLatestFxRate();
  }

  @CircuitBreaker(name = "marketDataService", fallbackMethod = "getFxRateRangeFallback")
  @Retry(name = "marketDataService")
  public FxRateRangeDto getFxRateRange() {
    log.debug("Calling market-data-service getFxRateRange");
    return marketDataServiceClient.getFxRateRange();
  }

  // Fallback 메서드 - 캐시된 기본 환율 반환
  private FxRateDto getFxRateFallback(String date, Exception ex) {
    log.warn("Fallback triggered for getFxRate. Date: {}, Error: {}. Using default FX rate: {}",
      date, ex.getMessage(), DEFAULT_FX_RATE);
    return new FxRateDto(LocalDate.parse(date), DEFAULT_FX_RATE, "FALLBACK");
  }

  // 범위를 못 받으면 null. 쓰는 쪽이 설정의 대체 범위로 검사한다
  private FxRateRangeDto getFxRateRangeFallback(Exception ex) {
    log.warn("Fallback triggered for getFxRateRange. Error: {}", ex.getMessage());
    return null;
  }

  private FxRateDto getLatestFxRateFallback(Exception ex) {
    log.warn("Fallback triggered for getLatestFxRate. Error: {}. Using default FX rate: {}",
      ex.getMessage(), DEFAULT_FX_RATE);
    return new FxRateDto(LocalDate.now(), DEFAULT_FX_RATE, "FALLBACK");
  }
}
