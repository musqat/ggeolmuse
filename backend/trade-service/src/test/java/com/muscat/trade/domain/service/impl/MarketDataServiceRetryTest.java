package com.muscat.trade.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.commonlib.dto.StockPriceDto;
import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.infra.client.MarketServiceClient;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import feign.FeignException;
import feign.Request;
import feign.Response;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
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

// 거래 가격 조회를 실제 래퍼와 함께(재시도 예외 목록은 설정 리포의 marketService 와 같게)
@SpringBootTest(classes = {MarketDataServiceImpl.class, MarketServiceClientWrapper.class}, properties = {
  "resilience4j.retry.instances.marketService.maxAttempts=3",
  "resilience4j.retry.instances.marketService.waitDuration=10ms",
  "resilience4j.retry.instances.marketService.retryExceptions[0]=feign.FeignException$FeignServerException",
  "resilience4j.retry.instances.marketService.retryExceptions[1]=feign.RetryableException"
})
@ImportAutoConfiguration({AopAutoConfiguration.class, CircuitBreakerAutoConfiguration.class,
  RetryAutoConfiguration.class})
@ActiveProfiles("test")
@DisplayName("거래 가격 조회의 재시도")
class MarketDataServiceRetryTest {

  private static final LocalDate TRADE_DATE = LocalDate.of(2024, 9, 18);

  @MockitoBean
  private MarketServiceClient client;

  @MockitoBean
  private TradeLogger tradeLogger;

  @Autowired
  private MarketDataServiceImpl marketDataService;

  /** open 216 / high 222 / low 213 / close 220 */
  private static StockPriceDto ohlc() {
    return new StockPriceDto(
      "AAPL", "Apple Inc.", new BigDecimal("220"), null, null, null, null,
      TRADE_DATE, null,
      new BigDecimal("216"), new BigDecimal("222"), new BigDecimal("213"), new BigDecimal("220"),
      new BigDecimal("220"), "USD", true, "EQUITY", null);
  }

  @Test
  @DisplayName("시세 서비스가 5xx 로 두 번 실패해도 다시 불러 종가로 거래 가격을 정한다")
  void tradePrice_RetriesServerError() {
    given(client.getOHLCPrice("AAPL", "2024-09-18")).willThrow(serverError(), serverError()).willReturn(ohlc());

    BigDecimal price = marketDataService.determineTradePrice("AAPL", TRADE_DATE, PriceType.CLOSE, null);

    assertThat(price).isEqualByComparingTo("220");
    verify(client, times(3)).getOHLCPrice("AAPL", "2024-09-18");
  }

  @Test
  @DisplayName("직접 입력 가격이 그날 범위를 벗어나면 다시 부르지 않고 거절한다")
  void manualPriceOutOfRange_NotRetried() {
    given(client.getOHLCPrice("AAPL", "2024-09-18")).willReturn(ohlc());

    assertThatThrownBy(() -> marketDataService.determineTradePrice("AAPL", TRADE_DATE, PriceType.MANUAL,
      new BigDecimal("230")))
      .isInstanceOf(TradeException.class)
      .hasFieldOrPropertyWithValue("errorCode", "MANUAL_PRICE_OUT_OF_RANGE");
    verify(client, times(1)).getOHLCPrice("AAPL", "2024-09-18");
  }

  private static FeignException serverError() {
    Request request = Request.create(Request.HttpMethod.GET, "/api/internal", Map.of(), null,
      StandardCharsets.UTF_8, null);
    return FeignException.errorStatus("MarketServiceClient#getOHLCPrice",
      Response.builder().status(503).reason("Service Unavailable").request(request).headers(Map.of()).build());
  }
}
