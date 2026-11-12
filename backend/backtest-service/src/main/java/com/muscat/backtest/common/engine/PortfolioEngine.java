package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.calculation.CapitalGainsTaxCalculator;
import com.muscat.backtest.common.constants.BacktestConstants.Money;
import com.muscat.backtest.common.constants.BacktestConstants.Portfolio;
import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.PortfolioMethod;
import com.muscat.backtest.common.enums.type.PortfolioRebalance;
import com.muscat.backtest.common.enums.type.PortfolioStrategyType;
import com.muscat.backtest.common.enums.type.TradeReason;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.common.util.RoundPurchase;
import com.muscat.backtest.domain.dto.request.PortfolioAsset;
import com.muscat.backtest.domain.dto.request.PortfolioRequest;
import com.muscat.backtest.domain.dto.request.PortfolioStrategy;
import com.muscat.backtest.domain.dto.response.DailyValue;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.domain.strategy.DcaSchedule;
import com.muscat.backtest.domain.strategy.SignalHolding;
import com.muscat.backtest.domain.strategy.TakeProfitStopLoss;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto.DividendPayment;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 포트폴리오 엔진: 여러 종목을 현금 하나(USD)로 날마다 양도세 납부 → 배당 → 익절 · 손절 → 이동평균 · 밴드 줄 신호 매매 → 회차 매수 → 리밸런싱 → 기간 끝 매도 → 평가 순서로 돌림
@Component
@RequiredArgsConstructor
@Slf4j
public class PortfolioEngine {

  private final MarketDataClientWrapper marketData;

  /** 받은 세후 배당(USD)과 배당락일 환율 */
  public record Dividend(String symbol, LocalDate exDate, BigDecimal amountUsd, BigDecimal fxRate) {}

  /** 요청 순서 줄마다 전략 · 줄 금액 · 평가일 보유 주식 수 · 종가(USD) · 평가 원화 */
  public record Position(String symbol, PortfolioStrategyType strategyType, BigDecimal plannedKrw, BigDecimal shares,
                         BigDecimal closeUsd, BigDecimal valueKrw) {}

  /** 기간 첫 거래일 · 평가일 · 거래 · 배당 · 줄마다 보유 · 합계 · 일별 가치와 지표 · 양도세 납부 · 기본 환율 날 */
  public record Result(LocalDate startDate, LocalDate valuationDate, List<StrategyTransaction> transactions,
                       List<Dividend> dividends, List<Position> positions, BigDecimal totalInvestedKrw,
                       BigDecimal cashUsd, BigDecimal cashKrw, BigDecimal stockValueKrw, BigDecimal totalAssetKrw,
                       BigDecimal currentFxRate, StrategyPerformance.Result performance,
                       CapitalGainsTaxCalculator.Payments taxPayments, List<LocalDate> defaultFxDates) {}

  // 줄마다 일봉 · 회차 · 배당을 받아 줄들의 쓸 수 있는 일봉 날짜를 합친 날마다 돌리고 평가일 환율로 평가
  public Result run(PortfolioRequest request) {
    LocalDate today = LocalDate.now();
    LocalDate valuationDate = request.getEndDate().isAfter(today) ? today : request.getEndDate();
    log.info("포트폴리오 실행 시작: {}개 종목 · {} · {} ~ {}", request.getAssets().size(),
      PortfolioMethod.orLumpSum(request.getMethod()), request.getStartDate(), valuationDate);

    List<Line> lines = request.getAssets().stream().map(asset -> line(request, asset, valuationDate)).toList();
    NavigableSet<LocalDate> dates = new TreeSet<>();
    lines.forEach(line -> dates.addAll(line.candles.keySet()));
    FxRates fxRates = request.getPurchaseFxRate() == null
      ? BacktestDataUtils.buildDailyFxRateMap(marketData, dates.first(), valuationDate)
      : FxRates.EMPTY;
    FxRateDto currentFx = request.getCurrentFxRate() != null
      ? new FxRateDto(valuationDate, request.getCurrentFxRate())
      : BacktestDataUtils.getFxRateAt(marketData, valuationDate);

    Run run = new Run(request, lines, fxRates, valuationDate);
    dates.forEach(run::day);
    // 마지막 일봉 뒤 평가일까지 배당락일이 온 배당은 현금으로
    run.receiveDividends(valuationDate, false);
    Result result = run.finish(dates.first(), currentFx);
    log.info("포트폴리오 실행 완료: 거래 {}건, 넣은 돈 {}원, 총 자산 {}원", result.transactions().size(),
      result.totalInvestedKrw(), result.totalAssetKrw());
    return result;
  }

