package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.calculation.CapitalGainsTaxCalculator;
import com.muscat.backtest.common.constants.BacktestConstants.Money;
import com.muscat.backtest.common.constants.BacktestConstants.Portfolio;
import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.ExecutionTiming;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.PortfolioMethod;
import com.muscat.backtest.common.enums.type.PortfolioRebalance;
import com.muscat.backtest.common.enums.type.PortfolioStrategyType;
import com.muscat.backtest.common.enums.type.PortfolioTaxSale;
import com.muscat.backtest.common.enums.type.TaxSale;
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
import com.muscat.backtest.domain.strategy.SignalOrder;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 포트폴리오 엔진: 여러 종목을 현금 하나(USD)로 날마다 신호 줄 시가 매도 → 양도세 납부(모자라면 판매 방식대로 매도) → 배당 → 신호 줄 시가 매수 → 익절 · 손절 → 이동평균 · 밴드 줄 신호 매매 → 회차 매수 → 리밸런싱 → 연말 정리 → 기간 끝 매도 → 평가 순서로 돌림
@Component
@RequiredArgsConstructor
@Slf4j
public class PortfolioEngine {

  private final MarketDataClientWrapper marketData;

  /** 받은 세후 배당(USD)과 배당락일 환율 */
  public record Dividend(String symbol, LocalDate exDate, BigDecimal amountUsd, BigDecimal fxRate) {}

  /** 요청 순서 줄마다 전략 · 체결 시점(이동평균 · 밴드 줄만) · 줄 금액 · 평가일 보유 주식 수 · 종가(USD) · 평가 원화 */
  public record Position(String symbol, PortfolioStrategyType strategyType, ExecutionTiming executionTiming,
                         BigDecimal plannedKrw, BigDecimal shares, BigDecimal closeUsd, BigDecimal valueKrw) {}

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

    Set<LocalDate> harvestDates = YearEndHarvest.enabled(request.getCapitalGainsTax(), request.getYearEndHarvest())
      ? YearEndHarvest.dates(dates, request.getStartDate(), valuationDate)
      : Set.of();
    Run run = new Run(request, lines, fxRates, valuationDate, harvestDates);
    dates.forEach(run::day);
    // 마지막 일봉 뒤 평가일까지 배당락일이 온 배당은 현금으로
    run.receiveDividends(valuationDate, false, Map.of());
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
    boolean nextOpen = signal != null && ExecutionTiming.nextDayOpen(asset.strategy().executionTiming());
    return new Line(symbol, type, request.amountOf(asset), candles,
      signal == null ? rounds(request, type, candles, valuationDate) : Map.of(), dividends,
      TakeProfitStopLoss.of(asset.takeProfitRate(), asset.stopLossRate()), signal, nextOpen, asset.taxSaleOrder());
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

  // 종목 줄 하나의 전략 · 일봉 · 회차 · 배당 · 익절 · 손절 · 신호 · 체결 시점 · 판매 순서와 보유 주식 · 원가(USD, 수수료 포함) · 그 날짜까지 마지막 종가 · 첫 매수일 · 신호 줄 몫 현금
  private static final class Line {
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
    private int nextDividend;
    private BigDecimal shares = BigDecimal.ZERO;
    private BigDecimal cost = BigDecimal.ZERO;
    private BigDecimal lastClose;
    private LocalDate firstBought;
    // 현금 가운데 이 신호 줄 몫(USD, 첫 매수 전은 null)
    private BigDecimal share;
    // 다음 거래일 시가에 체결할 신호(없으면 null)
    private SignalOrder order;

    private Line(String symbol, PortfolioStrategyType type, BigDecimal plannedKrw,
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
    }

    // 이동평균 · 밴드 줄의 체결 시점(다른 줄은 null)
    private ExecutionTiming executionTiming() {
      if (signal == null) {
        return null;
      }
      return nextOpen ? ExecutionTiming.NEXT_DAY_OPEN : ExecutionTiming.SAME_DAY_CLOSE;
    }

