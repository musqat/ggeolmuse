package com.muscat.user.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.commonlib.dto.FxRateDto;
import com.muscat.commonlib.dto.FxRateRangeDto;
import feign.FeignException;
import feign.Request;
import feign.Response;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import java.nio.charset.StandardCharsets;
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
@SpringBootTest(classes = MarketDataServiceClientWrapper.class, properties = {
  "resilience4j.retry.instances.marketDataService.maxAttempts=3",
  "resilience4j.retry.instances.marketDataService.waitDuration=10ms",
  "resilience4j.retry.instances.marketDataService.retryExceptions[0]=feign.FeignException$FeignServerException",
  "resilience4j.retry.instances.marketDataService.retryExceptions[1]=feign.RetryableException"
})
@ImportAutoConfiguration({AopAutoConfiguration.class, CircuitBreakerAutoConfiguration.class,
  RetryAutoConfiguration.class})
@ActiveProfiles("test")
@DisplayName("시세 서비스 환율 호출 래퍼의 재시도")
class ClientWrapperRetryTest {

  @MockitoBean
  private MarketDataServiceClient client;

  @Autowired
  private MarketDataServiceClientWrapper wrapper;

  record Call(String name, Function<MarketDataServiceClient, Object> client,
    Function<MarketDataServiceClientWrapper, Object> wrapper, Object value) {

    @Override
    public String toString() {
      return name;
    }
  }

  static Stream<Call> calls() {
    return Stream.of(
      new Call("최신 환율", MarketDataServiceClient::getLatestFxRate, MarketDataServiceClientWrapper::getLatestFxRate,
        mock(FxRateDto.class)),
      new Call("환율 범위", MarketDataServiceClient::getFxRateRange, MarketDataServiceClientWrapper::getFxRateRange,
        mock(FxRateRangeDto.class)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("calls")
  @DisplayName("시세 서비스가 5xx 로 두 번 실패해도 다시 불러 받은 값을 돌려준다")
  void retriesServerError(Call call) {
    given(call.client().apply(client)).willThrow(serverError(), serverError()).willReturn(call.value());

    assertThat(call.wrapper().apply(wrapper)).isSameAs(call.value());
    call.client().apply(verify(client, times(3)));
  }

  @Test
  @DisplayName("최신 환율이 세 번 다 5xx 면 지어낸 환율 없이 비워 둔다")
  void latestFxRate_ServerErrorEveryTime_Empty() {
    given(client.getLatestFxRate()).willThrow(serverError(), serverError(), serverError());

    assertThat(wrapper.getLatestFxRate()).isNull();
    verify(client, times(3)).getLatestFxRate();
  }

  private static FeignException serverError() {
    Request request = Request.create(Request.HttpMethod.GET, "/api/internal", Map.of(), null,
      StandardCharsets.UTF_8, null);
    return FeignException.errorStatus("ServiceClient#call",
      Response.builder().status(503).reason("Service Unavailable").request(request).headers(Map.of()).build());
  }
}
