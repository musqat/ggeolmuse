package com.muscat.trade.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import java.math.BigDecimal;
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
  @DisplayName("잔액을 못 받으면 대체값 없이 원인을 담은 예외를 올린다(잔액 모르고 주문하지 않는다)")
  void fallback_Throws() {
    RuntimeException down = new RuntimeException("down");

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(wrapper, "getAccountBalanceFallback", 20L, down))
      .isInstanceOf(RuntimeException.class)
      .hasMessageContaining("User service")
      .hasCause(down);
  }
}
