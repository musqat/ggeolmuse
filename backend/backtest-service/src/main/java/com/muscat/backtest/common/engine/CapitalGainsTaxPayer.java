package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.calculation.CapitalGainsTaxCalculator;
import com.muscat.backtest.common.constants.BacktestConstants.CapitalGains;
import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.domain.model.StrategyTransaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// 양도세 납부: 판 해의 세금을 다음 해 5월 1일 이후 첫 거래일부터 사고팔기 전에 판 돈(USD)에서 냄
// 현금이 모자라면 있는 만큼 내고 남은 몫은 현금이 있는 거래일마다 오래된 해부터 냄
public final class CapitalGainsTaxPayer {

  private final boolean enabled;
  // 납부일을 센 마지막 판 해
  private int dueThrough = Integer.MIN_VALUE;
  private final Map<Integer, LocalDate> dueDates = new TreeMap<>();
  private final Map<Integer, BigDecimal> unpaidKrw = new TreeMap<>();
  private final Map<Integer, BigDecimal> paidKrw = new TreeMap<>();

  public CapitalGainsTaxPayer(boolean enabled) {
    this.enabled = enabled;
  }

  // 그 날짜까지 납부일이 온 해의 남은 세금을 현금에서 낸 거래(끄거나 낼 세금 · 현금이 없거나 남은 세금이 몇 원뿐이라 달러로 0센트가 되면 null)
  // 다 낼 수 있으면 남은 세금 ÷ 환율(센트 반올림), 모자라면 현금 전부(낸 원화는 원 미만 버림)
  public StrategyTransaction pay(LocalDate date, List<StrategyTransaction> transactions, BigDecimal cash,
    BigDecimal fxRate, boolean defaultFx) {
    if (!enabled) {
      return null;
    }
    registerDue(date, transactions);
    BigDecimal owedKrw = owedKrw();
    if (owedKrw.signum() == 0 || cash == null || cash.signum() <= 0) {
      return null;
    }
    BigDecimal owedUsd = owedKrw.divide(fxRate, 2, RoundingMode.HALF_UP);
    boolean full = cash.compareTo(owedUsd) >= 0;
    BigDecimal taxPaid = full ? owedUsd : cash;
    BigDecimal paid = full ? owedKrw : cash.multiply(fxRate).setScale(0, RoundingMode.DOWN).min(owedKrw);
    if (paid.signum() == 0 || taxPaid.setScale(2, RoundingMode.HALF_UP).signum() == 0) {
      return null;
    }
    allocate(paid);
    return StrategyTransaction.builder()
      .date(date)
      .actualDate(date)
      .price(BigDecimal.ZERO)
      .shares(BigDecimal.ZERO)
      .amount(BigDecimal.ZERO)
      .fee(BigDecimal.ZERO)
      .fxRate(fxRate)
      .taxPaid(taxPaid)
      .defaultFxDate(defaultFx ? date : null)
      .trigger(TransactionTrigger.CAPITAL_GAINS_TAX_PAYMENT)
      .build();
  }

  // 그 날짜까지 납부일이 온 남은 세금 ÷ 환율(센트 반올림)에서 현금을 뺀 모자란 달러(끄거나 현금이 넉넉하면 0)
  public BigDecimal shortfallUsd(LocalDate date, List<StrategyTransaction> transactions, BigDecimal cash,
    BigDecimal fxRate) {
    if (!enabled) {
      return BigDecimal.ZERO;
    }
    registerDue(date, transactions);
    BigDecimal owedUsd = owedKrw().divide(fxRate, 2, RoundingMode.HALF_UP);
    return owedUsd.subtract(cash == null ? BigDecimal.ZERO : cash).max(BigDecimal.ZERO);
  }

  // 납부일이 온 해의 남은 세금 합(원화)
  private BigDecimal owedKrw() {
    return unpaidKrw.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  // 판 해마다 납부일 · 낸 원화
  public CapitalGainsTaxCalculator.Payments payments() {
    return new CapitalGainsTaxCalculator.Payments(Map.copyOf(dueDates), Map.copyOf(paidKrw));
  }

  // 그 날짜에 납부일이 처음 온 판 해의 납부일 · 세금
  private void registerDue(LocalDate date, List<StrategyTransaction> transactions) {
    int lastDue = MonthDay.from(date).isBefore(CapitalGains.PAYMENT_FROM) ? date.getYear() - 2 : date.getYear() - 1;
    if (lastDue <= dueThrough) {
      return;
    }
    int from = dueThrough;
    CapitalGainsTaxCalculator.realizedByYear(CapitalGainsTaxCalculator.realizedGains(transactions))
      .forEach((year, realized) -> {
        if (year > from && year <= lastDue) {
          dueDates.put(year, date);
          BigDecimal tax = CapitalGainsTaxCalculator.tax(realized);
          if (tax.signum() > 0) {
            unpaidKrw.put(year, tax);
          }
        }
      });
    dueThrough = lastDue;
  }

  // 낸 원화를 오래된 해부터 채움
  private void allocate(BigDecimal paid) {
    BigDecimal left = paid;
    Iterator<Map.Entry<Integer, BigDecimal>> years = unpaidKrw.entrySet().iterator();
    while (left.signum() > 0) {
      Map.Entry<Integer, BigDecimal> year = years.next();
      BigDecimal part = year.getValue().min(left);
      paidKrw.merge(year.getKey(), part, BigDecimal::add);
      left = left.subtract(part);
      if (part.compareTo(year.getValue()) == 0) {
        years.remove();
      } else {
        year.setValue(year.getValue().subtract(part));
      }
    }
  }
}
