package com.muscat.user.domain.account.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.user.common.exceptions.AccountException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Account 엔티티")
class AccountTest {

  private static Account accountWithUsd(String balanceUsd) {
    return Account.builder()
      .accountNumber("1001-2024-0001")
      .accountName("메인 계좌")
      .balanceUsd(new BigDecimal(balanceUsd))
      .build();
  }

  @Test
  @DisplayName("슬리피지율 기본값은 0.1% 다")
  void builder_DefaultSlippageRate_IsTenBasisPoints() {
    Account account = Account.builder()
      .accountNumber("1001-2024-0001")
      .accountName("메인 계좌")
      .build();

    assertThat(account.getSlippageRate()).isEqualByComparingTo("0.001");
  }

  @Test
  @DisplayName("배당 반환은 잔액보다 커도 마이너스로 뺀다")
  void applyDividendAdjustment_AllowsNegativeBalance() {
    Account account = accountWithUsd("1.00");

    account.applyDividendAdjustment(new BigDecimal("-5.00"));

    assertThat(account.getBalanceUsd()).isEqualByComparingTo("-4.00");
  }

  @Test
  @DisplayName("매매 정산은 잔액보다 크게 빼면 잔액 부족으로 거절한다")
  void adjustUsdBalance_RejectsNegativeBalance() {
    Account account = accountWithUsd("1.00");

    assertThatThrownBy(() -> account.adjustUsdBalance(new BigDecimal("-5.00")))
      .isInstanceOf(AccountException.class)
      .hasFieldOrPropertyWithValue("errorCode", "INSUFFICIENT_USD_BALANCE");
    assertThat(account.getBalanceUsd()).isEqualByComparingTo("1.00");
  }
}
