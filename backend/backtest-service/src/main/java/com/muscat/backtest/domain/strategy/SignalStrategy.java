package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.calculation.TradeStatsCalculator;
import com.muscat.backtest.common.engine.CapitalGainsTaxPayer;
import com.muscat.backtest.common.engine.StrategyFinalizer;
import com.muscat.backtest.common.engine.YearEndHarvest;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.ExecutionTiming;
import com.muscat.backtest.common.enums.type.SkipReason;
import com.muscat.backtest.common.enums.type.TaxSale;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.common.util.RoundPurchase;
import com.muscat.backtest.common.validation.BacktestRequestValidator;
import com.muscat.backtest.domain.dto.request.SignalStrategyRequest;
import com.muscat.backtest.domain.dto.request.StrategyRequest;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.SkippedRound;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

// 신호 전략 공통 매매: 시작일부터 매매 종목 일봉마다 그 날짜까지의 신호로 들고 있을지 정하고, 양도세를 낸 뒤 그 날짜 종가에(고르면 다음 거래일 시가 매도 →
// 양도세 납부 → 시가 매수 차례로) 가진 돈 전부로 사거나 전량 팜(트레일링 스탑 · 부분 익절은 정한 몫, 처음 매수는 원금, 그 뒤는 판 돈, 신호 종목은 시작일 전 일봉을 더 받아 시작일부터 신호를 봄)
@Slf4j
abstract class SignalStrategy<R extends SignalStrategyRequest> implements InvestmentStrategy {

  // 거래 내역과 보유 주식 · 그 원가(USD, 수수료 포함) · 판 돈(USD, 첫 매수 전은 null)
  private static final class Book {
    private final List<StrategyTransaction> transactions = new ArrayList<>();
    private BigDecimal shares = BigDecimal.ZERO;
    private BigDecimal cost = BigDecimal.ZERO;
    private BigDecimal cash;

    private boolean holding() {
      return shares.signum() > 0;
    }

    // 첫 매수 전이거나 판 돈이 남았는지(판 돈을 양도세로 다 냈으면 사지 않음)
    private boolean buyable() {
      return cash == null || cash.signum() > 0;
    }

    // 가진 돈 전부로 산 거래와 사이클 첫 매수의 트레일링 스탑 기준가 · 부분 익절 첫 단계
    private void bought(StrategyTransaction buy, TrailingStop trailing, TakeProfitLadder ladder) {
      transactions.add(buy);
      shares = buy.getShares();
      cost = buy.costUsd();
      cash = BigDecimal.ZERO;
      trailing.open(buy.getPrice(), buy.getPrice());
      ladder.open();
    }

    // 정한 주식 · 원가를 판 거래와 판 돈
    private void sold(StrategyTransaction sale, BigDecimal soldShares, BigDecimal soldCost) {
      transactions.add(sale);
      cash = cash.add(sale.getProceeds());
      shares = shares.subtract(soldShares);
      cost = cost.subtract(soldCost);
    }

    // 현금에서 낸 양도세
    private void paid(StrategyTransaction payment) {
      transactions.add(payment);
      cash = cash.subtract(payment.getTaxPaid());
    }

    // 연말 정리로 판 거래 · 다시 산 거래
    private void harvested(YearEndHarvest.Trade trade) {
      transactions.add(trade.sale());
      transactions.add(trade.rebuy());
      shares = shares.add(trade.sharesChange());
      cost = cost.add(trade.costChange());
    }
  }

  private final MarketDataClientWrapper marketData;
  private final ResponseMapper responseMapper;
  private final Class<R> requestType;

  SignalStrategy(MarketDataClientWrapper marketData, ResponseMapper responseMapper, Class<R> requestType) {
    this.marketData = marketData;
    this.responseMapper = responseMapper;
    this.requestType = requestType;
  }

  // 로그 · 오류 문구의 전략 이름
  abstract String label();

  // 신호 칸 검사
  abstract void validateSignal(R request);

  // 신호에 쓰는 가장 긴 기간(거래일)
  abstract int window(R request);

  abstract TradeSignal signal(R request, SignalPrices prices);

  @Override
  public StrategyResponse execute(StrategyRequest request) {
    // null 은 검증이 막음
    if (request != null && !requestType.isInstance(request)) {
      throw new BacktestException(BacktestResponse.STRATEGY_TYPE_MISMATCH,
        label() + " 전략에 다른 요청이 왔습니다: " + request.getStrategyType());
    }
    return run(requestType.cast(request));
  }

