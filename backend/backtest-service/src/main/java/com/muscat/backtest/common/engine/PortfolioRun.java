package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.calculation.CapitalGainsTaxCalculator;
import com.muscat.backtest.common.constants.BacktestConstants.Money;
import com.muscat.backtest.common.constants.BacktestConstants.Portfolio;
import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.common.engine.PortfolioEngine.Dividend;
import com.muscat.backtest.common.engine.PortfolioEngine.Position;
import com.muscat.backtest.common.engine.PortfolioEngine.Result;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.ExecutionTiming;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.PortfolioRebalance;
import com.muscat.backtest.common.enums.type.PortfolioStrategyType;
import com.muscat.backtest.common.enums.type.TaxSale;
import com.muscat.backtest.common.enums.type.TradeReason;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.domain.dto.request.PortfolioRequest;
import com.muscat.backtest.domain.dto.response.DailyValue;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.domain.strategy.SignalHolding;
import com.muscat.backtest.domain.strategy.SignalOrder;
import com.muscat.backtest.domain.strategy.TakeProfitStopLoss;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto.DividendPayment;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.TreeSet;

// 포트폴리오 한 번 돌리기: 현금(신호 줄 몫 포함) · 거래 기록 · 배당 · 넣은 돈 · 일별 가치를 두고 날마다 모의 매매(시세 조회 없음)
final class PortfolioRun {

  private final PortfolioRequest request;
  private final List<Line> lines;
  private final List<Line> targets;
  private final FxRates fxRates;
  private final LocalDate valuationDate;
  private final CapitalGainsTaxPayer taxPayer;
  private final PortfolioRebalance rebalance;
  private final PortfolioTaxSalePlan taxSalePlan;
  private final Set<LocalDate> harvestDates;
  private final TradeLedger ledger;
  private final List<Dividend> dividends = new ArrayList<>();
  private final List<Day> days = new ArrayList<>();
  private BigDecimal invested = BigDecimal.ZERO;
  private BigDecimal pendingDepositUsd = BigDecimal.ZERO;
  private boolean bought;
  private int rebalancedPeriod = Integer.MIN_VALUE;

  PortfolioRun(PortfolioRequest request, List<Line> lines, FxRates fxRates, LocalDate valuationDate,
    Set<LocalDate> harvestDates) {
    this.request = request;
    this.lines = lines;
    this.targets = lines.stream().filter(line -> line.signal == null).toList();
    this.fxRates = fxRates;
    this.valuationDate = valuationDate;
    this.taxPayer = new CapitalGainsTaxPayer(Boolean.TRUE.equals(request.getCapitalGainsTax()));
    this.rebalance = PortfolioRebalance.orNone(request.getRebalance());
    this.taxSalePlan = new PortfolioTaxSalePlan(request.getTaxSale(), request.getTradingFeeRate());
    this.harvestDates = harvestDates;
    this.ledger = new TradeLedger(request.getTradingFeeRate(), request.getPurchaseFxRate(), fxRates,
      Boolean.TRUE.equals(request.getCapitalGainsTax()));
  }

  // 시가 매도 → 양도세 납부 → 배당 → 시가 매수 → 익절 · 손절 → 신호 매매 → 회차 매수 → 리밸런싱 → 연말 정리 → 기간 끝 매도 → 평가(평가일은 마무리에서)
  void day(LocalDate date) {
    BigDecimal fx = ledger.fxOn(date);
    lines.forEach(line -> {
      OHLCPriceDto candle = line.candles.get(date);
      if (candle != null) {
        line.lastClose = PriceLookup.effectiveClose(candle);
      }
    });
    Map<Line, BigDecimal> soldAtOpen = sellAtOpen(date);
    payDueTax(date, fx);
    receiveDividends(date, Boolean.TRUE.equals(request.getReinvestDividends()), soldAtOpen);
    lines.forEach(line -> fill(line, date, true));
    lines.forEach(line -> sellOrFollow(line, date));
    for (Line line : lines) {
      LocalDate planned = line.rounds.get(date);
      if (planned != null) {
        buy(line, date, planned, fx);
      }
    }
    if (rebalanceDue(date)) {
      rebalance(date, fx);
    }
    if (harvestDates.contains(date)) {
      harvest(date, fx);
    }
    if (PeriodEnd.sells(request.getPeriodEnd())) {
      lines.stream().filter(line -> date.equals(line.candles.lastKey())).forEach(line -> sellAtEnd(line, date));
    }
    if (bought && date.isBefore(valuationDate)) {
      BigDecimal rawUsd = lines.stream().map(Line::valueUsd).reduce(ledger.cash(), BigDecimal::add);
      days.add(new Day(date, rawUsd, pendingDepositUsd));
      pendingDepositUsd = BigDecimal.ZERO;
    }
  }