  // 줄의 전략 · 기간 안 쓸 수 있는 일봉 · 매수 회차 · 배당락일 순서 배당 · 익절 · 손절 · 신호(일봉이 없으면 DATA_NOT_FOUND)
  private Line line(PortfolioRequest request, PortfolioAsset asset, LocalDate valuationDate) {
    String symbol = asset.normalizedSymbol();
    PortfolioStrategyType type = request.strategyOf(asset);
    LocalDate start = request.getStartDate();
    int window = signalWindow(type, asset.strategy());
    Map<LocalDate, OHLCPriceDto> priceMap = BacktestDataUtils.buildPriceMap(marketData, symbol,
      window > 0 ? start.minusDays(SignalHolding.lookbackDays(window)) : start, valuationDate);
    NavigableMap<LocalDate, OHLCPriceDto> candles = new TreeMap<>();
    priceMap.values().stream()
      .filter(PriceLookup::isUsable)
      .filter(candle -> !candle.date().isBefore(start))
      .forEach(candle -> candles.put(candle.date(), candle));
    if (candles.isEmpty()) {
      throw new BacktestException(BacktestResponse.DATA_NOT_FOUND, symbol + " 의 기간 안 시세가 없습니다");
    }
    List<DividendPayment> dividends = BacktestDataUtils.getDividendHistory(marketData, symbol, request.getStartDate(),
        valuationDate).getDividends().stream()
      .filter(dividend -> dividend.getExDate() != null)
      .sorted(Comparator.comparing(DividendPayment::getExDate))
      .toList();
    SignalHolding signal = signal(type, asset.strategy(), priceMap, start);
    return new Line(symbol, type, request.amountOf(asset), candles,
      signal == null ? rounds(request, type, candles, valuationDate) : Map.of(), dividends,
      TakeProfitStopLoss.of(asset.takeProfitRate(), asset.stopLossRate()), signal);
  }

  // 신호 줄의 신호 기간(이동평균 교차는 긴 기간, 밴드는 평균 기간, 다른 줄은 0)
  private static int signalWindow(PortfolioStrategyType type, PortfolioStrategy strategy) {
    return switch (type) {
      case MA_CROSS -> strategy.longWindowOrDefault();
      case MA_BAND -> strategy.bandWindowOrDefault();
      case LUMP_SUM, DCA -> 0;
    };
  }

  // 시작일 전 신호 기간만큼 더 받은 일봉의 이동평균 교차 · 밴드 신호(다른 줄은 null)
  private static SignalHolding signal(PortfolioStrategyType type, PortfolioStrategy strategy,
    Map<LocalDate, OHLCPriceDto> priceMap, LocalDate start) {
    return switch (type) {
      case MA_CROSS -> SignalHolding.maCross(priceMap, strategy.shortWindowOrDefault(), strategy.longWindowOrDefault(),
        start);
      case MA_BAND -> SignalHolding.maBand(priceMap, strategy.bandWindowOrDefault(), strategy.upperRateOrDefault(),
        strategy.lowerRateOrDefault(), start);
      case LUMP_SUM, DCA -> null;
    };
  }

  // 사는 거래일 → 계획일(한 번에 사기 줄은 기간 안 첫 거래일 하나, 적립식 줄은 요청의 주기로 쓸 거래일이 있는 회차)
  private static Map<LocalDate, LocalDate> rounds(PortfolioRequest request, PortfolioStrategyType type,
    NavigableMap<LocalDate, OHLCPriceDto> candles, LocalDate valuationDate) {
    if (type == PortfolioStrategyType.LUMP_SUM) {
      return Map.of(candles.firstKey(), candles.firstKey());
    }
    LocalDate first = request.getFirstPurchaseDate() != null ? request.getFirstPurchaseDate() : request.getStartDate();
    int purchaseDay = request.getPurchaseDay() != null ? request.getPurchaseDay() : first.getDayOfMonth();
    int interval = request.getInvestmentInterval() != null ? request.getInvestmentInterval() : 1;
    Map<LocalDate, LocalDate> rounds = new TreeMap<>();
    DcaSchedule.rounds(candles, frequency(request), first, valuationDate, purchaseDay, interval).stream()
      .filter(round -> round.trade() != null)
      .forEach(round -> rounds.putIfAbsent(round.trade().date(), round.planned()));
    return rounds;
  }

