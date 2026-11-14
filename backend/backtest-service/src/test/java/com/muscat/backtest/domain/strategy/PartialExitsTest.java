package com.muscat.backtest.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.domain.dto.request.PartialTakeProfit;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PartialExits 트레일링 스탑 · 부분 익절 묶음")
class PartialExitsTest {

  // 고점 대비 10% 내리면 절반, 평균 단가 대비 20% 오르면 절반
  private static PartialExits exits() {
    PartialExits exits = PartialExits.of(new BigDecimal("0.1"), new BigDecimal("0.5"),
      List.of(new PartialTakeProfit(new BigDecimal("0.2"), new BigDecimal("0.5"))));
    exits.open(new BigDecimal("100"), new BigDecimal("100"));
    return exits;
  }

  @Test
  @DisplayName("트레일링 스탑에 안 닿으면 부분 익절을 보고, 판 부분 익절 단계는 다시 보지 않음")
  void ladderAfterTrailing() {
    PartialExits exits = exits();

    SaleShare first = exits.check(new BigDecimal("125"), new BigDecimal("10"), new BigDecimal("1000"));
    assertThat(first.shares()).isEqualByComparingTo("5");
    exits.sold(new BigDecimal("125"));

    assertThat(exits.check(new BigDecimal("125"), new BigDecimal("5"), new BigDecimal("500"))).isNull();
  }

  @Test
  @DisplayName("트레일링 스탑에 닿으면 트레일링 스탑 몫을 팔고 판 값이 새 기준가")
  void trailingFirst() {
    PartialExits exits = exits();
    exits.check(new BigDecimal("125"), new BigDecimal("10"), new BigDecimal("1000"));
    exits.sold(new BigDecimal("125"));

    SaleShare trailed = exits.check(new BigDecimal("110"), new BigDecimal("5"), new BigDecimal("500"));
    assertThat(trailed.trigger()).startsWith(TransactionTrigger.TRAILING_STOP);
    assertThat(trailed.shares()).isEqualByComparingTo("2.5");
    exits.sold(new BigDecimal("110"));

    assertThat(exits.check(new BigDecimal("100"), new BigDecimal("2.5"), new BigDecimal("250"))).isNull();
  }
}
