package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.calculation.TradeStatsCalculator;
import com.muscat.backtest.common.engine.CapitalGainsTaxPayer;
import com.muscat.backtest.common.engine.StrategyFinalizer;
import com.muscat.backtest.common.engine.YearEndHarvest;
import com.muscat.backtest.common.enums.response.BacktestResponse;
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

// 신호 전략 공통 매매: 시작일부터 매매 종목 일봉마다 그 날짜까지의 신호로 들고 있을지 정하고, 양도세를 낸 뒤 그 날짜 종가에
// 가진 돈 전부로 사거나 전량 팜(트레일링 스탑 · 부분 익절은 정한 몫, 처음 매수는 원금, 그 뒤는 판 돈, 신호 종목은 시작일 전 일봉을 더 받아 시작일부터 신호를 봄)
@Slf4j
abstract class SignalStrategy<R extends SignalStrategyRequest> implements InvestmentStrategy {

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

    List<StrategyTransaction> transactions = new ArrayList<>();
    List<SkippedRound> skippedRounds = new ArrayList<>();
    // 보유 주식 · 그 원가(USD, 수수료 포함) · 판 돈(USD, 첫 매수 전은 null)
    BigDecimal heldShares = BigDecimal.ZERO;
    BigDecimal heldCost = BigDecimal.ZERO;
    BigDecimal cash = null;
    SignalHolding target = new SignalHolding(signal, signalPrices, start);
    TrailingStop trailing = TrailingStop.of(request.getTrailingStopRate(), request.getTrailingSellRate());
    TakeProfitLadder ladder = TakeProfitLadder.of(request.getPartialTakeProfits());
    Set<LocalDate> harvestDates = YearEndHarvest.enabled(request.getCapitalGainsTax(), request.getYearEndHarvest())
      ? YearEndHarvest.dates(priceMap, start, actualEndDate)
      : Set.of();

    for (OHLCPriceDto candle : candles) {
      LocalDate date = candle.date();
      if (date.isBefore(start)) {
        continue;
      }
      boolean wanted = target.wanted(date);
      try {
        BigDecimal fx = fxOn(request, fxRates, date);
        // 양도세 낼 현금이 모자라면 그 날짜 종가에 모자란 만큼 팖, 다 팔면 새 매수 신호까지 사지 않음
        SaleShare taxExit = TaxSale.sells(request.getTaxSale())
          ? SaleShare.forTax(taxPayer.shortfallUsd(date, transactions, cash, fx), PriceLookup.effectiveClose(candle),
            request.getTradingFeeRate(), heldShares, heldCost)
          : null;
        if (taxExit != null) {
          StrategyTransaction sale = sell(request, candle, fxRates, taxExit.shares(), taxExit.cost(), taxExit.trigger());
          transactions.add(sale);
          cash = cash.add(sale.getProceeds());
          heldShares = heldShares.subtract(taxExit.shares());
          heldCost = heldCost.subtract(taxExit.cost());
          if (taxExit.all()) {
            target.soldOut();
            wanted = false;
          }
        }
        StrategyTransaction payment = taxPayer.pay(date, transactions, cash, fx, fxRates.isDefault(date));
        if (payment != null) {
          transactions.add(payment);
          cash = cash.subtract(payment.getTaxPaid());
        }
        boolean holding = heldShares.signum() > 0;
        // 판 돈을 양도세로 다 냈으면 사지 않음
        if (!holding && wanted && (cash == null || cash.signum() > 0)) {
          StrategyTransaction buy = buy(request, candle, fxRates, cash, signal.buyTrigger());
          transactions.add(buy);
          heldShares = buy.getShares();
          heldCost = buy.costUsd();
          cash = BigDecimal.ZERO;
          trailing.open(buy.getPrice(), buy.getPrice());
          ladder.open();
        } else if (holding && !wanted) {
          StrategyTransaction sale = sell(request, candle, fxRates, heldShares, heldCost, signal.sellTrigger());
          transactions.add(sale);
          cash = cash.add(sale.getProceeds());
          heldShares = BigDecimal.ZERO;
          heldCost = BigDecimal.ZERO;
        } else if (holding) {
          BigDecimal close = PriceLookup.effectiveClose(candle);
          SaleShare trailed = trailing.check(close, heldShares, heldCost);
          SaleShare exit = trailed != null ? trailed : ladder.check(close, heldShares, heldCost);
          if (exit != null) {
            StrategyTransaction sale = sell(request, candle, fxRates, exit.shares(), exit.cost(), exit.trigger());
            transactions.add(sale);
            if (exit == trailed) {
              trailing.sold(sale.getPrice());
            } else {
              ladder.sold();
            }
            cash = cash.add(sale.getProceeds());
            heldShares = heldShares.subtract(exit.shares());
            heldCost = heldCost.subtract(exit.cost());
            if (exit.all()) {
              target.soldOut();
            }
          }
        }
        // 그 날짜 사고팔기 뒤 연말 정리
        YearEndHarvest.Trade harvest = harvestDates.contains(date) && heldShares.signum() > 0
          ? YearEndHarvest.single(date, transactions, PriceLookup.effectiveClose(candle), heldShares, heldCost,
            request.getTradingFeeRate(), fx, fxRates.isDefault(date))
          : null;
        if (harvest != null) {
          transactions.add(harvest.sale());
          transactions.add(harvest.rebuy());
          heldShares = heldShares.add(harvest.sharesChange());
          heldCost = heldCost.add(harvest.costChange());
        }
      } catch (RuntimeException e) {
        skippedRounds.add(new SkippedRound(candle.date(), SkipReason.CALCULATION_ERROR));
        log.warn("{} {} 매매 계산 실패, 사고팔지 않음: {}", label(), candle.date(), e.getMessage());
      }
    }

    log.info("{} 전략 완료: 거래 {}건, 계산 실패 {}회", label(), transactions.size(), skippedRounds.size());
    return new StrategyPurchases(transactions, skippedRounds, priceMap, fxRates);
  }

  // 그 날짜 종가에 원금(첫 매수) 또는 판 돈 전부로 삼(수수료를 뺀 달러로 소수 주)
  private static StrategyTransaction buy(SignalStrategyRequest request, OHLCPriceDto candle, FxRates fxRates,
    BigDecimal cash, String trigger) {
    LocalDate date = candle.date();
    BigDecimal price = PriceLookup.effectiveClose(candle);
    BigDecimal fxRate = fxOn(request, fxRates, date);
    StrategyTransaction.StrategyTransactionBuilder builder = StrategyTransaction.builder()
      .date(date)
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

  // 그 날짜 종가에 정한 주식을 팔고 매도 수수료를 뺀 판 돈 · 판 몫의 원가 대비 실현 손익을 남김
  private static StrategyTransaction sell(SignalStrategyRequest request, OHLCPriceDto candle, FxRates fxRates,
    BigDecimal shares, BigDecimal cost, String trigger) {
    LocalDate date = candle.date();
    BigDecimal price = PriceLookup.effectiveClose(candle);
    BigDecimal gross = shares.multiply(price).setScale(8, RoundingMode.HALF_UP);
    BigDecimal fee = BacktestCalculationUtils.calculateTradingFee(gross, request.getTradingFeeRate());
    BigDecimal proceeds = gross.subtract(fee);
    return StrategyTransaction.builder()
      .date(date)
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