  // 당일 일봉과 주식이 있는 줄(기간 끝에 당일 파는 줄은 빼고)을 연말 정리 계획대로 당일 종가에 팔고 다시 삼(현금 · 신호 줄 몫은 그대로)
  private void harvest(LocalDate date, BigDecimal fx) {
    boolean sellsAtEnd = PeriodEnd.sells(request.getPeriodEnd());
    Map<String, Line> harvestable = new HashMap<>();
    lines.stream()
      .filter(line -> line.holding.held() && line.candles.containsKey(date))
      .filter(line -> !(sellsAtEnd && date.equals(line.candles.lastKey())))
      .forEach(line -> harvestable.put(line.symbol, line));
    Map<String, BigDecimal> prices = new HashMap<>();
    harvestable.forEach((symbol, line) -> prices.put(symbol, line.lastClose));
    for (YearEndHarvest.Sale sale : YearEndHarvest.plan(date, ledger.transactions(), prices,
      request.getTradingFeeRate(), fx)) {
      Line line = harvestable.get(sale.symbol());
      YearEndHarvest.Trade trade = YearEndHarvest.trade(date, sale, line.lastClose, line.holding.shares(),
        line.holding.cost(), request.getTradingFeeRate(), fx);
      trade.sale().setSymbol(line.symbol);
      trade.sale().setReason(sale.gain() ? TradeReason.HARVEST_GAIN_SELL : TradeReason.HARVEST_LOSS_SELL);
      trade.sale().setDefaultFxDate(ledger.saleDefaultFxDate(date));
      trade.rebuy().setSymbol(line.symbol);
      trade.rebuy().setReason(sale.gain() ? TradeReason.HARVEST_GAIN_BUY : TradeReason.HARVEST_LOSS_BUY);
      trade.rebuy().setDefaultFxDate(ledger.defaultFxDate(date));
      ledger.harvest(line.holding, trade);
    }
  }

  // 당일까지 납부일이 온 남은 양도세를 현금에서 냄(모자라면 판매 방식대로 먼저 팖, 같은 날 또 내면 같은 날 납부 줄에 더함)
  private void payDueTax(LocalDate date, BigDecimal fx) {
    sellForTax(date, fx, ledger.taxShortfall(taxPayer, date));
    BigDecimal free = freeCash();
    StrategyTransaction payment = ledger.payTax(taxPayer, date, TradeReason.TAX_PAYMENT);
    if (payment != null) {
      takeFromShares(payment.getTaxPaid().subtract(free).max(BigDecimal.ZERO));
    }
  }

  // 배당락일이 당일까지 온 배당을 전날까지 든 주식(시가에 판 줄은 판 주식)으로 받아 세후 달러를 현금에, 재투자면 당일 종목 종가로 삼(시가에 판 줄은 빼고)
  void receiveDividends(LocalDate date, boolean reinvest, Map<Line, BigDecimal> soldAtOpen) {
    for (Line line : lines) {
      BigDecimal held = soldAtOpen.getOrDefault(line, line.holding.shares());
      while (line.nextDividend < line.dividends.size()
        && !line.dividends.get(line.nextDividend).getExDate().isAfter(date)) {
        DividendPayment payment = line.dividends.get(line.nextDividend++);
        if (payment.getAmount() == null) {
          continue;
        }
        BigDecimal gross = payment.getAmount().multiply(held).setScale(2, RoundingMode.HALF_UP);
        BigDecimal afterTax = gross.subtract(withholding(gross));
        if (!Decimals.isPositive(afterTax)) {
          continue;
        }
        LocalDate exDate = payment.getExDate();
        BigDecimal fx = ledger.fxOn(exDate);
        dividends.add(new Dividend(line.symbol, exDate, afterTax, fx));
        ledger.receiveDividend(afterTax);
        OHLCPriceDto candle = line.candles.get(date);
        if (reinvest && candle != null && !soldAtOpen.containsKey(line)) {
          reinvest(line, exDate, candle, afterTax);
        }
      }
    }
  }

