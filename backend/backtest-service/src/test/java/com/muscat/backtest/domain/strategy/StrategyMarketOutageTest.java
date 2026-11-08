package com.muscat.backtest.domain.strategy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import feign.FeignException;
import feign.Request;
import feign.Response;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 전략이 서킷 브레이커 · 재시도가 걸린 래퍼로 시세를 받는지(재시도 예외 목록은 설정 리포와 같게)
@SpringBootTest(classes = {MarketDataClientWrapper.class, DCAStrategy.class, ConditionalPurchaseStrategy.class,
  ResponseMapper.class}, properties = {
  "resilience4j.retry.instances.marketDataService.maxAttempts=2",
  "resilience4j.retry.instances.marketDataService.waitDuration=10ms",
  "resilience4j.retry.instances.marketDataService.retryExceptions[0]=feign.FeignException$FeignServerException",
  "resilience4j.retry.instances.marketDataService.retryExceptions[1]=feign.RetryableException"
})
@ImportAutoConfiguration({AopAutoConfiguration.class, CircuitBreakerAutoConfiguration.class,
  RetryAutoConfiguration.class, TimeLimiterAutoConfiguration.class})
@ActiveProfiles("test")
@DisplayName("시세 서비스가 안 되면 전략은 서비스 불가로 응답한다")
class StrategyMarketOutageTest {

  @MockitoBean
  private MarketDataClient marketDataClient;

  @Autowired
  private DCAStrategy dcaStrategy;

  @Autowired
  private ConditionalPurchaseStrategy conditionalStrategy;

  private static FeignException outage() {
    Request request = Request.create(Request.HttpMethod.GET, "/api/internal/market", Map.of(), null,
      StandardCharsets.UTF_8, null);
    return FeignException.errorStatus("MarketDataClient#call",
      Response.builder().status(503).reason("Service Unavailable").request(request).headers(Map.of()).build());
  }

  @Test
  @DisplayName("적립식은 기간 시세를 못 받으면 서비스 불가다")
  void dca_RangeOutage_Unavailable() {
    given(marketDataClient.getOHLCPriceRange(anyString(), anyString(), anyString())).willThrow(outage());

    assertThatThrownBy(() -> dcaStrategy.execute(DcaStrategyRequest.builder()
      .symbol("AAPL").startDate(LocalDate.of(2024, 1, 1)).endDate(LocalDate.of(2024, 3, 31))
      .monthlyAmount(new BigDecimal("100000")).purchaseDay(15).build()))
      .isInstanceOf(BacktestException.class)
      .extracting("code").isEqualTo(BacktestResponse.MARKET_DATA_UNAVAILABLE);
  }

  @Test
  @DisplayName("조건부 매수는 기간 시세를 못 받으면 서비스 불가다")
  void conditional_RangeOutage_Unavailable() {
    given(marketDataClient.getOHLCPriceRange(anyString(), anyString(), anyString())).willThrow(outage());

    assertThatThrownBy(() -> conditionalStrategy.execute(ConditionalStrategyRequest.builder()
      .symbol("AAPL").startDate(LocalDate.of(2024, 1, 1)).endDate(LocalDate.of(2024, 3, 31))
      .amountPerPurchase(new BigDecimal("100000")).maxPurchases(3)
      .dropPercentage(new BigDecimal("0.05")).build()))
      .isInstanceOf(BacktestException.class)
      .extracting("code").isEqualTo(BacktestResponse.MARKET_DATA_UNAVAILABLE);
  }
}