  // 적립식 주기(비우면 매달)
  private static DcaFrequency frequency(PortfolioRequest request) {
    return request.getFrequency() != null ? request.getFrequency() : DcaFrequency.MONTHLY;
  }

  // 종목 줄 하나의 전략 · 일봉 · 회차 · 배당 · 익절 · 손절 · 신호와 보유 주식 · 원가(USD, 수수료 포함) · 그 날짜까지 마지막 종가 · 첫 매수일 · 신호 줄 몫 현금
  private static final class Line {
    private final String symbol;
    private final PortfolioStrategyType type;
    private final BigDecimal plannedKrw;
    private final NavigableMap<LocalDate, OHLCPriceDto> candles;
    private final Map<LocalDate, LocalDate> rounds;
    private final List<DividendPayment> dividends;
    private final TakeProfitStopLoss exits;
    private final SignalHolding signal;
    private int nextDividend;
    private BigDecimal shares = BigDecimal.ZERO;
    private BigDecimal cost = BigDecimal.ZERO;
    private BigDecimal lastClose;
    private LocalDate firstBought;
    // 현금 가운데 이 신호 줄 몫(USD, 첫 매수 전은 null)
    private BigDecimal share;

    private Line(String symbol, PortfolioStrategyType type, BigDecimal plannedKrw,
      NavigableMap<LocalDate, OHLCPriceDto> candles, Map<LocalDate, LocalDate> rounds, List<DividendPayment> dividends,
      TakeProfitStopLoss exits, SignalHolding signal) {
      this.symbol = symbol;
      this.type = type;
      this.plannedKrw = plannedKrw;
      this.candles = candles;
      this.rounds = rounds;
      this.dividends = dividends;
      this.exits = exits;
      this.signal = signal;
    }

    // 보유 × 마지막 종가(USD, 첫 일봉 전은 0)
    private BigDecimal valueUsd() {
      return lastClose == null ? BigDecimal.ZERO : shares.multiply(lastClose);
    }
  }

  // 평가일 전 하루의 반올림 전 가치(USD)와 앞 날 다음부터 그 날짜까지의 입금(USD)
  private record Day(LocalDate date, BigDecimal rawUsd, BigDecimal depositUsd) {}

  // 한 번 돌리는 동안의 현금(USD, 신호 줄 몫 포함) · 거래 · 배당 · 넣은 돈 · 일별 가치 · 마지막으로 리밸런싱한 주기
  private static final class Run {
    private final PortfolioRequest request;
    private final List<Line> lines;
    private final List<Line> targets;
    private final FxRates fxRates;
    private final LocalDate valuationDate;
    private final CapitalGainsTaxPayer taxPayer;
    private final PortfolioRebalance rebalance;
    private final List<StrategyTransaction> transactions = new ArrayList<>();
    private final List<Dividend> dividends = new ArrayList<>();
    private final List<Day> days = new ArrayList<>();
    private BigDecimal cash = BigDecimal.ZERO;
    private BigDecimal invested = BigDecimal.ZERO;
    private BigDecimal pendingDepositUsd = BigDecimal.ZERO;
    private boolean bought;
    private int rebalancedPeriod = Integer.MIN_VALUE;

    private Run(PortfolioRequest request, List<Line> lines, FxRates fxRates, LocalDate valuationDate) {
      this.request = request;
      this.lines = lines;
      this.targets = lines.stream().filter(line -> line.signal == null).toList();
      this.fxRates = fxRates;
      this.valuationDate = valuationDate;
      this.taxPayer = new CapitalGainsTaxPayer(Boolean.TRUE.equals(request.getCapitalGainsTax()));
      this.rebalance = PortfolioRebalance.orNone(request.getRebalance());
    }