  // 배당 원천징수(USD 두 자리, 세율이 없으면 0)
  private BigDecimal withholding(BigDecimal gross) {
    BigDecimal rate = request.getDividendTaxRate();
    return rate != null && Decimals.isPositive(rate)
      ? gross.multiply(rate).setScale(2, RoundingMode.HALF_UP)
      : BigDecimal.ZERO;
  }

  // 받은 배당 전부로 받은 일봉의 종가 · 환율에 삼(수수료 없음)
  private void reinvest(Line line, LocalDate exDate, OHLCPriceDto candle, BigDecimal usd) {
    BigDecimal price = PriceLookup.effectiveClose(candle);
    LocalDate bought = candle.date();
    StrategyTransaction tx = StrategyTransaction.builder()
      .symbol(line.symbol)
      .reason(TradeReason.DIVIDEND_REINVESTMENT)
      .date(exDate)
      .actualDate(bought)
      .price(price)
      .shares(usd.divide(price, 8, RoundingMode.HALF_UP))
      .amount(BigDecimal.ZERO)
      .cashUsed(usd)
      .fxRate(ledger.fxOn(bought))
      .defaultFxDate(ledger.defaultFxDate(bought))
      .trigger(TransactionTrigger.DIVIDEND_REINVESTMENT)
      .build();
    bought(line, ledger.buy(line.holding, tx));
  }

  // 줄 금액만큼 당일 종가에 삼, 공용 현금(신호 줄 몫 빼고)을 먼저 쓰고 모자란 만큼 새 돈(원 단위)
  private void buy(Line line, LocalDate date, LocalDate planned, BigDecimal fx) {
    boolean lumpSum = line.type == PortfolioStrategyType.LUMP_SUM;
    BigDecimal price = PriceLookup.effectiveClose(line.candles.get(date));
    TradeReason reason = lumpSum ? TradeReason.BUY : TradeReason.DCA_BUY;
    String trigger = lumpSum ? TransactionTrigger.LUMP_SUM_PURCHASE : DcaFrequency.orMonthly(request.getFrequency()).trigger();
    BigDecimal amount = line.plannedKrw;
    BigDecimal free = freeCash();
    if (!Decimals.isPositive(free)) {
      bought(line, ledger.buyWithDeposit(line.holding, planned, date, price, amount, trigger, reason));
      return;
    }
    BigDecimal needUsd = amount.divide(fx, 8, RoundingMode.HALF_UP);
    BigDecimal cashUsed = free.min(needUsd);
    BigDecimal deposit = free.compareTo(needUsd) >= 0
      ? BigDecimal.ZERO
      : MoneyUtils.roundKrw(amount.subtract(free.multiply(fx))).max(BigDecimal.ZERO);
    BigDecimal spendUsd = cashUsed.add(deposit.divide(fx, 8, RoundingMode.HALF_UP));
    BigDecimal fee = ledger.fee(spendUsd);
    bought(line, ledger.buy(line.holding, StrategyTransaction.builder()
      .symbol(line.symbol)
      .reason(reason)
      .date(planned)
      .actualDate(date)
      .price(price)
      .shares(spendUsd.subtract(fee).divide(price, 8, RoundingMode.HALF_UP))
      .fee(fee)
      .amount(deposit)
      .cashUsed(cashUsed)
      .fxRate(fx)
      .defaultFxDate(ledger.defaultFxDate(date))
      .trigger(trigger)
      .build()));
  }

  // 모의 계좌에 남긴 매수를 넣은 돈 · 첫 매수일에 반영
  private void bought(Line line, StrategyTransaction tx) {
    if (line.firstBought == null) {
      line.firstBought = tx.tradedOn();
    }
    invested = invested.add(tx.getAmount());
    pendingDepositUsd = pendingDepositUsd.add(tx.getAmount().divide(tx.getFxRate(), MathContext.DECIMAL64));
    bought = true;
  }

