package com.muscat.backtest.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.common.enums.type.SkipReason;
import com.muscat.backtest.domain.model.SkippedSpan;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PurchaseRounds 회차 · 예산 셈")
class PurchaseRoundsTest {

  private static final LocalDate MAR = LocalDate.of(2024, 3, 4);
  private static final LocalDate APR = LocalDate.of(2024, 4, 1);

  @Test
  @DisplayName("최대 횟수 · 예산을 비우면 제한 없음")
  void noLimits() {
    PurchaseRounds rounds = new PurchaseRounds(null, null);
    rounds.bought(new BigDecimal("100000"));

    assertThat(rounds.used()).isFalse();
    assertThat(rounds.budgetLeft()).isNull();
    assertThat(rounds.span()).isNull();
    assertThat(rounds.exceeds(new BigDecimal("999999999"))).isFalse();
  }

  @Test
  @DisplayName("새 돈을 넣은 매수만 회차 · 넣은 돈에 세고 남은 예산은 예산 - 넣은 돈")
  void countsOnlyNewMoney() {
    PurchaseRounds rounds = new PurchaseRounds(2, new BigDecimal("300000"));
    rounds.bought(BigDecimal.ZERO);
    rounds.bought(new BigDecimal("100000"));

    assertThat(rounds.used()).isFalse();
    assertThat(rounds.deposited()).isEqualByComparingTo("100000");
    assertThat(rounds.budgetLeft()).isEqualByComparingTo("200000");
    assertThat(rounds.exceeds(new BigDecimal("200000"))).isFalse();
    assertThat(rounds.exceeds(new BigDecimal("200001"))).isTrue();
  }

  @Test
  @DisplayName("산 회차와 예산 부족 회차를 더해 최대 횟수에 닿으면 다 씀")
  void shortRoundsCountTowardMax() {
    PurchaseRounds rounds = new PurchaseRounds(2, null);
    rounds.bought(new BigDecimal("100000"));
    rounds.shortOn(MAR);

    assertThat(rounds.used()).isTrue();
  }

  @Test
  @DisplayName("처음 모자란 날짜부터 남은 예산 0, 부족 구간은 처음 날짜와 부족 회차 수")
  void firstShortZeroesBudget() {
    PurchaseRounds rounds = new PurchaseRounds(null, new BigDecimal("300000"));
    rounds.bought(new BigDecimal("100000"));
    rounds.shortOn(MAR);
    rounds.shortOn(APR);

    assertThat(rounds.isShort()).isTrue();
    assertThat(rounds.budgetLeft()).isEqualByComparingTo("0");
    assertThat(rounds.shortRounds()).isEqualTo(2);
    assertThat(rounds.span()).isEqualTo(new SkippedSpan(MAR, 2, SkipReason.OVER_BUDGET));
  }
}
