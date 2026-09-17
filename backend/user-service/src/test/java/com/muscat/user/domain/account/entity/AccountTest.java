package com.muscat.user.domain.account.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Account 엔티티 기본값")
class AccountTest {

  @Test
  @DisplayName("슬리피지율 기본값은 0.1% 다")
  void builder_DefaultSlippageRate_IsTenBasisPoints() {
    Account account = Account.builder()
      .accountNumber("1001-2024-0001")
      .accountName("메인 계좌")
      .build();

    assertThat(account.getSlippageRate()).isEqualByComparingTo("0.001");
  }
}