  // 당일 일봉이 있는 줄의 익절 · 손절, 신호 줄은 이어서 신호대로 전량 팔거나 삼(판 날은 사지 않고 익절 · 손절로 다 팔면 다음 매수 신호까지 기다림)
  private void sellOrFollow(Line line, LocalDate date) {
    OHLCPriceDto candle = line.candles.get(date);
    if (candle == null) {
      return;
    }
    boolean wanted = line.signal != null && line.signal.wanted(date);
    if (sellIfTriggered(line, candle, date)) {
      if (line.signal != null) {
        line.signal.soldOut();
      }
      return;
    }
    if (line.signal == null) {
      return;
    }
    boolean holding = line.holding.held();
    if (holding && !wanted) {
      if (line.nextOpen) {
        line.order = new SignalOrder(date, false);
      } else {
        sell(line, line.holding.shares(), line.holding.cost(), line.lastClose, TradeReason.STRATEGY_SELL,
          line.signal.sellTrigger(), date);
      }
    } else if (!holding && wanted && (line.share == null || line.share.signum() > 0)) {
      if (line.nextOpen) {
        line.order = new SignalOrder(date, true);
      } else {
        buyOnSignal(line, date, date, line.lastClose);
      }
    }
  }

  // 당일 시가에 판 신호 줄과 판 주식 수
  private Map<Line, BigDecimal> sellAtOpen(LocalDate date) {
    Map<Line, BigDecimal> sold = new HashMap<>();
    for (Line line : lines) {
      BigDecimal held = line.holding.shares();
      fill(line, date, false);
      if (held.compareTo(line.holding.shares()) != 0) {
        sold.put(line, held);
      }
    }
    return sold;
  }

  // 당일 일봉이 있는 신호 줄의 앞 거래일 매도 · 매수 신호를 당일 시가에 체결(매도는 전량, 매수는 줄 몫 현금이 남았을 때)
  private void fill(Line line, LocalDate date, boolean buy) {
    OHLCPriceDto candle = line.candles.get(date);
    if (line.order == null || line.order.buy() != buy || candle == null) {
      return;
    }
    LocalDate signalDate = line.order.signalDate();
    line.order = null;
    BigDecimal open = PriceLookup.effectiveOpen(candle);
    if (!buy && line.holding.held()) {
      credit(line, recordSale(line, line.holding.shares(), line.holding.cost(), open, TradeReason.STRATEGY_SELL,
        line.signal.sellTrigger(), signalDate, date));
    } else if (buy && line.holding.shares().signum() == 0 && (line.share == null || line.share.signum() > 0)) {
      buyOnSignal(line, signalDate, date, open);
    }
  }

  // 일봉이 줄의 평균 단가 대비 익절 · 손절 기준가에 닿으면 기준가에 전량 팖(판 날 true)
  private boolean sellIfTriggered(Line line, OHLCPriceDto candle, LocalDate date) {
    if (!line.holding.held()) {
      return false;
    }
    TakeProfitStopLoss.Exit exit = line.exits.check(candle, line.holding.shares(), line.holding.cost());
    if (exit == null) {
      return false;
    }
    TradeReason reason = exit.takeProfit() ? TradeReason.TAKE_PROFIT : TradeReason.STOP_LOSS;
    sell(line, line.holding.shares(), line.holding.cost(), exit.price(), reason, exit.trigger(), date);
    return true;
  }

  // 신호 줄 매수: 첫 매수는 줄 금액을 새 돈으로, 이후 매수는 줄 몫 현금 전부로 받은 날짜 · 가격에 삼(계획일은 신호 난 날)
  private void buyOnSignal(Line line, LocalDate planned, LocalDate date, BigDecimal price) {
    String trigger = line.signal.buyTrigger();
    bought(line, line.share == null
      ? ledger.buyWithDeposit(line.holding, planned, date, price, line.plannedKrw, trigger, TradeReason.STRATEGY_BUY)
      : ledger.buyWithCash(line.holding, planned, date, price, line.share, trigger, TradeReason.STRATEGY_BUY));
    line.share = BigDecimal.ZERO;
  }

  // 줄의 마지막 거래일 종가에 남은 주식 전량을 팔고 판 돈을 현금에 넣음(남은 주식이 없으면 그대로)
  private void sellAtEnd(Line line, LocalDate date) {
    if (line.holding.held()) {
      sell(line, line.holding.shares(), line.holding.cost(), line.lastClose, TradeReason.PERIOD_END_SALE,
        TransactionTrigger.PERIOD_END_SALE, date);
    }
  }

