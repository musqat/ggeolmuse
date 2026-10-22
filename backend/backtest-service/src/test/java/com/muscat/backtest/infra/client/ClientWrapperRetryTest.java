package com.muscat.backtest.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.infra.client.dto.DividendDto;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;
import feign.FeignException;
import feign.Request;
import feign.Response;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 애노테이션이 실제로 걸린 래퍼로 봄. 재시도 예외 목록은 설정 리포의 marketDataService 와 같게
// 대체 동작을 @CircuitBreaker 에 두면 첫 실패에 대체 동작이 돌아 바깥 @Retry 가 안 걸림
@SpringBootTest(classes = MarketDataClientWrapper.class, properties = {
  "resilience4j.retry.instances.marketDataService.maxAttempts=3",
  "resilience4j.retry.instances.marketDataService.waitDuration=10ms",
  "resilience4j.retry.instances.marketDataService.retryExceptions[0]=feign.FeignException$FeignServerException",
  "resilience4j.retry.instances.marketDataService.retryExceptions[1]=feign.RetryableException"
})
@ImportAutoConfiguration({AopAutoConfiguration.class, CircuitBreakerAutoConfiguration.class,
  RetryAutoConfiguration.class, TimeLimiterAutoConfiguration.class})
@ActiveProfiles("test")
@DisplayName("서비스 호출 래퍼의 재시도")
class ClientWrapperRetryTest {

  @MockitoBean
  private MarketDataClient marketClient;

  @Autowired
  private MarketDataClientWrapper marketWrapper;

  record Call<C, W>(String name, Function<C, Object> client, Function<W, Object> wrapper, Object value) {

    @Override
    public String toString() {
      return name;
    }
  }

  static Stream<Call<MarketDataClient, MarketDataClientWrapper>> marketCalls() {
    return Stream.of(
      new Call<>("날짜 시세", c -> c.getOHLCPrice("AAPL", "2024-09-16"),
        w -> w.getOHLCPrice("AAPL", "2024-09-16"), mock(OHLCPriceDto.class)),
      new Call<>("기간 시세", c -> c.getOHLCPriceRange("AAPL", "2024-01-01", "2024-12-31"),
        w -> w.getOHLCPriceRange("AAPL", "2024-01-01", "2024-12-31"), List.of(mock(OHLCPriceDto.class))),
      new Call<>("현재가", c -> c.getCurrentPrice("AAPL"), w -> w.getCurrentPrice("AAPL"), mock(StockPriceDto.class)),
      new Call<>("배당", c -> c.getDividendHistory("AAPL", "2024-01-01", "2024-12-31"),
        w -> w.getDividendHistory("AAPL", "2024-01-01", "2024-12-31"), List.of(mock(DividendDto.class))),
      new Call<>("날짜 환율", c -> c.getFxRate("2024-09-16"), w -> w.getFxRate("2024-09-16"), mock(FxRateDto.class)),
      new Call<>("최신 환율", MarketDataClient::getLatestFxRate, MarketDataClientWrapper::getLatestFxRate,
        mock(FxRateDto.class)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("marketCalls")
  @DisplayName("시세 서비스가 5xx 로 두 번 실패해도 다시 불러 받은 값을 돌려준다")
  void marketCall_RetriesServerError(Call<MarketDataClient, MarketDataClientWrapper> call) {
    given(call.client().apply(marketClient)).willThrow(serverError(), serverError()).willReturn(call.value());

    assertThat(call.wrapper().apply(marketWrapper)).isSameAs(call.value());
    call.client().apply(verify(marketClient, times(3)));
  }

  @Test
  @DisplayName("날짜 시세가 세 번 다 5xx 면 시세 서비스 사용 불가 코드에 원인을 담아 올린다")
  void ohlcPrice_ServerErrorEveryTime_Unavailable() {
    FeignException last = serverError();
    given(marketClient.getOHLCPrice("AAPL", "2024-09-16")).willThrow(serverError(), serverError(), last);

    assertThatThrownBy(() -> marketWrapper.getOHLCPrice("AAPL", "2024-09-16"))
      .isInstanceOf(BacktestException.class)
      .hasFieldOrPropertyWithValue("errorCode", "MARKET_DATA_UNAVAILABLE")
      .hasCause(last);
    verify(marketClient, times(3)).getOHLCPrice("AAPL", "2024-09-16");
  }

  private static FeignException serverError() {
    Request request = Request.create(Request.HttpMethod.GET, "/api/internal", Map.of(), null,
      StandardCharsets.UTF_8, null);
    return FeignException.errorStatus("ServiceClient#call",
      Response.builder().status(503).reason("Service Unavailable").request(request).headers(Map.of()).build());
  }
}
