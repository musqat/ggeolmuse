package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.domain.dto.request.PartialTakeProfit;
import java.math.BigDecimal;
import java.util.List;

// 트레일링 스탑 · 부분 익절 묶음: 트레일링 스탑을 먼저 보고 안 닿으면 부분 익절, 판 뒤에는 판 규칙만 다음으로 넘김
final class PartialExits {

  private final TrailingStop trailing;
  private final TakeProfitLadder ladder;
  // 마지막으로 본 팔 몫이 트레일링 스탑 몫인지
  private boolean trailed;

  private PartialExits(TrailingStop trailing, TakeProfitLadder ladder) {
    this.trailing = trailing;
    this.ladder = ladder;
  }

  // 하락 비율을 비우면 트레일링 스탑을, 단계를 비우면 부분 익절을 보지 않음
  static PartialExits of(BigDecimal stopRate, BigDecimal sellRate, List<PartialTakeProfit> steps) {
    return new PartialExits(TrailingStop.of(stopRate, sellRate), TakeProfitLadder.of(steps));
  }

  // 사이클 첫 매수: 트레일링 스탑 기준가는 매수가 · 당일 종가 가운데 큰 값, 부분 익절은 첫 단계부터
  void open(BigDecimal price, BigDecimal close) {
    trailing.open(price, close);
    ladder.open();
  }

  // 트레일링 스탑 다음 부분 익절에 닿으면 팔 몫(아니면 null)
  SaleShare check(BigDecimal close, BigDecimal held, BigDecimal cost) {
    SaleShare share = trailing.check(close, held, cost);
    trailed = share != null;
    return trailed ? share : ladder.check(close, held, cost);
  }

  // 판 값: 트레일링 스탑 몫이면 새 기준가, 부분 익절 몫이면 다음 단계로
  void sold(BigDecimal price) {
    if (trailed) {
      trailing.sold(price);
    } else {
      ladder.sold();
    }
  }
}