    // 보유 × 마지막 종가(USD, 첫 일봉 전은 0)
    private BigDecimal valueUsd() {
      return lastClose == null ? BigDecimal.ZERO : shares.multiply(lastClose);
    }
  }

  // 평가일 전 하루의 반올림 전 가치(USD)와 앞 날 다음부터 그 날짜까지의 입금(USD)
  private record Day(LocalDate date, BigDecimal rawUsd, BigDecimal depositUsd) {}

  // 한 번 돌리는 동안의 현금(USD, 신호 줄 몫 포함) · 거래 · 배당 · 넣은 돈 · 일별 가치 · 마지막으로 리밸런싱한 주기 · 마지막 납부 줄
  private static final class Run {
    private final PortfolioRequest request;
    private final List<Line> lines;
    private final List<Line> targets;
    private final FxRates fxRates;
    private final LocalDate valuationDate;
    private final CapitalGainsTaxPayer taxPayer;
    private final PortfolioRebalance rebalance;
    private final PortfolioTaxSale taxSale;
    private final Set<LocalDate> harvestDates;
    private final List<StrategyTransaction> transactions = new ArrayList<>();
    private final List<Dividend> dividends = new ArrayList<>();
    private final List<Day> days = new ArrayList<>();
    private BigDecimal cash = BigDecimal.ZERO;
    private BigDecimal invested = BigDecimal.ZERO;
    private BigDecimal pendingDepositUsd = BigDecimal.ZERO;
    private boolean bought;
    private int rebalancedPeriod = Integer.MIN_VALUE;
    private StrategyTransaction lastPayment;

    private Run(PortfolioRequest request, List<Line> lines, FxRates fxRates, LocalDate valuationDate,
      Set<LocalDate> harvestDates) {
      this.request = request;
      this.lines = lines;
      this.targets = lines.stream().filter(line -> line.signal == null).toList();
      this.fxRates = fxRates;
      this.valuationDate = valuationDate;
      this.taxPayer = new CapitalGainsTaxPayer(Boolean.TRUE.equals(request.getCapitalGainsTax()));
      this.rebalance = PortfolioRebalance.orNone(request.getRebalance());
      this.taxSale = PortfolioTaxSale.orNone(request.getTaxSale());
      this.harvestDates = harvestDates;
    }

