package com.muscat.trade.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import feign.FeignException;
import feign.Request;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("사용자 서비스 잔액 호출과 대체 동작")
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
  @DisplayName("잔액을 못 받으면 대체값 없이 원인을 담아 사용자 서비스 오류로 올린다(잔액 모르고 주문하지 않는다)")
  void fallback_Throws() {
    RuntimeException down = new RuntimeException("down");

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getAccountBalanceFallback", 20L, down))
      .isInstanceOf(TradeException.class)
      .hasMessage(TradeResponse.USER_SERVICE_ERROR.getMessage())
      .hasCause(down);
  }

  @Test
  @DisplayName("4xx 는 장애가 아니라 그대로 올린다. 계좌 없음 · 접근 거부를 서비스 불가로 바꾸지 않는다")
  void fallback_ClientError_Rethrown() {
    Request request = Request.create(Request.HttpMethod.GET, "/api/accounts/20/balance", Map.of(), null,
      StandardCharsets.UTF_8, null);
    FeignException.NotFound notFound = new FeignException.NotFound("Not Found", request, null, Map.of());

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getAccountBalanceFallback", 20L, notFound))
      .isSameAs(notFound);
  }
}
