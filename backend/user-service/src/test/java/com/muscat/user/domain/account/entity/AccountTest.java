package com.muscat.user.domain.account.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.user.common.exception.AccountException;
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

  // 원화 → 달러 환전 누계 · 평균 환율이 있는 달러 잔액 계좌
  private static Account exchanged(String balanceUsd, String totalKrw, String totalUsd, String avgRate) {
    return Account.builder()
      .accountNumber("1001-2024-0001")
      .accountName("메인 계좌")
      .balanceKrw(new BigDecimal("1000000"))
      .balanceUsd(new BigDecimal(balanceUsd))
      .totalExchangedKrw(new BigDecimal(totalKrw))
      .totalExchangedUsd(new BigDecimal(totalUsd))
      .avgExchangeRate(new BigDecimal(avgRate))
      .build();
  }

  @Test
  @DisplayName("부분 역환전은 남은 달러 비율만큼 두 환전 누계를 줄이고 평균 환율은 그대로 둔다")
  void executeExchangeUsdToKrw_Partial_ScalesTotals() {
    Account account = exchanged("1000.00", "1300000", "1000.00", "1300");

    account.executeExchangeUsdToKrw(new BigDecimal("999.99"), new BigDecimal("1299987"));

    assertThat(account.getTotalExchangedKrw()).isEqualByComparingTo("13");
    assertThat(account.getTotalExchangedUsd()).isEqualByComparingTo("0.01");
    assertThat(account.getAvgExchangeRate()).isEqualByComparingTo("1300");
  }

  @Test
  @DisplayName("매도 대금이 섞인 달러 잔액의 절반을 역환전하면 두 환전 누계도 절반이 된다")
  void executeExchangeUsdToKrw_HalfOfMixedBalance_HalvesTotals() {
    Account account = exchanged("2000.00", "1300000", "1000.00", "1300");

    account.executeExchangeUsdToKrw(new BigDecimal("1000.00"), new BigDecimal("1300000"));

    assertThat(account.getTotalExchangedKrw()).isEqualByComparingTo("650000");
    assertThat(account.getTotalExchangedUsd()).isEqualByComparingTo("500.00");
    assertThat(account.getAvgExchangeRate()).isEqualByComparingTo("1300");
  }

  @Test
  @DisplayName("전액 역환전은 두 환전 누계와 평균 환율을 0 으로 만든다")
  void executeExchangeUsdToKrw_All_ResetsTotals() {
    Account account = exchanged("1000.00", "1300000", "1000.00", "1300");

    account.executeExchangeUsdToKrw(new BigDecimal("1000.00"), new BigDecimal("1300000"));

    assertThat(account.getTotalExchangedKrw()).isEqualByComparingTo("0");
    assertThat(account.getTotalExchangedUsd()).isEqualByComparingTo("0");
    assertThat(account.getAvgExchangeRate()).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("새 평균이 정상 환율 범위 밖이면 이번 환전 금액으로 두 누계를 다시 시작한다")
  void executeExchangeKrwToUsd_AverageOutOfRange_RestartsTotals() {
    Account account = exchanged("1000.00", "1300000", "0", "1300");

    account.executeExchangeKrwToUsd(new BigDecimal("13000"), new BigDecimal("10.00"),
      new BigDecimal("131300"), new BigDecimal("13000"));

    assertThat(account.getTotalExchangedKrw()).isEqualByComparingTo("13000");
    assertThat(account.getTotalExchangedUsd()).isEqualByComparingTo("10.00");
    assertThat(account.getAvgExchangeRate()).isEqualByComparingTo("1300");
    assertThat(account.getBalanceUsd()).isEqualByComparingTo("1010.00");
  }

  @Test
  @DisplayName("달러 누계 없이 남은 원화 누계는 평균 환율로 달러 누계를 맞춘다")
  void alignExchangeTotals_MissingUsdTotal_DerivesFromAverage() {
    Account account = exchanged("1000.00", "1300000", "0", "1300");

    account.alignExchangeTotals();

    assertThat(account.getTotalExchangedKrw()).isEqualByComparingTo("1300000");
    assertThat(account.getTotalExchangedUsd()).isEqualByComparingTo("1000.00");
  }

  @Test
  @DisplayName("원화 누계를 더하고 달러 누계는 그대로인 계좌는 평균 환율로 달러 누계를 맞춘다")
  void alignExchangeTotals_StaleUsdTotal_DerivesFromAverage() {
    Account account = exchanged("2000.00", "2700000", "1000.00", "1350");

    account.alignExchangeTotals();

    assertThat(account.getTotalExchangedUsd()).isEqualByComparingTo("2000.00");
  }

  @Test
  @DisplayName("평균 환율이 0 이면 남은 달러 누계도 0 으로 맞춘다")
  void alignExchangeTotals_ZeroAverage_ClearsTotals() {
    Account account = exchanged("0", "0", "1000.00", "0");

    account.alignExchangeTotals();

    assertThat(account.getTotalExchangedKrw()).isEqualByComparingTo("0");
    assertThat(account.getTotalExchangedUsd()).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("두 누계의 비율이 평균 환율과 1% 안으로 맞으면 그대로 둔다")
  void alignExchangeTotals_Consistent_KeepsTotals() {
    Account account = exchanged("0.01", "13", "0.01", "1299.37");

    account.alignExchangeTotals();

    assertThat(account.getTotalExchangedKrw()).isEqualByComparingTo("13");
    assertThat(account.getTotalExchangedUsd()).isEqualByComparingTo("0.01");
  }
}
