package com.muscat.trade.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import com.muscat.trade.infra.client.dto.ExchangeRecordDto;
import feign.FeignException;
import feign.Request;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("사용자 서비스 잔액 · 환전 기록 호출과 대체 동작")
class UserServiceClientWrapperTest {

  private final UserServiceClient client = mock(UserServiceClient.class);
  private final UserServiceClientWrapper wrapper = new UserServiceClientWrapper(client);

  @Test
  @DisplayName("정상일 때는 사용자 서비스가 준 잔액을 그대로 돌려준다")
  void delegates() {
    AccountBalanceDto balance = AccountBalanceDto.builder().balanceUsd(new BigDecimal("12.50")).build();
    given(client.getAccountBalance(20L)).willReturn(balance);

    assertThat(wrapper.getAccountBalance(20L)).isSameAs(balance);
  }

  @Test
  @DisplayName("잔액을 못 받으면 대체값 없이 원인을 담은 사용자 서비스 오류 예외가 난다(잔액을 모르면 주문하지 않는다)")
  void fallback_Throws() {
    RuntimeException down = new RuntimeException("down");

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getAccountBalanceFallback", 20L, down))
      .isInstanceOf(TradeException.class)
      .hasMessage(TradeResponse.USER_SERVICE_ERROR.getMessage())
      .hasCause(down);
  }

  @Test
  @DisplayName("4xx 는 장애로 보지 않고 그 예외가 그대로 난다. 계좌 없음 · 접근 거부를 서비스 불가로 바꾸지 않는다")
  void fallback_ClientError_Rethrown() {
    Request request = Request.create(Request.HttpMethod.GET, "/api/accounts/20/balance", Map.of(), null,
      StandardCharsets.UTF_8, null);
    FeignException.NotFound notFound = new FeignException.NotFound("Not Found", request, null, Map.of());

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getAccountBalanceFallback", 20L, notFound))
      .isSameAs(notFound);
  }

  @Test
  @DisplayName("환전 기록은 사용자 서비스가 준 목록을 그대로 돌려준다")
  void exchangeRecords_Delegates() {
    List<ExchangeRecordDto> records = List.of(new ExchangeRecordDto(3L, LocalDateTime.of(2024, 9, 2, 10, 15, 30),
      "KRW", "USD", new BigDecimal("1300000.00"), new BigDecimal("1000.00"), new BigDecimal("1300.000000")));
    given(client.getExchangeRecords()).willReturn(records);

    assertThat(wrapper.getExchangeRecords()).isSameAs(records);
  }

  @Test
  @DisplayName("환전 기록을 못 받으면 4xx 도 원인을 담은 사용자 서비스 오류 예외가 난다")
  void exchangeRecordsFallback_AnyError_UserServiceError() {
    Request request = Request.create(Request.HttpMethod.GET, "/api/internal/accounts/exchanges", Map.of(), null,
      StandardCharsets.UTF_8, null);
    FeignException.NotFound notFound = new FeignException.NotFound("Not Found", request, null, Map.of());
    RuntimeException down = new RuntimeException("down");

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getExchangeRecordsFallback", notFound))
      .isInstanceOf(TradeException.class)
      .hasMessage(TradeResponse.USER_SERVICE_ERROR.getMessage())
      .hasCause(notFound);
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getExchangeRecordsFallback", down))
      .isInstanceOf(TradeException.class)
      .hasCause(down);
  }
}