  private StrategyResponse run(R request) {
    validate(request);

    BacktestLogger.setStrategyContext(request.getUserId(), getStrategyType().name(), request.getSymbol());

    try {
      String signalSymbol = request.signalSymbolOrDefault();
      log.info("{} 전략 실행 시작: {} - 신호 {} · 신호 기간 {}일, 원금 {}원",
        label(), request.getSymbol(), signalSymbol, window(request), request.getInvestmentAmount());

      LocalDate today = LocalDate.now();
      LocalDate actualEndDate = request.getEndDate().isAfter(today) ? today : request.getEndDate();

      CapitalGainsTaxPayer taxPayer = new CapitalGainsTaxPayer(Boolean.TRUE.equals(request.getCapitalGainsTax()));
      boolean separate = !signalSymbol.equals(request.getSymbol());
      LocalDate signalFrom = request.getStartDate().minusDays(SignalHolding.lookbackDays(window(request)));
      Map<LocalDate, OHLCPriceDto> priceMap = BacktestDataUtils.buildPriceMap(
        marketData, request.getSymbol(), separate ? request.getStartDate() : signalFrom, actualEndDate);
      SignalPrices prices = separate
        ? signalPrices(signalSymbol, signalFrom, request.getStartDate(), actualEndDate)
        : SignalPrices.of(priceMap);
      TradeSignal signal = signal(request, prices);
      StrategyPurchases trades = trade(request, priceMap, prices, signal, actualEndDate, taxPayer);

      StrategyFinalizer.Result finalized = StrategyFinalizer.run(
        marketData, request.getSymbol(), request.getStartDate(), actualEndDate, request.getCurrentFxRate(),
        request.getPurchaseFxRate(), false, request.getDividendTaxRate(), trades.transactions(), trades.priceMap(),
        trades.dailyFx(), request.getPeriodEnd(), request.getTradingFeeRate(), false);

      log.info("{} 전략 실행 완료: {} - 거래 {}건, 수익률 {}%",
        label(), request.getSymbol(), finalized.transactions().size(), finalized.calculation().getTotalReturnPercent());

      StrategyResponse response = finalized.fill(responseMapper.toStrategyResponse(
        request, finalized.transactions(), finalized.calculation(), finalized.currentPrice(), trades.skippedRounds()));
      response.setCapitalGainsTax(Boolean.TRUE.equals(request.getCapitalGainsTax())
        ? finalized.capitalGainsTax(taxPayer.payments())
        : null);
      response.setSignalChart(signal.chart(signalSymbol, request.getStartDate()));
      response.setExecutionTiming(ExecutionTiming.orSameDayClose(request.getExecutionTiming()));
      response.setTradeStats(TradeStatsCalculator.of(finalized.transactions(), request.getStartDate(), actualEndDate));
      return response;

    } finally {
      BacktestLogger.remove("operation");
    }
  }

  // 종목 · 기간 · 원금 · 신호 · 트레일링 스탑 · 부분 익절 칸 검사, 배당 재투자는 막음
  private void validate(R request) {
    BacktestRequestValidator.requireNonNull(request);
    BacktestRequestValidator.requireSymbol(request.getSymbol());
    BacktestRequestValidator.requireDateRange(request.getStartDate(), request.getEndDate());
    if (request.getInvestmentAmount() == null || !Decimals.isPositive(request.getInvestmentAmount())) {
      throw new BacktestException(BacktestResponse.INVALID_INVESTMENT_AMOUNT);
    }
    validateSignal(request);
    BacktestRequestValidator.requireTrailingStop(request.getTrailingStopRate(), request.getTrailingSellRate());
    BacktestRequestValidator.requirePartialTakeProfits(request.getPartialTakeProfits());
    // 재투자 주식은 신호 매매 뒤에 더해져 매도와 순서가 맞지 않음
    if (Boolean.TRUE.equals(request.getReinvestDividends())) {
      throw new BacktestException(BacktestResponse.SELL_WITH_REINVEST);
    }
  }

  // 신호 종목 일봉(기간 안 일봉이 없으면 DATA_NOT_FOUND)
  private SignalPrices signalPrices(String symbol, LocalDate from, LocalDate start, LocalDate end) {
    SignalPrices prices = SignalPrices.of(BacktestDataUtils.buildPriceMap(marketData, symbol, from, end));
    if (prices.candles().isEmpty() || prices.candles().getLast().date().isBefore(start)) {
      throw new BacktestException(BacktestResponse.DATA_NOT_FOUND, "신호 종목 시세가 없습니다");
    }
    return prices;
  }

