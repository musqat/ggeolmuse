package com.muscat.trade.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.config.TradeProperties;
import com.muscat.trade.infra.client.UserServiceClientWrapper;
import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import feign.FeignException;
import feign.Request;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("거래 공통 계산 · 검증")
class TradeUtilsTest {

  private static final BigDecimal TRADE_AMOUNT = new BigDecimal("89.80");

  private final UserServiceClientWrapper userServiceClientWrapper = mock(UserServiceClientWrapper.class);
  private final TradeLogger tradeLogger = mock(TradeLogger.class);
  private final TradeUtils tradeUtils = new TradeUtils(userServiceClientWrapper, tradeLogger, new TradeProperties());

  private static AccountBalanceDto balanceWithRate(BigDecimal commissionRate) {
    return AccountBalanceDto.builder().commissionRate(commissionRate).build();
  }

  @Nested
  @DisplayName("수수료")
  class Fee {

    @Test
    @DisplayName("계좌 수수료율로 계산한다")
    void calculateFee_AccountRate_UsesAccountRate() {
      BigDecimal fee = tradeUtils.calculateFee(balanceWithRate(new BigDecimal("0.001")), TRADE_AMOUNT);

      assertThat(fee).isEqualByComparingTo("0.09");
    }

    @Test
    @DisplayName("계좌 수수료율이 0 이면 수수료도 0")
    void calculateFee_ZeroRate_ReturnsZero() {
      assertThat(tradeUtils.calculateFee(balanceWithRate(BigDecimal.ZERO), TRADE_AMOUNT)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("계좌 수수료율이 없으면 기본 0.25% 를 쓴다")
    void calculateFee_NullRate_UsesDefaultRate() {
      assertThat(tradeUtils.calculateFee(balanceWithRate(null), TRADE_AMOUNT)).isEqualByComparingTo("0.22");
    }

    @Test
    @DisplayName("계좌 수수료율이 음수면 기본 0.25% 를 쓴다")
    void calculateFee_NegativeRate_UsesDefaultRate() {
      assertThat(tradeUtils.calculateFee(balanceWithRate(new BigDecimal("-0.1")), TRADE_AMOUNT)).isEqualByComparingTo("0.22");
    }
  }

  @Nested
  @DisplayName("계좌 잔액 조회")
  class AccountBalance {

    @Test
    @DisplayName("사용자 서비스가 준 잔액을 돌려준다")
    void returnsBalance() {
      AccountBalanceDto balance = AccountBalanceDto.builder().balanceUsd(new BigDecimal("12.50")).build();
      given(userServiceClientWrapper.getAccountBalance(20L)).willReturn(balance);

      assertThat(tradeUtils.getAccountBalance("20")).isSameAs(balance);
    }

    @Test
    @DisplayName("응답이 비면 계좌 없음(404)이다. 서비스 오류로 바꾸지 않는다")
    void nullResponse_AccountNotFound() {
      given(userServiceClientWrapper.getAccountBalance(20L)).willReturn(null);

      assertThatThrownBy(() -> tradeUtils.getAccountBalance("20"))
        .isInstanceOf(TradeException.class)
        .hasMessage(TradeResponse.ACCOUNT_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("호출이 실패하면 사용자 서비스 오류(502)다")
    void clientFails_UserServiceError() {
      given(userServiceClientWrapper.getAccountBalance(20L)).willThrow(new RuntimeException("down"));

      assertThatThrownBy(() -> tradeUtils.getAccountBalance("20"))
        .isInstanceOf(TradeException.class)
        .hasMessage(TradeResponse.USER_SERVICE_ERROR.getMessage());
    }

    @Test
    @DisplayName("대체 동작이 이미 오류 코드를 붙여 올리면 다시 감싸지 않고 그대로 올린다")
    void codedError_PassedThrough() {
      TradeException coded = new TradeException(TradeResponse.USER_SERVICE_ERROR, new RuntimeException("down"));
      given(userServiceClientWrapper.getAccountBalance(20L)).willThrow(coded);

      assertThatThrownBy(() -> tradeUtils.getAccountBalance("20")).isSameAs(coded);
    }

    @Test
    @DisplayName("사용자 서비스가 404 면 서비스 오류가 아니라 계좌 없음이다(남의 계좌도 404 로 온다)")
    void notFound_AccountNotFound() {
      given(userServiceClientWrapper.getAccountBalance(20L))
        .willThrow(new FeignException.NotFound("Not Found", balanceRequest(), null, Map.of()));

      assertThatThrownBy(() -> tradeUtils.getAccountBalance("20"))
        .isInstanceOf(TradeException.class)
        .hasMessage(TradeResponse.ACCOUNT_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("사용자 서비스가 403 이면 계좌 접근 거부다")
    void forbidden_AccessDenied() {
      given(userServiceClientWrapper.getAccountBalance(20L))
        .willThrow(new FeignException.Forbidden("Forbidden", balanceRequest(), null, Map.of()));

      assertThatThrownBy(() -> tradeUtils.getAccountBalance("20"))
        .isInstanceOf(TradeException.class)
        .hasMessage(TradeResponse.ACCOUNT_ACCESS_DENIED.getMessage());
    }

    private static Request balanceRequest() {
      return Request.create(Request.HttpMethod.GET, "/api/accounts/20/balance", Map.of(), null,
        StandardCharsets.UTF_8, null);
    }
  }

  @Nested
  @DisplayName("매수 잔액 검증")
  class BuyBalance {

    private final AccountBalanceDto balance = AccountBalanceDto.builder().balanceUsd(new BigDecimal("100")).build();

    @Test
    @DisplayName("잔액이 모자라면 부족으로 기록하고 막는다")
    void insufficient_LogsAndThrows() {
      assertThatThrownBy(() -> tradeUtils.validateBuyBalance("user-1", "20", new BigDecimal("100.01"), balance))
        .isInstanceOf(TradeException.class)
        .hasMessage(TradeResponse.INSUFFICIENT_BALANCE.getMessage());
      verify(tradeLogger).logBalanceCheck("user-1", "20", new BigDecimal("100.01"), new BigDecimal("100"), false);
    }

    @Test
    @DisplayName("잔액이 딱 맞거나 남으면 충분으로 기록하고 통과한다")
    void sufficient_LogsAndPasses() {
      assertThatCode(() -> tradeUtils.validateBuyBalance("user-1", "20", new BigDecimal("100"), balance))
        .doesNotThrowAnyException();
      verify(tradeLogger).logBalanceCheck("user-1", "20", new BigDecimal("100"), new BigDecimal("100"), true);
      verify(tradeLogger, never()).logBalanceCheck(anyString(), anyString(), any(), any(), eq(false));
    }
  }
}
