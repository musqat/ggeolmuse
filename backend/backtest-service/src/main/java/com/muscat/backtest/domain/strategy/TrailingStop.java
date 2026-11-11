package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.constants.BacktestConstants.Money;
import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;

// 트레일링 스탑: 종가가 기준가(사이클 첫 매수가에서 시작한 종가 고점, 판 뒤에는 판 값) × (1 - 하락 비율) 이하인 날 보유의 팔 비율만큼 팜
final class TrailingStop {

  private final BigDecimal stopRate;
  private final BigDecimal sellRate;
  private BigDecimal reference;

  private TrailingStop(BigDecimal stopRate, BigDecimal sellRate) {
    this.stopRate = stopRate;
    this.sellRate = sellRate;
  }

  // 하락 비율을 비우면 보지 않음, 팔 비율을 비우면 전량
  static TrailingStop of(BigDecimal stopRate, BigDecimal sellRate) {
    return new TrailingStop(stopRate, sellRate == null ? BigDecimal.ONE : sellRate);
  }

  // 사이클 첫 매수: 매수가와 그 날짜 종가 가운데 큰 값이 기준가
  void open(BigDecimal price, BigDecimal close) {
    reference = price.max(close);
  }

  // 기준가를 그 날짜 종가와 큰 쪽으로 올린 뒤 종가가 기준가 × (1 - 하락 비율) 이하면 팔 몫(아니면 null)
  Exit check(BigDecimal close, BigDecimal held, BigDecimal cost) {
    if (stopRate == null) {
      return null;
    }
    reference = reference.max(close);
    if (close.compareTo(reference.multiply(BigDecimal.ONE.subtract(stopRate))) > 0) {
      return null;
    }
    BigDecimal shares = sellRate.compareTo(BigDecimal.ONE) >= 0
      ? held
      : held.multiply(sellRate).setScale(Money.SHARES_SCALE, RoundingMode.HALF_UP);
    if (shares.signum() == 0) {
      return null;
    }
    boolean all = shares.compareTo(held) >= 0;
    BigDecimal soldCost = all ? cost : cost.multiply(shares).divide(held, Money.SHARES_SCALE, RoundingMode.HALF_UP);
    BigDecimal drop = MoneyUtils.calculateReturnRate(reference, close).abs();
    return new Exit(all ? held : shares, soldCost, all,
      String.format("%s %.1f%%", TransactionTrigger.TRAILING_STOP, drop));
  }

  // 판 값이 새 기준가
  void sold(BigDecimal price) {
    reference = price;
  }

  // 팔 주식 수 · 그 몫의 원가(USD) · 전량인지 · 거래 이유
  record Exit(BigDecimal shares, BigDecimal cost, boolean all, String trigger) {}
}