  // 시작일부터 매매 종목 일봉마다 그 날짜까지의 신호로 보유 여부를 정하고 양도세를 낸 뒤 사고팖(계산이 실패한 날은 이유를 남기고 다음 거래일에 다시 봄)
  // 트레일링 스탑 다음 부분 익절을 신호로 팔지 않는 날만 보고, 다 팔면 새 매수 신호까지 사지 않음, 연말 정리 날은 사고판 뒤 연말 정리
  // 다음 거래일 시가 체결은 그 날짜 첫 매매(매도는 양도세 납부 전, 매수는 납부 뒤 남은 돈으로), 마지막 거래일 신호는 체결 안 함
  private StrategyPurchases trade(R request, Map<LocalDate, OHLCPriceDto> priceMap, SignalPrices signalPrices,
    TradeSignal signal, LocalDate actualEndDate, CapitalGainsTaxPayer taxPayer) {
    LocalDate start = request.getStartDate();
    List<OHLCPriceDto> candles = SignalPrices.usable(priceMap);
    LocalDate firstInPeriod = candles.stream()
      .map(OHLCPriceDto::date)
      .filter(date -> !date.isBefore(start))
      .findFirst()
      .orElseThrow(() -> new BacktestException(BacktestResponse.DATA_NOT_FOUND,
        label() + " 전략 실행 가능한 데이터가 없습니다"));

    FxRates fxRates = FxRates.EMPTY;
    if (request.getPurchaseFxRate() == null) {
      fxRates = BacktestDataUtils.buildDailyFxRateMap(marketData, firstInPeriod, actualEndDate);
      log.info("환율 데이터 조회 완료: {}개", fxRates.size());
    }

    List<SkippedRound> skippedRounds = new ArrayList<>();
    Book book = new Book();
    SignalHolding target = new SignalHolding(signal, signalPrices, start);
    TrailingStop trailing = TrailingStop.of(request.getTrailingStopRate(), request.getTrailingSellRate());
    TakeProfitLadder ladder = TakeProfitLadder.of(request.getPartialTakeProfits());
    Set<LocalDate> harvestDates = YearEndHarvest.enabled(request.getCapitalGainsTax(), request.getYearEndHarvest())
      ? YearEndHarvest.dates(priceMap, start, actualEndDate)
      : Set.of();
    boolean nextOpen = ExecutionTiming.nextDayOpen(request.getExecutionTiming());
    SignalOrder order = null;

    for (OHLCPriceDto candle : candles) {
      LocalDate date = candle.date();
      if (date.isBefore(start)) {
        continue;
      }
      boolean wanted = target.wanted(date);
      SignalOrder due = order;
      order = null;
      try {
        BigDecimal fx = fxOn(request, fxRates, date);
        BigDecimal close = PriceLookup.effectiveClose(candle);
        if (due != null && !due.buy() && book.holding()) {
          book.sold(sell(request, candle, PriceLookup.effectiveOpen(candle), due.signalDate(), fxRates, book.shares,
            book.cost, signal.sellTrigger()), book.shares, book.cost);
        }
        // 양도세 낼 현금이 모자라면 그 날짜 종가에 모자란 만큼 팖, 다 팔면 새 매수 신호까지 사지 않음
        SaleShare taxExit = TaxSale.sells(request.getTaxSale())
          ? SaleShare.forTax(taxPayer.shortfallUsd(date, book.transactions, book.cash, fx), close,
            request.getTradingFeeRate(), book.shares, book.cost)
          : null;
        if (taxExit != null) {
          book.sold(sell(request, candle, close, date, fxRates, taxExit.shares(), taxExit.cost(), taxExit.trigger()),
            taxExit.shares(), taxExit.cost());
          if (taxExit.all()) {
            target.soldOut();
            wanted = false;
          }
        }
        StrategyTransaction payment = taxPayer.pay(date, book.transactions, book.cash, fx, fxRates.isDefault(date));
        if (payment != null) {
          book.paid(payment);
        }
        if (due != null && due.buy() && !book.holding() && book.buyable()) {
          book.bought(buy(request, candle, PriceLookup.effectiveOpen(candle), due.signalDate(), fxRates, book.cash,
            signal.buyTrigger()), trailing, ladder);
        }
        boolean holding = book.holding();
        if (!holding && wanted && book.buyable()) {
          if (nextOpen) {
            order = new SignalOrder(date, true);
          } else {
            book.bought(buy(request, candle, close, date, fxRates, book.cash, signal.buyTrigger()), trailing, ladder);
          }
        } else if (holding && !wanted) {
          if (nextOpen) {
            order = new SignalOrder(date, false);
          } else {
            book.sold(sell(request, candle, close, date, fxRates, book.shares, book.cost, signal.sellTrigger()),
              book.shares, book.cost);
          }
        } else if (holding) {
          SaleShare trailed = trailing.check(close, book.shares, book.cost);
          SaleShare exit = trailed != null ? trailed : ladder.check(close, book.shares, book.cost);
          if (exit != null) {
            StrategyTransaction sale = sell(request, candle, close, date, fxRates, exit.shares(), exit.cost(),
              exit.trigger());
            book.sold(sale, exit.shares(), exit.cost());
            if (exit == trailed) {
              trailing.sold(sale.getPrice());
            } else {
              ladder.sold();
            }
            if (exit.all()) {
              target.soldOut();
            }
          }
        }
        // 그 날짜 사고팔기 뒤 연말 정리
        YearEndHarvest.Trade harvest = harvestDates.contains(date) && book.holding()
          ? YearEndHarvest.single(date, book.transactions, close, book.shares, book.cost,
            request.getTradingFeeRate(), fx, fxRates.isDefault(date))
          : null;
        if (harvest != null) {
          book.harvested(harvest);
        }
      } catch (RuntimeException e) {
        skippedRounds.add(new SkippedRound(candle.date(), SkipReason.CALCULATION_ERROR));
        log.warn("{} {} 매매 계산 실패, 사고팔지 않음: {}", label(), candle.date(), e.getMessage());
      }
    }

    log.info("{} 전략 완료: 거래 {}건, 계산 실패 {}회", label(), book.transactions.size(), skippedRounds.size());
    return new StrategyPurchases(book.transactions, skippedRounds, priceMap, fxRates);
  }