  // 신호 줄 빼고 모든 줄의 처음 매수가 끝난 주기 다음 주기부터 주기마다 한 번, 신호 아닌 줄들이 모두 당일 일봉이 있고 기간 끝에 파는 날이 아닌 첫 날
  private boolean rebalanceDue(LocalDate date) {
    if (!rebalance.enabled() || targets.isEmpty()) {
      return false;
    }
    boolean sellsAtEnd = PeriodEnd.sells(request.getPeriodEnd());
    LocalDate allBought = null;
    for (Line line : targets) {
      if (line.firstBought == null || !line.candles.containsKey(date)
        || sellsAtEnd && date.equals(line.candles.lastKey())) {
        return false;
      }
      allBought = allBought == null || line.firstBought.isAfter(allBought) ? line.firstBought : allBought;
    }
    int period = rebalance.period(date);
    return period > rebalance.period(allBought) && period > rebalancedPeriod;
  }

  // 신호 줄 빼고 목표 = 신호 아닌 줄들 사이 처음 비중 × (신호 아닌 줄들 평가 합 + 공용 현금), 넘는 줄을 먼저 팔아 남은 양도세를 내고 모자란 줄을 요청 순서로 남은 공용 현금만큼 삼(1달러 미만 매매는 건너뜀)
  private void rebalance(LocalDate date, BigDecimal fx) {
    rebalancedPeriod = rebalance.period(date);
    BigDecimal plannedSum = targets.stream().map(line -> line.plannedKrw).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal base = targets.stream().map(Line::valueUsd).reduce(freeCash(), BigDecimal::add);
    List<BigDecimal> gaps = targets.stream()
      .map(line -> base.multiply(line.plannedKrw).divide(plannedSum, 8, RoundingMode.HALF_UP).subtract(line.valueUsd()))
      .toList();
    for (int i = 0; i < targets.size(); i++) {
      BigDecimal excess = gaps.get(i).negate();
      if (excess.compareTo(Portfolio.MIN_REBALANCE_USD) >= 0) {
        sellExcess(targets.get(i), excess, date);
      }
    }
    payDueTax(date, fx);
    for (int i = 0; i < targets.size(); i++) {
      BigDecimal spend = gaps.get(i).min(freeCash());
      if (spend.compareTo(Portfolio.MIN_REBALANCE_USD) >= 0) {
        buyShortfall(targets.get(i), spend, date);
      }
    }
  }

  // 넘는 달러만큼 당일 종가에 팔고 판 몫의 원가는 보유 원가 × 판 수량 ÷ 보유 수량
  private void sellExcess(Line line, BigDecimal excessUsd, LocalDate date) {
    BigDecimal price = line.lastClose;
    BigDecimal held = line.holding.shares();
    BigDecimal shares = excessUsd.divide(price, Money.SHARES_SCALE, RoundingMode.HALF_UP).min(held);
    BigDecimal soldCost = line.holding.cost().multiply(shares).divide(held, 8, RoundingMode.HALF_UP);
    sell(line, shares, soldCost, price, TradeReason.REBALANCE_SELL, TransactionTrigger.REBALANCE_SELL, date);
  }

  // 정한 주식을 받은 가격에 팔아 매도 수수료를 뺀 판 돈 · 판 몫의 원가 대비 실현 손익을 남기고 판 돈은 현금(신호 줄은 줄 몫)
  private void sell(Line line, BigDecimal shares, BigDecimal soldCost, BigDecimal price, TradeReason reason,
    String trigger, LocalDate date) {
    credit(line, recordSale(line, shares, soldCost, price, reason, trigger, date, date));
  }

  // 모의 계좌에 판 거래(계획일은 신호 난 날)를 남기고 매도 수수료를 뺀 판 돈
  private BigDecimal recordSale(Line line, BigDecimal shares, BigDecimal soldCost, BigDecimal price,
    TradeReason reason, String trigger, LocalDate planned, LocalDate date) {
    return ledger.sell(line.holding, planned, date, price, shares, soldCost, trigger, reason).getProceeds();
  }

  // 신호 줄의 판 돈을 신호 줄 몫으로 셈
  private void credit(Line line, BigDecimal proceeds) {
    if (line.signal != null) {
      line.share = line.share.add(proceeds);
    }
  }

  // 현금 가운데 신호 줄 몫을 뺀 공용 현금
  private BigDecimal freeCash() {
    return lines.stream().map(line -> line.share).filter(Objects::nonNull)
      .reduce(ledger.cash(), BigDecimal::subtract);
  }