    // 양도세 납부 → 배당 → 익절 · 손절 → 신호 매매 → 회차 매수 → 리밸런싱 → 기간 끝 매도 → 평가(평가일은 마무리에서)
    private void day(LocalDate date) {
      BigDecimal fx = fxOn(date);
      lines.forEach(line -> {
        OHLCPriceDto candle = line.candles.get(date);
        if (candle != null) {
          line.lastClose = PriceLookup.effectiveClose(candle);
        }
      });
      StrategyTransaction payment = taxPayer.pay(date, transactions, cash, fx, fxRates.isDefault(date));
      if (payment != null) {
        payment.setReason(TradeReason.TAX_PAYMENT);
        transactions.add(payment);
        payTax(payment.getTaxPaid());
      }
      receiveDividends(date, Boolean.TRUE.equals(request.getReinvestDividends()));
      lines.forEach(line -> sellOrFollow(line, date, fx));
      for (Line line : lines) {
        LocalDate planned = line.rounds.get(date);
        if (planned != null) {
          buy(line, date, planned, fx);
        }
      }
      if (rebalanceDue(date)) {
        rebalance(date, fx);
      }
      if (PeriodEnd.sells(request.getPeriodEnd())) {
        lines.stream().filter(line -> date.equals(line.candles.lastKey())).forEach(line -> sellAtEnd(line, date, fx));
      }
      if (bought && date.isBefore(valuationDate)) {
        BigDecimal rawUsd = lines.stream().map(Line::valueUsd).reduce(cash, BigDecimal::add);
        days.add(new Day(date, rawUsd, pendingDepositUsd));
        pendingDepositUsd = BigDecimal.ZERO;
      }
    }

