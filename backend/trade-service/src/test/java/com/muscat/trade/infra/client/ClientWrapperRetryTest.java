package com.muscat.trade.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.commonlib.dto.StockPriceDto;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import com.muscat.trade.infra.client.dto.DividendDto;
import feign.FeignException;
import feign.Request;
import feign.Response;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import java.math.BigDecimal;
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

// 애노테이션이 실제로 걸린 래퍼로 봄. 재시도 예외 목록은 설정 리포의 marketService · userService 와 같게
// 대체 동작을 @CircuitBreaker 에 두면 첫 실패에 대체 동작이 돌아 바깥 @Retry 가 안 걸림
@SpringBootTest(classes = {MarketServiceClientWrapper.class, UserServiceClientWrapper.class}, properties = {
  "resilience4j.retry.instances.marketService.maxAttempts=3",
  "resilience4j.retry.instances.marketService.waitDuration=10ms",
  "resilience4j.retry.instances.marketService.retryExceptions[0]=feign.FeignException$FeignServerException",
  "resilience4j.retry.instances.marketService.retryExceptions[1]=feign.RetryableException",
  "resilience4j.retry.instances.userService.maxAttempts=3",
  "resilience4j.retry.instances.userService.waitDuration=10ms",
  "resilience4j.retry.instances.userService.retryExceptions[0]=feign.FeignException$FeignServerException",
  "resilience4j.retry.instances.userService.retryExceptions[1]=feign.RetryableException"
})
@ImportAutoConfiguration({AopAutoConfiguration.class, CircuitBreakerAutoConfiguration.class,
  RetryAutoConfiguration.class})
@ActiveProfiles("test")
@DisplayName("서비스 호출 래퍼의 재시도")
class ClientWrapperRetryTest {

  @MockitoBean
  private MarketServiceClient marketClient;

  @MockitoBean
  private UserServiceClient userClient;

  @Autowired
  private MarketServiceClientWrapper marketWrapper;

  @Autowired
  private UserServiceClientWrapper userWrapper;

  record Call(String name, Function<MarketServiceClient, Object> client,
    Function<MarketServiceClientWrapper, Object> wrapper, Object value) {

    @Override
    public String toString() {
      return name;
    }
  }

  static Stream<Call> marketCalls() {
    return Stream.of(
      new Call("날짜 시세", c -> c.getOHLCPrice("AAPL", "2024-09-16"),
        w -> w.getOHLCPrice("AAPL", "2024-09-16"), mock(StockPriceDto.class)),
      new Call("현재가", c -> c.getCurrentPrice("AAPL"), w -> w.getCurrentPrice("AAPL"), mock(StockPriceDto.class)),
      new Call("여러 종목 현재가", c -> c.getCurrentPrices(List.of("AAPL")),
        w -> w.getCurrentPrices(List.of("AAPL")), Map.of("AAPL", mock(StockPriceDto.class))),
      new Call("기간 시세", c -> c.getHistoricalPrices("AAPL", "2024-01-01", "2024-12-31"),
        w -> w.getHistoricalPrices("AAPL", "2024-01-01", "2024-12-31"), List.of(mock(StockPriceDto.class))),
      new Call("배당", c -> c.getDividends("AAPL", "2024-01-01", "2024-12-31"),
        w -> w.getDividends("AAPL", "2024-01-01", "2024-12-31"),
        List.of(new DividendDto("2024-09-16", new BigDecimal("0.25")))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("marketCalls")
  @DisplayName("시세 서비스가 5xx 로 두 번 실패해도 다시 불러 받은 값을 돌려준다")
  void marketCall_RetriesServerError(Call call) {
    given(call.client().apply(marketClient)).willThrow(serverError(), serverError()).willReturn(call.value());

    assertThat(call.wrapper().apply(marketWrapper)).isSameAs(call.value());
    call.client().apply(verify(marketClient, times(3)));
  }

  @Test
  @DisplayName("날짜 시세가 세 번 다 5xx 면 시장 데이터 오류 코드로 올린다")
  void ohlcPrice_ServerErrorEveryTime_MarketDataError() {
    FeignException last = serverError();
    given(marketClient.getOHLCPrice("AAPL", "2024-09-16")).willThrow(serverError(), serverError(), last);

    assertThatThrownBy(() -> marketWrapper.getOHLCPrice("AAPL", "2024-09-16"))
      .isInstanceOf(TradeException.class)
      .hasFieldOrPropertyWithValue("errorCode", "MARKET_DATA_SERVICE_ERROR")
      .hasCause(last);
    verify(marketClient, times(3)).getOHLCPrice("AAPL", "2024-09-16");
  }

  @Test
  @DisplayName("배당 목록이 세 번 다 5xx 면 빈 목록이 아니라 시장 데이터 오류 코드로 올린다")
  void dividends_ServerErrorEveryTime_MarketDataError() {
    FeignException last = serverError();
    given(marketClient.getDividends("AAPL", "2024-01-01", "2024-12-31")).willThrow(serverError(), serverError(), last);

    assertThatThrownBy(() -> marketWrapper.getDividends("AAPL", "2024-01-01", "2024-12-31"))
      .isInstanceOf(TradeException.class)
      .hasFieldOrPropertyWithValue("errorCode", "MARKET_DATA_SERVICE_ERROR")
      .hasCause(last);
    verify(marketClient, times(3)).getDividends("AAPL", "2024-01-01", "2024-12-31");
  }

  @Test
  @DisplayName("계좌 잔액이 5xx 로 두 번 실패해도 다시 불러 받은 값을 돌려준다")
  void accountBalance_RetriesServerError() {
    AccountBalanceDto balance = mock(AccountBalanceDto.class);
    given(userClient.getAccountBalance(20L)).willThrow(serverError(), serverError()).willReturn(balance);

    assertThat(userWrapper.getAccountBalance(20L)).isSameAs(balance);
    verify(userClient, times(3)).getAccountBalance(20L);
  }

  private static FeignException serverError() {
    Request request = Request.create(Request.HttpMethod.GET, "/api/internal", Map.of(), null,
      StandardCharsets.UTF_8, null);
    return FeignException.errorStatus("ServiceClient#call",
      Response.builder().status(503).reason("Service Unavailable").request(request).headers(Map.of()).build());
  }
}