  // 모자란 달러만큼 판매 방식이 정한 줄 · 양을 당일 종가에 팔아 판 돈을 공용 현금에 넣음(당일 일봉과 주식이 있는 줄만)
  private void sellForTax(LocalDate date, BigDecimal fx, BigDecimal shortfallUsd) {
    if (shortfallUsd.signum() <= 0) {
      return;
    }
    Map<String, CapitalGainsTaxCalculator.HoldingKrw> holdings = taxSalePlan.usesGain()
      ? CapitalGainsTaxCalculator.holdingsKrw(ledger.transactions())
      : Map.of();
    List<PortfolioTaxSalePlan.Candidate> candidates = lines.stream().map(line -> {
      boolean sellable = line.holding.held() && line.candles.containsKey(date);
      CapitalGainsTaxCalculator.HoldingKrw holding = sellable ? holdings.get(line.symbol) : null;
      return new PortfolioTaxSalePlan.Candidate(line.valueUsd(), line.plannedKrw, line.taxSaleOrder,
        holding == null ? null : holding.costKrw(), sellable);
    }).toList();
    for (PortfolioTaxSalePlan.Sale sale : taxSalePlan.plan(candidates, shortfallUsd, fx)) {
      sellLineForTax(lines.get(sale.line()), sale.usd(), date);
    }
  }

  // 판 돈(수수료 뺌)이 정한 달러 이상이 되게 당일 종가에 팔고(보유 상한) 판 돈은 공용 현금
  private void sellLineForTax(Line line, BigDecimal usd, LocalDate date) {
    BigDecimal held = line.holding.shares();
    BigDecimal shares = TaxSale.shares(usd, line.lastClose, request.getTradingFeeRate(), held);
    BigDecimal soldCost = line.holding.cost().multiply(shares).divide(held, 8, RoundingMode.HALF_UP);
    recordSale(line, shares, soldCost, line.lastClose, TradeReason.TAX_SALE, TransactionTrigger.TAX_SALE, date, date);
  }

  // 공용 현금으로 못 낸 양도세를 신호 줄 몫에서 줄 순서대로 뺌
  private void takeFromShares(BigDecimal fromShares) {
    for (Line line : lines) {
      if (fromShares.signum() <= 0) {
        return;
      }
      if (line.share != null) {
        BigDecimal used = line.share.min(fromShares);
        line.share = line.share.subtract(used);
        fromShares = fromShares.subtract(used);
      }
    }
  }

  // 공용 현금 달러로 당일 종가에 삼(수수료는 쓴 달러에서 뺌, 새 돈 없음)
  private void buyShortfall(Line line, BigDecimal spendUsd, LocalDate date) {
    bought(line, ledger.buyWithCash(line.holding, date, date, line.lastClose, spendUsd,
      TransactionTrigger.REBALANCE_BUY, TradeReason.REBALANCE_BUY));
  }

  // 평가일 환율로 줄마다 평가 · 현금 · 총 자산을 내고 날마다 가치(당일 환율)와 시간가중 지표를 냄
  Result finish(LocalDate startDate, FxRateDto currentFx) {
    BigDecimal rate = currentFx.rate();
    List<Position> positions = lines.stream()
      .map(line -> new Position(line.symbol, line.type, line.executionTiming(), line.plannedKrw, line.holding.shares(),
        line.lastClose, MoneyUtils.roundKrw(line.valueUsd().multiply(rate))))
      .toList();
    BigDecimal stockValueKrw = positions.stream().map(Position::valueKrw).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal cashKrw = MoneyUtils.roundKrw(ledger.cash().multiply(rate));
    BigDecimal totalAssetKrw = stockValueKrw.add(cashKrw);
    List<StrategyTransaction> transactions = ledger.transactions();
    List<LocalDate> defaultFxDates = Stream.concat(transactions.stream().map(StrategyTransaction::getDefaultFxDate),
        Stream.of(currentFx.defaulted() ? currentFx.date() : null))
      .filter(Objects::nonNull)
      .distinct()
      .sorted()
      .toList();
    return new Result(startDate, valuationDate, List.copyOf(transactions), List.copyOf(dividends), positions,
      invested, ledger.cash(), cashKrw, stockValueKrw, totalAssetKrw, rate, performance(totalAssetKrw, rate),
      taxPayer.payments(), defaultFxDates);
  }