    // 배당락일이 그 날짜까지 온 배당을 전날까지 든 주식으로 받아 세후 달러를 현금에, 재투자면 그 날짜 그 종목 종가로 삼
    private void receiveDividends(LocalDate date, boolean reinvest) {
      for (Line line : lines) {
        while (line.nextDividend < line.dividends.size()
          && !line.dividends.get(line.nextDividend).getExDate().isAfter(date)) {
          DividendPayment payment = line.dividends.get(line.nextDividend++);
          if (payment.getAmount() == null) {
            continue;
          }
          BigDecimal gross = payment.getAmount().multiply(line.shares).setScale(2, RoundingMode.HALF_UP);
          BigDecimal afterTax = gross.subtract(withholding(gross));
          if (!Decimals.isPositive(afterTax)) {
            continue;
          }
          LocalDate exDate = payment.getExDate();
          BigDecimal fx = fxOn(exDate);
          dividends.add(new Dividend(line.symbol, exDate, afterTax, fx));
          cash = cash.add(afterTax);
          OHLCPriceDto candle = line.candles.get(date);
          if (reinvest && candle != null) {
            reinvest(line, exDate, candle, afterTax, fx);
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

    // 받은 배당 전부로 그 일봉 종가에 삼(수수료 없음)
    private void reinvest(Line line, LocalDate exDate, OHLCPriceDto candle, BigDecimal usd, BigDecimal fx) {
      BigDecimal price = PriceLookup.effectiveClose(candle);
      StrategyTransaction tx = StrategyTransaction.builder()
        .symbol(line.symbol)
        .reason(TradeReason.DIVIDEND_REINVESTMENT)
        .date(exDate)
        .actualDate(candle.date())
        .price(price)
        .shares(usd.divide(price, 8, RoundingMode.HALF_UP))
        .amount(BigDecimal.ZERO)
        .cashUsed(usd)
        .fxRate(fx)
        .defaultFxDate(defaultFx(exDate))
        .trigger(TransactionTrigger.DIVIDEND_REINVESTMENT)
        .build();
      record(line, tx);
    }

    // 줄 금액만큼 그 날짜 종가에 삼, 공용 현금(신호 줄 몫 빼고)을 먼저 쓰고 모자란 만큼 새 돈(원 단위)
    private void buy(Line line, LocalDate date, LocalDate planned, BigDecimal fx) {
      boolean lumpSum = line.type == PortfolioStrategyType.LUMP_SUM;
      BigDecimal price = PriceLookup.effectiveClose(line.candles.get(date));
      StrategyTransaction.StrategyTransactionBuilder builder = StrategyTransaction.builder()
        .symbol(line.symbol)
        .reason(lumpSum ? TradeReason.BUY : TradeReason.DCA_BUY)
        .date(planned)
        .actualDate(date)
        .price(price)
        .fxRate(fx)
        .defaultFxDate(defaultFx(date))
        .trigger(lumpSum ? TransactionTrigger.LUMP_SUM_PURCHASE : frequency(request).trigger());
      BigDecimal amount = line.plannedKrw;
      BigDecimal free = freeCash();
      if (!Decimals.isPositive(free)) {
        RoundPurchase purchase = RoundPurchase.byAmount(amount, price, fx, request.getTradingFeeRate());
        record(line, builder.shares(purchase.shares()).fee(purchase.feeUsd()).amount(purchase.amountKrw()).build());
        return;
      }
      BigDecimal needUsd = amount.divide(fx, 8, RoundingMode.HALF_UP);
      BigDecimal cashUsed = free.min(needUsd);
      BigDecimal deposit = free.compareTo(needUsd) >= 0
        ? BigDecimal.ZERO
        : MoneyUtils.roundKrw(amount.subtract(free.multiply(fx))).max(BigDecimal.ZERO);
      BigDecimal spendUsd = cashUsed.add(deposit.divide(fx, 8, RoundingMode.HALF_UP));
      BigDecimal fee = BacktestCalculationUtils.calculateTradingFee(spendUsd, request.getTradingFeeRate());
      record(line, builder.shares(spendUsd.subtract(fee).divide(price, 8, RoundingMode.HALF_UP)).fee(fee)
        .amount(deposit).cashUsed(cashUsed).build());
    }

    // 매수 거래를 남기고 현금 · 줄 보유 · 원가 · 넣은 돈 · 첫 매수일에 반영
    private void record(Line line, StrategyTransaction tx) {
      transactions.add(tx);
      if (tx.getCashUsed() != null) {
        cash = cash.subtract(tx.getCashUsed());
      }
      if (line.firstBought == null) {
        line.firstBought = tx.tradedOn();
      }
      line.shares = line.shares.add(tx.getShares());
      line.cost = line.cost.add(tx.costUsd());
      invested = invested.add(tx.getAmount());
      pendingDepositUsd = pendingDepositUsd.add(tx.getAmount().divide(tx.getFxRate(), MathContext.DECIMAL64));
      bought = true;
    }

    // 그 날짜 일봉이 있는 줄의 익절 · 손절, 신호 줄은 그 뒤 신호대로 전량 팔거나 삼(판 날은 사지 않고 익절 · 손절로 다 팔면 다음 매수 신호까지 기다림)
    private void sellOrFollow(Line line, LocalDate date, BigDecimal fx) {
      OHLCPriceDto candle = line.candles.get(date);
      if (candle == null) {
        return;
      }
      boolean wanted = line.signal != null && line.signal.wanted(date);
      if (sellIfTriggered(line, candle, date, fx)) {
        if (line.signal != null) {
          line.signal.soldOut();
        }
        return;
      }
      if (line.signal == null) {
        return;
      }
      boolean holding = line.shares.signum() > 0;
      if (holding && !wanted) {
        sell(line, line.shares, line.cost, line.lastClose, TradeReason.STRATEGY_SELL, line.signal.sellTrigger(), date,
          fx);
      } else if (!holding && wanted && (line.share == null || line.share.signum() > 0)) {
        buyOnSignal(line, date, fx);
      }
    }

    // 일봉이 줄의 평균 단가 대비 익절 · 손절 기준가에 닿으면 그 값에 전량 팖(판 날 true)
    private boolean sellIfTriggered(Line line, OHLCPriceDto candle, LocalDate date, BigDecimal fx) {
      if (line.shares.signum() <= 0) {
        return false;
      }
      TakeProfitStopLoss.Exit exit = line.exits.check(candle, line.shares, line.cost);
      if (exit == null) {
        return false;
      }
      TradeReason reason = exit.takeProfit() ? TradeReason.TAKE_PROFIT : TradeReason.STOP_LOSS;
      sell(line, line.shares, line.cost, exit.price(), reason, exit.trigger(), date, fx);
      return true;
    }

    // 신호 줄 매수: 첫 매수는 줄 금액을 새 돈으로, 그 뒤는 줄 몫 현금 전부로 그 날짜 종가에 삼
    private void buyOnSignal(Line line, LocalDate date, BigDecimal fx) {
      BigDecimal price = line.lastClose;
      StrategyTransaction.StrategyTransactionBuilder builder = StrategyTransaction.builder()
        .symbol(line.symbol)
        .reason(TradeReason.STRATEGY_BUY)
        .date(date)
        .actualDate(date)
        .price(price)
        .fxRate(fx)
        .defaultFxDate(defaultFx(date))
        .trigger(line.signal.buyTrigger());
      if (line.share == null) {
        RoundPurchase purchase = RoundPurchase.byAmount(line.plannedKrw, price, fx, request.getTradingFeeRate());
        record(line, builder.shares(purchase.shares()).fee(purchase.feeUsd()).amount(purchase.amountKrw()).build());
      } else {
        BigDecimal fee = BacktestCalculationUtils.calculateTradingFee(line.share, request.getTradingFeeRate());
        record(line, builder.shares(line.share.subtract(fee).divide(price, 8, RoundingMode.HALF_UP)).fee(fee)
          .amount(BigDecimal.ZERO).cashUsed(line.share).build());
      }
      line.share = BigDecimal.ZERO;
    }

    // 줄의 마지막 거래일 종가에 남은 주식 전량을 팔고 판 돈을 현금에 넣음(남은 주식이 없으면 그대로)
    private void sellAtEnd(Line line, LocalDate date, BigDecimal fx) {
      StrategyTransaction sale = PeriodEndSale.of(line.shares, line.candles, valuationDate, line.lastClose,
        request.getTradingFeeRate(), fx);
      if (sale == null) {
        return;
      }
      sale.setSymbol(line.symbol);
      sale.setReason(TradeReason.PERIOD_END_SALE);
      sale.setRealizedProfit(sale.getProceeds().subtract(line.cost));
      sale.setDefaultFxDate(saleDefaultFx(date));
      transactions.add(sale);
      credit(line, sale.getProceeds());
      line.shares = BigDecimal.ZERO;
      line.cost = BigDecimal.ZERO;
    }

    // 신호 줄 빼고 모든 줄의 처음 매수가 끝난 주기 다음 주기부터 주기마다 한 번, 그 줄들이 모두 그 날짜 일봉이 있고 기간 끝에 파는 날이 아닌 첫 날
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

    // 신호 줄 빼고 목표 = 그 줄들 사이 처음 비중 × (그 줄들 평가 합 + 공용 현금), 넘는 줄을 먼저 팔고 모자란 줄을 요청 순서로 남은 공용 현금만큼 삼(1달러 미만 매매는 건너뜀)
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
          sellExcess(targets.get(i), excess, date, fx);
        }
      }
      for (int i = 0; i < targets.size(); i++) {
        BigDecimal spend = gaps.get(i).min(freeCash());
        if (spend.compareTo(Portfolio.MIN_REBALANCE_USD) >= 0) {
          buyShortfall(targets.get(i), spend, date, fx);
        }
      }
    }

    // 넘는 달러만큼 그 날짜 종가에 팔고 판 몫의 원가는 보유 원가 × 판 수량 ÷ 보유 수량
    private void sellExcess(Line line, BigDecimal excessUsd, LocalDate date, BigDecimal fx) {
      BigDecimal price = line.lastClose;
      BigDecimal shares = excessUsd.divide(price, Money.SHARES_SCALE, RoundingMode.HALF_UP).min(line.shares);
      BigDecimal soldCost = line.cost.multiply(shares).divide(line.shares, 8, RoundingMode.HALF_UP);
      sell(line, shares, soldCost, price, TradeReason.REBALANCE_SELL, TransactionTrigger.REBALANCE_SELL, date, fx);
    }

    // 정한 주식을 그 값에 팔아 매도 수수료를 뺀 판 돈 · 판 몫의 원가 대비 실현 손익을 남기고 판 돈은 현금(신호 줄은 줄 몫)
    private void sell(Line line, BigDecimal shares, BigDecimal soldCost, BigDecimal price, TradeReason reason,
      String trigger, LocalDate date, BigDecimal fx) {
      BigDecimal gross = shares.multiply(price).setScale(8, RoundingMode.HALF_UP);
      BigDecimal fee = BacktestCalculationUtils.calculateTradingFee(gross, request.getTradingFeeRate());
      BigDecimal proceeds = gross.subtract(fee);
      transactions.add(StrategyTransaction.builder()
        .symbol(line.symbol)
        .reason(reason)
        .date(date)
        .actualDate(date)
        .price(price)
        .shares(shares.negate())
        .fee(fee)
        .amount(BigDecimal.ZERO)
        .proceeds(proceeds)
        .realizedProfit(proceeds.subtract(soldCost))
        .fxRate(fx)
        .defaultFxDate(saleDefaultFx(date))
        .trigger(trigger)
        .build());
      credit(line, proceeds);
      line.shares = line.shares.subtract(shares);
      line.cost = line.cost.subtract(soldCost);
    }

    // 판 돈을 현금에 넣음(신호 줄은 그 줄 몫으로도 셈)
    private void credit(Line line, BigDecimal proceeds) {
      cash = cash.add(proceeds);
      if (line.signal != null) {
        line.share = line.share.add(proceeds);
      }
    }

    // 현금 가운데 신호 줄 몫을 뺀 공용 현금
    private BigDecimal freeCash() {
      return lines.stream().map(line -> line.share).filter(Objects::nonNull).reduce(cash, BigDecimal::subtract);
    }

    // 낸 양도세를 공용 현금에서 먼저, 모자라면 신호 줄 몫에서 줄 순서대로 뺌
    private void payTax(BigDecimal paid) {
      BigDecimal fromShares = paid.subtract(freeCash()).max(BigDecimal.ZERO);
      cash = cash.subtract(paid);
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

    // 공용 현금 달러로 그 날짜 종가에 삼(수수료는 쓴 달러에서 뺌, 새 돈 없음)
    private void buyShortfall(Line line, BigDecimal spendUsd, LocalDate date, BigDecimal fx) {
      BigDecimal price = line.lastClose;
      BigDecimal fee = BacktestCalculationUtils.calculateTradingFee(spendUsd, request.getTradingFeeRate());
      record(line, StrategyTransaction.builder()
        .symbol(line.symbol)
        .reason(TradeReason.REBALANCE_BUY)
        .date(date)
        .actualDate(date)
        .price(price)
        .shares(spendUsd.subtract(fee).divide(price, 8, RoundingMode.HALF_UP))
        .fee(fee)
        .amount(BigDecimal.ZERO)
        .cashUsed(spendUsd)
        .fxRate(fx)
        .defaultFxDate(defaultFx(date))
        .trigger(TransactionTrigger.REBALANCE_BUY)
        .build());
    }

    // 그 날짜 환율(수동 환율이 있으면 그 값)
    private BigDecimal fxOn(LocalDate date) {
      return request.getPurchaseFxRate() != null ? request.getPurchaseFxRate() : fxRates.get(date);
    }

    // 산 날의 기본 환율 날(아니면 null)
    private LocalDate defaultFx(LocalDate date) {
      return fxRates.isDefault(date) ? date : null;
    }

    // 판 날의 기본 환율 날(양도세를 켰을 때만)
    private LocalDate saleDefaultFx(LocalDate date) {
      return Boolean.TRUE.equals(request.getCapitalGainsTax()) ? defaultFx(date) : null;
    }

    // 평가일 환율로 줄마다 평가 · 현금 · 총 자산을 내고 날마다 가치(그 날짜 환율)와 시간가중 지표를 냄
    private Result finish(LocalDate startDate, FxRateDto currentFx) {
      BigDecimal rate = currentFx.rate();
      List<Position> positions = lines.stream()
        .map(line -> new Position(line.symbol, line.type, line.plannedKrw, line.shares, line.lastClose,
          MoneyUtils.roundKrw(line.valueUsd().multiply(rate))))
        .toList();
      BigDecimal stockValueKrw = positions.stream().map(Position::valueKrw).reduce(BigDecimal.ZERO, BigDecimal::add);
      BigDecimal cashKrw = MoneyUtils.roundKrw(cash.multiply(rate));
      BigDecimal totalAssetKrw = stockValueKrw.add(cashKrw);
      List<LocalDate> defaultFxDates = Stream.concat(transactions.stream().map(StrategyTransaction::getDefaultFxDate),
          Stream.of(currentFx.defaulted() ? currentFx.date() : null))
        .filter(Objects::nonNull)
        .distinct()
        .sorted()
        .toList();
      return new Result(startDate, valuationDate, List.copyOf(transactions), List.copyOf(dividends), positions,
        invested, cash, cashKrw, stockValueKrw, totalAssetKrw, rate, performance(totalAssetKrw, rate),
        taxPayer.payments(), defaultFxDates);
    }

    // 산 날부터 일별 가치 · 지표(산 것이 없으면 일별 가치 없음 · 지표 null)
    private StrategyPerformance.Result performance(BigDecimal totalAssetKrw, BigDecimal rate) {
      if (!bought) {
        return new StrategyPerformance.Result(List.of(), null, null);
      }
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
  }
}
