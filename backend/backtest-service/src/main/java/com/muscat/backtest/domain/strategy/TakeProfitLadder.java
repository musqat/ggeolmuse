package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.constants.BacktestConstants.Money;
import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.domain.dto.request.PartialTakeProfit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// 부분 익절 단계: 종가가 평균 단가(수수료 포함) × (1 + 단계 수익률) 이상인 날 보유의 단계 팔 비율만큼 팜, 단계는 사이클에 한 번 · 하루에 한 단계
final class TakeProfitLadder {

  private final List<PartialTakeProfit> steps;
  // 다음에 볼 단계 번호
  private int next;

  private TakeProfitLadder(List<PartialTakeProfit> steps) {
    this.steps = steps;
  }

  // 단계를 비우면 보지 않음
  static TakeProfitLadder of(List<PartialTakeProfit> steps) {
    return new TakeProfitLadder(steps == null ? List.of() : List.copyOf(steps));
  }

  // 사이클 첫 매수: 첫 단계부터
  void open() {
    next = 0;
  }

  // 아직 안 쓴 가장 낮은 단계에 종가가 닿으면 팔 몫(아니면 null)
  SaleShare check(BigDecimal close, BigDecimal held, BigDecimal cost) {
    if (next >= steps.size()) {
      return null;
    }
    PartialTakeProfit step = steps.get(next);
    BigDecimal average = cost.divide(held, Money.SHARES_SCALE, RoundingMode.HALF_UP);
    if (close.compareTo(average.multiply(BigDecimal.ONE.add(step.gainRate()))) < 0) {
      return null;
    }
    String gain = step.gainRate().movePointRight(2).stripTrailingZeros().toPlainString();
    return SaleShare.of(held, cost, step.sellRate(),
      String.format("%s +%s%%", TransactionTrigger.PARTIAL_TAKE_PROFIT, gain));
  }

  // 판 단계는 이 사이클에서 다시 보지 않음
  void sold() {
    next++;
  }
}
