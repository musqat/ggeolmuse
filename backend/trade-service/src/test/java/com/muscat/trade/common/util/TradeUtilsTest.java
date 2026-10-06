package com.muscat.trade.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.config.TradeProperties;
import com.muscat.trade.infra.client.UserServiceClientWrapper;
import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("거래 수수료 계산")
class TradeUtilsTest {

  private static final BigDecimal TRADE_AMOUNT = new BigDecimal("89.80");

  private final TradeUtils tradeUtils = new TradeUtils(
    mock(UserServiceClientWrapper.class), mock(TradeLogger.class), new TradeProperties());

  private static AccountBalanceDto balanceWithRate(BigDecimal commissionRate) {
    return AccountBalanceDto.builder().commissionRate(commissionRate).build();
  }

  @Test
  @DisplayName("계좌 수수료율로 계산한다")
  void calculateFee_AccountRate_UsesAccountRate() {
    AccountBalanceDto balance = balanceWithRate(new BigDecimal("0.001"));

    // 89.80 × 0.001 = 0.0898
    BigDecimal fee = tradeUtils.calculateFee(balance, TRADE_AMOUNT);

    assertThat(fee).isEqualByComparingTo("0.09");
  }

  @Test
  @DisplayName("계좌 수수료율이 0 이면 수수료도 0")
  void calculateFee_ZeroRate_ReturnsZero() {
    BigDecimal fee = tradeUtils.calculateFee(balanceWithRate(BigDecimal.ZERO), TRADE_AMOUNT);

    assertThat(fee).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("계좌 수수료율이 없으면 기본 0.25% 를 쓴다")
  void calculateFee_NullRate_UsesDefaultRate() {
    // 89.80 × 0.0025 = 0.2245
    BigDecimal fee = tradeUtils.calculateFee(balanceWithRate(null), TRADE_AMOUNT);

    assertThat(fee).isEqualByComparingTo("0.22");
  }

  @Test
  @DisplayName("계좌 수수료율이 음수면 기본 0.25% 를 쓴다")
  void calculateFee_NegativeRate_UsesDefaultRate() {
    AccountBalanceDto balance = balanceWithRate(new BigDecimal("-0.001"));

    BigDecimal fee = tradeUtils.calculateFee(balance, TRADE_AMOUNT);

    assertThat(fee).isEqualByComparingTo("0.22");
  }
}