  // 그 날짜 그 값에 원금(첫 매수) 또는 판 돈 전부로 삼(수수료를 뺀 달러로 소수 주, 계획일은 신호 난 날)
  private static StrategyTransaction buy(SignalStrategyRequest request, OHLCPriceDto candle, BigDecimal price,
    LocalDate planned, FxRates fxRates, BigDecimal cash, String trigger) {
    LocalDate date = candle.date();
    BigDecimal fxRate = fxOn(request, fxRates, date);
    StrategyTransaction.StrategyTransactionBuilder builder = StrategyTransaction.builder()
      .date(planned)
      .actualDate(date)
      .price(price)
      .fxRate(fxRate)
      .defaultFxDate(fxRates.isDefault(date) ? date : null)
      .trigger(trigger);
    if (cash == null) {
      RoundPurchase purchase = RoundPurchase.byAmount(request.getInvestmentAmount(), price, fxRate,
        request.getTradingFeeRate());
      return builder.shares(purchase.shares()).fee(purchase.feeUsd()).amount(purchase.amountKrw()).build();
    }
    BigDecimal fee = BacktestCalculationUtils.calculateTradingFee(cash, request.getTradingFeeRate());
    return builder.shares(cash.subtract(fee).divide(price, 8, RoundingMode.HALF_UP))
      .fee(fee)
      .amount(BigDecimal.ZERO)
      .cashUsed(cash)
      .build();
  }

  // 그 날짜 그 값에 정한 주식을 팔고 매도 수수료를 뺀 판 돈 · 판 몫의 원가 대비 실현 손익을 남김(계획일은 신호 난 날)
  private static StrategyTransaction sell(SignalStrategyRequest request, OHLCPriceDto candle, BigDecimal price,
    LocalDate planned, FxRates fxRates, BigDecimal shares, BigDecimal cost, String trigger) {
    LocalDate date = candle.date();
    BigDecimal gross = shares.multiply(price).setScale(8, RoundingMode.HALF_UP);
    BigDecimal fee = BacktestCalculationUtils.calculateTradingFee(gross, request.getTradingFeeRate());
    BigDecimal proceeds = gross.subtract(fee);
    return StrategyTransaction.builder()
      .date(planned)
      .actualDate(date)
      .price(price)
      .shares(shares.negate())
      .fee(fee)
      .amount(BigDecimal.ZERO)
      .proceeds(proceeds)
      .realizedProfit(proceeds.subtract(cost))
      .fxRate(fxOn(request, fxRates, date))
      .defaultFxDate(Boolean.TRUE.equals(request.getCapitalGainsTax()) && fxRates.isDefault(date) ? date : null)
      .trigger(trigger)
      .build();
  }

  // 그 날짜 환율(수동 환율이 있으면 그 값)
  private static BigDecimal fxOn(SignalStrategyRequest request, FxRates fxRates, LocalDate date) {
    return request.getPurchaseFxRate() != null ? request.getPurchaseFxRate() : fxRates.get(date);
  }
}