    // 시가 매도 → 양도세 납부 → 배당 → 시가 매수 → 익절 · 손절 → 신호 매매 → 회차 매수 → 리밸런싱 → 연말 정리 → 기간 끝 매도 → 평가(평가일은 마무리에서)
    private void day(LocalDate date) {
      BigDecimal fx = fxOn(date);
      lines.forEach(line -> {
        OHLCPriceDto candle = line.candles.get(date);
        if (candle != null) {
          line.lastClose = PriceLookup.effectiveClose(candle);
        }
      });
      Map<Line, BigDecimal> soldAtOpen = sellAtOpen(date, fx);
      payDueTax(date, fx);
      receiveDividends(date, Boolean.TRUE.equals(request.getReinvestDividends()), soldAtOpen);
      lines.forEach(line -> fill(line, date, fx, true));
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
      if (harvestDates.contains(date)) {
        harvest(date, fx);
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

    // 그 날짜 일봉과 주식이 있는 줄(기간 끝에 그 날짜 파는 줄은 빼고)을 연말 정리 계획대로 그 날짜 종가에 팔고 다시 삼(현금 · 신호 줄 몫은 그대로)
    private void harvest(LocalDate date, BigDecimal fx) {
      boolean sellsAtEnd = PeriodEnd.sells(request.getPeriodEnd());
      Map<String, Line> harvestable = new HashMap<>();
      lines.stream()
        .filter(line -> line.shares.signum() > 0 && line.candles.containsKey(date))
        .filter(line -> !(sellsAtEnd && date.equals(line.candles.lastKey())))
        .forEach(line -> harvestable.put(line.symbol, line));
      Map<String, BigDecimal> prices = new HashMap<>();
      harvestable.forEach((symbol, line) -> prices.put(symbol, line.lastClose));
      for (YearEndHarvest.Sale sale : YearEndHarvest.plan(date, transactions, prices, request.getTradingFeeRate(), fx)) {
        Line line = harvestable.get(sale.symbol());
        YearEndHarvest.Trade trade = YearEndHarvest.trade(date, sale, line.lastClose, line.shares, line.cost,
          request.getTradingFeeRate(), fx);
        trade.sale().setSymbol(line.symbol);
        trade.sale().setReason(sale.gain() ? TradeReason.HARVEST_GAIN_SELL : TradeReason.HARVEST_LOSS_SELL);
        trade.sale().setDefaultFxDate(saleDefaultFx(date));
        trade.rebuy().setSymbol(line.symbol);
        trade.rebuy().setReason(sale.gain() ? TradeReason.HARVEST_GAIN_BUY : TradeReason.HARVEST_LOSS_BUY);
        trade.rebuy().setDefaultFxDate(defaultFx(date));
        transactions.add(trade.sale());
        transactions.add(trade.rebuy());
        line.shares = line.shares.add(trade.sharesChange());
        line.cost = line.cost.add(trade.costChange());
      }
    }

    // 그 날짜까지 납부일이 온 남은 양도세를 현금에서 냄(모자라면 판매 방식대로 먼저 팖, 그 날짜에 또 내면 그 날짜 납부 줄에 더함)
    private void payDueTax(LocalDate date, BigDecimal fx) {
      sellForTax(date, fx, taxPayer.shortfallUsd(date, transactions, cash, fx));
      StrategyTransaction payment = taxPayer.pay(date, transactions, cash, fx, fxRates.isDefault(date));
      if (payment == null) {
        return;
      }
      if (lastPayment != null && date.equals(lastPayment.getDate())) {
        lastPayment.setTaxPaid(lastPayment.getTaxPaid().add(payment.getTaxPaid()));
      } else {
        payment.setReason(TradeReason.TAX_PAYMENT);
        transactions.add(payment);
        lastPayment = payment;
      }
      payTax(payment.getTaxPaid());
    }

    // 배당락일이 그 날짜까지 온 배당을 전날까지 든 주식(시가에 판 줄은 판 주식)으로 받아 세후 달러를 현금에, 재투자면 그 날짜 그 종목 종가로 삼(시가에 판 줄은 빼고)
    private void receiveDividends(LocalDate date, boolean reinvest, Map<Line, BigDecimal> soldAtOpen) {
      for (Line line : lines) {
        BigDecimal held = soldAtOpen.getOrDefault(line, line.shares);
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
          BigDecimal fx = fxOn(exDate);
          dividends.add(new Dividend(line.symbol, exDate, afterTax, fx));
          cash = cash.add(afterTax);
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

    // 받은 배당 전부로 그 일봉 날짜 종가 · 환율에 삼(수수료 없음)
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
        .fxRate(fxOn(bought))
        .defaultFxDate(defaultFx(bought))
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
        if (line.nextOpen) {
          line.order = new SignalOrder(date, false);
        } else {
          sell(line, line.shares, line.cost, line.lastClose, TradeReason.STRATEGY_SELL, line.signal.sellTrigger(), date,
            fx);
        }
      } else if (!holding && wanted && (line.share == null || line.share.signum() > 0)) {
        if (line.nextOpen) {
          line.order = new SignalOrder(date, true);
        } else {
          buyOnSignal(line, date, date, line.lastClose, fx);
        }
      }
    }

    // 그 날짜 시가에 판 신호 줄과 판 주식 수
    private Map<Line, BigDecimal> sellAtOpen(LocalDate date, BigDecimal fx) {
      Map<Line, BigDecimal> sold = new HashMap<>();
      for (Line line : lines) {
        BigDecimal held = line.shares;
        fill(line, date, fx, false);
        if (held.compareTo(line.shares) != 0) {
          sold.put(line, held);
        }
      }
      return sold;
    }

    // 그 날짜 일봉이 있는 신호 줄의 앞 거래일 매도 · 매수 신호를 그 날짜 시가에 체결(매도는 전량, 매수는 줄 몫 현금이 남았을 때)
    private void fill(Line line, LocalDate date, BigDecimal fx, boolean buy) {
      OHLCPriceDto candle = line.candles.get(date);
      if (line.order == null || line.order.buy() != buy || candle == null) {
        return;
      }
      LocalDate signalDate = line.order.signalDate();
      line.order = null;
      BigDecimal open = PriceLookup.effectiveOpen(candle);
      if (!buy && line.shares.signum() > 0) {
        credit(line, recordSale(line, line.shares, line.cost, open, TradeReason.STRATEGY_SELL, line.signal.sellTrigger(),
          signalDate, date, fx));
      } else if (buy && line.shares.signum() == 0 && (line.share == null || line.share.signum() > 0)) {
        buyOnSignal(line, signalDate, date, open, fx);
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

    // 신호 줄 매수: 첫 매수는 줄 금액을 새 돈으로, 그 뒤는 줄 몫 현금 전부로 그 날짜 그 값에 삼(계획일은 신호 난 날)
    private void buyOnSignal(Line line, LocalDate planned, LocalDate date, BigDecimal price, BigDecimal fx) {
      StrategyTransaction.StrategyTransactionBuilder builder = StrategyTransaction.builder()
        .symbol(line.symbol)
        .reason(TradeReason.STRATEGY_BUY)
        .date(planned)
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

    // 신호 줄 빼고 목표 = 그 줄들 사이 처음 비중 × (그 줄들 평가 합 + 공용 현금), 넘는 줄을 먼저 팔아 남은 양도세를 내고 모자란 줄을 요청 순서로 남은 공용 현금만큼 삼(1달러 미만 매매는 건너뜀)
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
      payDueTax(date, fx);
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
      credit(line, recordSale(line, shares, soldCost, price, reason, trigger, date, date, fx));
    }

    // 판 거래(계획일은 신호 난 날)를 남기고 줄 보유 · 원가를 줄인 뒤 매도 수수료를 뺀 판 돈
    private BigDecimal recordSale(Line line, BigDecimal shares, BigDecimal soldCost, BigDecimal price,
      TradeReason reason, String trigger, LocalDate planned, LocalDate date, BigDecimal fx) {
      BigDecimal gross = shares.multiply(price).setScale(8, RoundingMode.HALF_UP);
      BigDecimal fee = BacktestCalculationUtils.calculateTradingFee(gross, request.getTradingFeeRate());
      BigDecimal proceeds = gross.subtract(fee);
      transactions.add(StrategyTransaction.builder()
        .symbol(line.symbol)
        .reason(reason)
        .date(planned)
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
      line.shares = line.shares.subtract(shares);
      line.cost = line.cost.subtract(soldCost);
      return proceeds;
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

    // 모자란 달러만큼 판매 방식이 정한 줄 · 양을 그 날짜 종가에 팔아 판 돈을 공용 현금에 넣음(그 날짜 일봉과 주식이 있는 줄만)
    private void sellForTax(LocalDate date, BigDecimal fx, BigDecimal shortfallUsd) {
      if (shortfallUsd.signum() <= 0) {
        return;
      }
      List<Line> sellable = lines.stream()
        .filter(line -> line.shares.signum() > 0 && line.candles.containsKey(date))
        .toList();
      Map<Line, BigDecimal> plan = switch (taxSale) {
        case NONE -> Map.of();
        case TARGET_WEIGHT -> overTarget(sellable, shortfallUsd);
        case CUSTOM_ORDER -> inOrder(sellable.stream()
          .sorted(Comparator.comparing((Line line) -> line.taxSaleOrder)).toList(), shortfallUsd);
        case LOSS_FIRST -> inOrder(byGain(sellable, unrealizedKrw(sellable, fx)), shortfallUsd);
        case GAIN_NEUTRAL -> gainNeutral(sellable, shortfallUsd, fx);
      };
      plan.forEach((line, usd) -> sellLineForTax(line, usd, date, fx));
    }

    // 차례대로 줄마다 남은 모자란 달러만큼(줄을 다 팔아도 모자라면 다음 줄)
    private Map<Line, BigDecimal> inOrder(List<Line> ordered, BigDecimal needUsd) {
      Map<Line, BigDecimal> plan = new LinkedHashMap<>();
      BigDecimal left = needUsd;
      for (Line line : ordered) {
        if (left.signum() <= 0) {
          break;
        }
        BigDecimal take = left.min(netUsd(line));
        plan.put(line, take);
        left = left.subtract(take);
      }
      return plan;
    }

    // 목표(줄 금액 비중 × 모든 줄 평가 합)보다 넘친 줄부터 넘친 만큼, 그래도 모자라면 남은 몫을 줄 금액 비중대로 나눔(줄마다 한 번, 요청 순서)
    private Map<Line, BigDecimal> overTarget(List<Line> sellable, BigDecimal needUsd) {
      BigDecimal plannedSum = sum(lines, line -> line.plannedKrw);
      BigDecimal stockUsd = sum(lines, Line::valueUsd);
      Function<Line, BigDecimal> excess = line -> line.valueUsd()
        .subtract(stockUsd.multiply(line.plannedKrw).divide(plannedSum, 8, RoundingMode.HALF_UP));
      List<Line> over = sellable.stream()
        .filter(line -> excess.apply(line).signum() > 0)
        .sorted(Comparator.comparing(excess).reversed())
        .toList();
      Map<Line, BigDecimal> taken = new HashMap<>();
      BigDecimal left = needUsd;
      for (Line line : over) {
        if (left.signum() <= 0) {
          break;
        }
        BigDecimal take = excess.apply(line).multiply(keptAfterFee()).min(left);
        taken.put(line, take);
        left = left.subtract(take);
      }
      spread(sellable, line -> line.plannedKrw, left, taken);
      return inLineOrder(taken);
    }

    // 손실 줄 · 이익 줄 묶음의 달러당 평가 손익 rL < 0 ≤ rG 로 이익 쪽 = 모자란 달러 × (-rL) ÷ (rG - rL), 손실 쪽은 나머지
    // 한쪽 묶음이 모자라면 그쪽 전부 · 나머지는 다른 쪽, 묶음 안은 지금 가치대로, 한쪽 묶음이 없으면 평가 손익이 작은 줄부터
    private Map<Line, BigDecimal> gainNeutral(List<Line> sellable, BigDecimal needUsd, BigDecimal fx) {
      Map<Line, BigDecimal> gains = unrealizedKrw(sellable, fx);
      List<Line> losers = sellable.stream().filter(line -> gains.get(line).signum() < 0).toList();
      List<Line> winners = sellable.stream().filter(line -> gains.get(line).signum() >= 0).toList();
      if (losers.isEmpty() || winners.isEmpty()) {
        return inOrder(byGain(sellable, gains), needUsd);
      }
      BigDecimal lossUsd = sum(losers, this::netUsd);
      BigDecimal gainUsd = sum(winners, this::netUsd);
      BigDecimal lossRate = sum(losers, gains::get).divide(lossUsd, 10, RoundingMode.HALF_UP);
      BigDecimal gainRate = sum(winners, gains::get).divide(gainUsd, 10, RoundingMode.HALF_UP);
      BigDecimal toWinners = needUsd.multiply(lossRate.negate())
        .divide(gainRate.subtract(lossRate), 8, RoundingMode.HALF_UP)
        .max(needUsd.subtract(lossUsd))
        .min(gainUsd);
      Map<Line, BigDecimal> taken = new HashMap<>();
      spread(winners, this::netUsd, toWinners, taken);
      spread(losers, this::netUsd, needUsd.subtract(toWinners), taken);
      return inLineOrder(taken);
    }

    // 남은 달러를 줄 무게대로 나눠 더함, 몫이 남은 판 돈 이상인 줄은 남은 판 돈만큼 채우고 나머지를 다른 줄에 다시 나눔
    private void spread(List<Line> candidates, Function<Line, BigDecimal> weight, BigDecimal needUsd,
      Map<Line, BigDecimal> taken) {
      List<Line> open = new ArrayList<>(candidates);
      BigDecimal left = needUsd;
      while (left.signum() > 0 && !open.isEmpty()) {
        BigDecimal total = sum(open, weight);
        BigDecimal round = left;
        Function<Line, BigDecimal> part = line -> round.multiply(weight.apply(line))
          .divide(total, 8, RoundingMode.CEILING);
        List<Line> capped = open.stream().filter(line -> part.apply(line).compareTo(room(line, taken)) >= 0).toList();
        if (capped.isEmpty()) {
          open.forEach(line -> taken.merge(line, part.apply(line), BigDecimal::add));
          return;
        }
        for (Line line : capped) {
          left = left.subtract(room(line, taken));
          taken.merge(line, room(line, taken), BigDecimal::add);
        }
        open.removeAll(capped);
      }
    }

    // 줄의 판 돈(USD, 수수료 뺌)에서 이미 정한 몫을 뺀 값
    private BigDecimal room(Line line, Map<Line, BigDecimal> taken) {
      return netUsd(line).subtract(taken.getOrDefault(line, BigDecimal.ZERO));
    }

    // 정한 몫을 요청 줄 순서로
    private Map<Line, BigDecimal> inLineOrder(Map<Line, BigDecimal> taken) {
      Map<Line, BigDecimal> plan = new LinkedHashMap<>();
      lines.stream()
        .filter(line -> taken.getOrDefault(line, BigDecimal.ZERO).signum() > 0)
        .forEach(line -> plan.put(line, taken.get(line)));
      return plan;
    }

    // 평가 손익(원화)이 작은 줄부터
    private static List<Line> byGain(List<Line> sellable, Map<Line, BigDecimal> gains) {
      return sellable.stream().sorted(Comparator.comparing(gains::get)).toList();
    }

    // 줄마다 평가 손익(원화, 수수료를 뺀 판 돈 × 그 날짜 환율 - 남은 주식의 원화 원가)
    private Map<Line, BigDecimal> unrealizedKrw(List<Line> sellable, BigDecimal fx) {
      Map<String, CapitalGainsTaxCalculator.HoldingKrw> holdings = CapitalGainsTaxCalculator.holdingsKrw(transactions);
      Map<Line, BigDecimal> gains = new HashMap<>();
      sellable.forEach(line -> gains.put(line,
        netUsd(line).multiply(fx).subtract(holdings.get(line.symbol).costKrw())));
      return gains;
    }

    // 보유 × 마지막 종가에서 매도 수수료를 뺀 달러
    private BigDecimal netUsd(Line line) {
      return line.valueUsd().multiply(keptAfterFee());
    }

    // 1 - 수수료율(수수료율이 없으면 1)
    private BigDecimal keptAfterFee() {
      BigDecimal feeRate = request.getTradingFeeRate();
      return feeRate == null ? BigDecimal.ONE : BigDecimal.ONE.subtract(feeRate);
    }

    private static BigDecimal sum(List<Line> group, Function<Line, BigDecimal> value) {
      return group.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // 판 돈(수수료 뺌)이 정한 달러 이상이 되게 그 날짜 종가에 팔고(보유 상한) 판 돈은 공용 현금
    private void sellLineForTax(Line line, BigDecimal usd, LocalDate date, BigDecimal fx) {
      BigDecimal shares = TaxSale.shares(usd, line.lastClose, request.getTradingFeeRate(), line.shares);
      BigDecimal soldCost = line.cost.multiply(shares).divide(line.shares, 8, RoundingMode.HALF_UP);
      cash = cash.add(recordSale(line, shares, soldCost, line.lastClose, TradeReason.TAX_SALE,
        TransactionTrigger.TAX_SALE, date, date, fx));
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
        .map(line -> new Position(line.symbol, line.type, line.executionTiming(), line.plannedKrw, line.shares,
          line.lastClose, MoneyUtils.roundKrw(line.valueUsd().multiply(rate))))
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
