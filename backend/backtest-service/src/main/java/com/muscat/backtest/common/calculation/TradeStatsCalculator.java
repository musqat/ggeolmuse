package com.muscat.backtest.common.calculation;

import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.domain.dto.response.TradeStats;
import com.muscat.backtest.domain.model.StrategyTransaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// 거래 내역을 사이클(보유 0 에서 산 날 ~ 다시 0 이 된 날)로 나눠 매매 통계를 내는 계산기
public final class TradeStatsCalculator {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365.25");
  // 사이클 수익률 중간 자리수
  private static final int RATE_SCALE = 10;

  private TradeStatsCalculator() {}

  // 끝난 사이클(산 날 · 전부 판 날 · 매수 원가 합 USD · 매도 실현 손익 합 USD)
  private record Cycle(LocalDate opened, LocalDate closed, BigDecimal cost, BigDecimal realized) {

    boolean won() {
      return realized.signum() > 0;
    }

    // 실현 손익 합 ÷ 매수 원가 × 100
    BigDecimal returnPercent() {
      return Decimals.isPositive(cost)
        ? realized.multiply(HUNDRED).divide(cost, RATE_SCALE, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;
    }

    long holdingDays() {
      return ChronoUnit.DAYS.between(opened, closed);
    }
  }

  // 종목 하나의 진행 중 사이클(보유 주식 · 매수 원가 합 USD · 매도 실현 손익 합 USD · 산 날)
  private static final class Holding {
    private BigDecimal shares = BigDecimal.ZERO;
    private BigDecimal cost = BigDecimal.ZERO;
    private BigDecimal realized = BigDecimal.ZERO;
    private LocalDate opened;
  }

  // 양도세 납부를 뺀 거래(거래일 순서)의 매수 · 매도 · 트레일링 스탑 · 부분 익절 수와 종목마다 사이클 통계(종목이 없으면 한 묶음), 1년 사이클 수는 시작일 ~ 평가일 기준
  public static TradeStats of(List<StrategyTransaction> transactions, LocalDate start, LocalDate valuationDate) {
    List<StrategyTransaction> trades = transactions.stream()
      .filter(tx -> !tx.isTaxPayment())
      .sorted(Comparator.comparing(StrategyTransaction::tradedOn))
      .toList();
    int buys = 0;
    int sells = 0;
    int trailingStops = 0;
    int partialTakeProfits = 0;
    List<Cycle> cycles = new ArrayList<>();
    Map<String, Holding> holdings = new HashMap<>();
    for (StrategyTransaction tx : trades) {
      Holding holding = holdings.computeIfAbsent(Objects.requireNonNullElse(tx.getSymbol(), ""), key -> new Holding());
      holding.shares = holding.shares.add(tx.getShares());
      if (!tx.isSell()) {
        // 배당 재투자 주식은 보유에만 더함(원가 0)
        if (!TransactionTrigger.DIVIDEND_REINVESTMENT.equals(tx.getTrigger())) {
          buys++;
          holding.opened = holding.opened == null ? tx.tradedOn() : holding.opened;
          holding.cost = holding.cost.add(tx.costUsd());
        }
        continue;
      }
      sells++;
      if (soldBy(tx, TransactionTrigger.TRAILING_STOP)) {
        trailingStops++;
      }
      if (soldBy(tx, TransactionTrigger.PARTIAL_TAKE_PROFIT)) {
        partialTakeProfits++;
      }
      holding.realized = holding.realized.add(tx.getRealizedProfit() != null ? tx.getRealizedProfit() : BigDecimal.ZERO);
      if (holding.opened != null && holding.shares.signum() <= 0) {
        cycles.add(new Cycle(holding.opened, tx.tradedOn(), holding.cost, holding.realized));
        holdings.remove(Objects.requireNonNullElse(tx.getSymbol(), ""));
      }
    }

    int openCycles = (int) holdings.values().stream().filter(holding -> holding.opened != null).count();
    boolean open = openCycles > 0;
    List<BigDecimal> wins = cycles.stream().filter(Cycle::won).map(Cycle::returnPercent).toList();
    List<BigDecimal> losses = cycles.stream().filter(cycle -> !cycle.won()).map(cycle -> cycle.returnPercent().abs())
      .toList();
    BigDecimal averageWin = average(wins, 2);
    BigDecimal averageLoss = average(losses, 2);
    return new TradeStats(buys, sells, cycles.size(), open, wins.size(), losses.size(),
      ratio(BigDecimal.valueOf(wins.size()).multiply(HUNDRED), BigDecimal.valueOf(cycles.size())),
      averageWin, averageLoss, ratio(averageWin, averageLoss),
      average(cycles.stream().map(cycle -> BigDecimal.valueOf(cycle.holdingDays())).toList(), 1),
      perYear(cycles.size() + openCycles, start, valuationDate), trailingStops, partialTakeProfits);
  }

  // 거래 이유가 그 규칙 이름으로 시작하는지
  private static boolean soldBy(StrategyTransaction tx, String rule) {
    return tx.getTrigger() != null && tx.getTrigger().startsWith(rule);
  }

  // 평균(값이 없으면 null)
  private static BigDecimal average(List<BigDecimal> values, int scale) {
    return values.isEmpty() ? null
      : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(values.size()), scale, RoundingMode.HALF_UP);
  }

  // 소수 둘째 나눗셈(어느 쪽이 없거나 나누는 값이 0 이면 null)
  private static BigDecimal ratio(BigDecimal value, BigDecimal divisor) {
    return value == null || divisor == null || divisor.signum() == 0 ? null
      : value.divide(divisor, 2, RoundingMode.HALF_UP);
  }

  // 시작한 사이클 수 ÷ (시작일 ~ 평가일 날수 ÷ 365.25), 같은 날이면 null
  private static BigDecimal perYear(int started, LocalDate start, LocalDate valuationDate) {
    long days = ChronoUnit.DAYS.between(start, valuationDate);
    return days <= 0 ? null
      : BigDecimal.valueOf(started).multiply(DAYS_PER_YEAR).divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
  }
}