  // 산 날부터 일별 가치 · 지표(산 것이 없으면 일별 가치 없음 · 지표 null)
  private StrategyPerformance.Result performance(BigDecimal totalAssetKrw, BigDecimal rate) {
    if (!bought) {
      return new StrategyPerformance.Result(List.of(), null, null);
    }
    List<StrategyTransaction> transactions = ledger.transactions();
    Function<LocalDate, BigDecimal> fxPath = StrategyFinalizer.fxPath(transactions, valuationDate,
      request.getPurchaseFxRate(), request.getCurrentFxRate(), rate, fxRates);
    List<StrategyPerformance.Point> points = new ArrayList<>();
    for (Day day : days) {
      BigDecimal fx = fxPath.apply(day.date());
      BigDecimal valueUsd = MoneyUtils.roundUsd(day.rawUsd());
      points.add(new StrategyPerformance.Point(new DailyValue(day.date(), valueUsd, DailyValue.toKrw(valueUsd, fx)),
        day.rawUsd(), day.depositUsd(), fx));
    }
    BigDecimal lastRawUsd = totalAssetKrw.divide(rate, MathContext.DECIMAL64);
    points.add(new StrategyPerformance.Point(
      new DailyValue(valuationDate, lastRawUsd.setScale(2, RoundingMode.HALF_UP), totalAssetKrw), lastRawUsd,
      pendingDepositUsd, rate));
    BigDecimal firstFx = transactions.stream()
      .filter(tx -> Decimals.isPositive(tx.getAmount()))
      .findFirst()
      .map(StrategyTransaction::getFxRate)
      .orElse(rate);
    return StrategyPerformance.measure(points, firstFx, request.getStartDate());
  }

  // 줄들의 쓸 수 있는 일봉 날짜를 합친 날
  static NavigableSet<LocalDate> dates(List<Line> lines) {
    NavigableSet<LocalDate> dates = new TreeSet<>();
    lines.forEach(line -> dates.addAll(line.candles.keySet()));
    return dates;
  }

  // 종목 줄 하나의 전략 · 일봉 · 회차 · 배당 · 익절 · 손절 · 신호 · 체결 시점 · 판매 순서와 보유 주식 · 원가 · 당일까지 마지막 종가 · 첫 매수일 · 신호 줄 몫 현금
  static final class Line {
    private final String symbol;
    private final PortfolioStrategyType type;
    private final BigDecimal plannedKrw;
    private final NavigableMap<LocalDate, OHLCPriceDto> candles;
    private final Map<LocalDate, LocalDate> rounds;
    private final List<DividendPayment> dividends;
    private final TakeProfitStopLoss exits;
    private final SignalHolding signal;
    private final boolean nextOpen;
    private final Integer taxSaleOrder;
    private final TradeLedger.Holding holding;
    private int nextDividend;
    private BigDecimal lastClose;
    private LocalDate firstBought;
    // 현금 가운데 이 신호 줄 몫(USD, 첫 매수 전은 null)
    private BigDecimal share;
    // 다음 거래일 시가에 체결할 신호(없으면 null)
    private SignalOrder order;

    Line(String symbol, PortfolioStrategyType type, BigDecimal plannedKrw,
      NavigableMap<LocalDate, OHLCPriceDto> candles, Map<LocalDate, LocalDate> rounds, List<DividendPayment> dividends,
      TakeProfitStopLoss exits, SignalHolding signal, boolean nextOpen, Integer taxSaleOrder) {
      this.symbol = symbol;
      this.type = type;
      this.plannedKrw = plannedKrw;
      this.candles = candles;
      this.rounds = rounds;
      this.dividends = dividends;
      this.exits = exits;
      this.signal = signal;
      this.nextOpen = nextOpen;
      this.taxSaleOrder = taxSaleOrder;
      this.holding = new TradeLedger.Holding(symbol);
    }

    // 이동평균 · 밴드 줄의 체결 시점(한 번에 사기 · 적립식 줄은 null)
    private ExecutionTiming executionTiming() {
      if (signal == null) {
        return null;
      }
      return nextOpen ? ExecutionTiming.NEXT_DAY_OPEN : ExecutionTiming.SAME_DAY_CLOSE;
    }

    // 보유 × 마지막 종가(USD, 첫 일봉 전은 0)
    private BigDecimal valueUsd() {
      return lastClose == null ? BigDecimal.ZERO : holding.shares().multiply(lastClose);
    }
  }

  // 평가일 전 하루의 반올림 전 가치(USD)와 앞 날 다음부터 당일까지의 입금(USD)
  private record Day(LocalDate date, BigDecimal rawUsd, BigDecimal depositUsd) {}
}
