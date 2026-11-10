package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.constants.BacktestConstants.MovingAverage;
import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.common.engine.CapitalGainsTaxPayer;
import com.muscat.backtest.common.engine.StrategyFinalizer;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.SkipReason;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.common.util.RoundPurchase;
import com.muscat.backtest.common.validation.BacktestRequestValidator;
import com.muscat.backtest.domain.dto.request.MaCrossStrategyRequest;
import com.muscat.backtest.domain.dto.request.StrategyRequest;
import com.muscat.backtest.domain.dto.response.SignalChart;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.SkippedRound;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 이동평균 교차 전략: 수정 종가 단순 이동평균의 짧은 선이 긴 선을 아래에서 위로 넘는 날(골든 크로스) 그 날짜 종가에 가진 돈 전부로 사고,
// 위에서 아래로 넘는 날(데드 크로스) 전량 팜(처음은 원금, 그 뒤는 판 돈, 시작일 전 일봉을 더 받아 시작일부터 교차를 봄)
@Component
@RequiredArgsConstructor
@Slf4j
public class MaCrossStrategy implements InvestmentStrategy {

  private final MarketDataClientWrapper marketData;
  private final ResponseMapper responseMapper;

  @Override
  public StrategyType getStrategyType() {
    return StrategyType.MA_CROSS;
  }

  @Override
  public StrategyResponse execute(StrategyRequest request) {
    // null 은 검증이 막음
    if (request != null && !(request instanceof MaCrossStrategyRequest)) {
      throw new BacktestException(BacktestResponse.STRATEGY_TYPE_MISMATCH,
        "이동평균 교차 전략에 다른 요청이 왔습니다: " + request.getStrategyType());
    }
    return runMaCross((MaCrossStrategyRequest) request);
  }

  private StrategyResponse runMaCross(MaCrossStrategyRequest request) {
    validate(request);

    BacktestLogger.setStrategyContext(request.getUserId(), "MA_CROSS", request.getSymbol());

    try {
      int shortWindow = request.shortWindowOrDefault();
      int longWindow = request.longWindowOrDefault();
      log.info("이동평균 교차 전략 실행 시작: {} - {}일 · {}일 선, 원금 {}원",
        request.getSymbol(), shortWindow, longWindow, request.getInvestmentAmount());

      LocalDate today = LocalDate.now();
      LocalDate actualEndDate = request.getEndDate().isAfter(today) ? today : request.getEndDate();

      CapitalGainsTaxPayer taxPayer = new CapitalGainsTaxPayer(Boolean.TRUE.equals(request.getCapitalGainsTax()));
      Map<LocalDate, OHLCPriceDto> priceMap = BacktestDataUtils.buildPriceMap(
        marketData, request.getSymbol(), request.getStartDate().minusDays(lookbackDays(longWindow)), actualEndDate);
      MovingAverages averages = MovingAverages.of(priceMap, shortWindow, longWindow);
      StrategyPurchases trades = trade(request, priceMap, averages, actualEndDate, taxPayer);

      StrategyFinalizer.Result finalized = StrategyFinalizer.run(
        marketData, request.getSymbol(), request.getStartDate(), actualEndDate, request.getCurrentFxRate(),
        request.getPurchaseFxRate(), false, request.getDividendTaxRate(), trades.transactions(), trades.priceMap(),
        trades.dailyFx(), request.getPeriodEnd(), request.getTradingFeeRate());

      log.info("이동평균 교차 전략 실행 완료: {} - 거래 {}건, 수익률 {}%",
        request.getSymbol(), finalized.transactions().size(), finalized.calculation().getTotalReturnPercent());

      StrategyResponse response = finalized.fill(responseMapper.toStrategyResponse(
        request, finalized.transactions(), finalized.calculation(), finalized.currentPrice(), trades.skippedRounds()));
      response.setCapitalGainsTax(Boolean.TRUE.equals(request.getCapitalGainsTax())
        ? finalized.capitalGainsTax(taxPayer.payments())
        : null);
      response.setSignalChart(averages.chart(request.getSymbol(), request.getStartDate()));
      return response;

    } finally {
      BacktestLogger.remove("operation");
    }
  }

  // 종목 · 기간 · 원금 · 이동평균 기간 검사, 배당 재투자는 막음
  private static void validate(MaCrossStrategyRequest request) {
    BacktestRequestValidator.requireNonNull(request);
    BacktestRequestValidator.requireSymbol(request.getSymbol());
    BacktestRequestValidator.requireDateRange(request.getStartDate(), request.getEndDate());
    if (request.getInvestmentAmount() == null || !Decimals.isPositive(request.getInvestmentAmount())) {
      throw new BacktestException(BacktestResponse.INVALID_INVESTMENT_AMOUNT);
    }
    int shortWindow = request.shortWindowOrDefault();
    int longWindow = request.longWindowOrDefault();
    if (shortWindow < MovingAverage.MIN_WINDOW || longWindow > MovingAverage.MAX_WINDOW || shortWindow >= longWindow) {
      throw new BacktestException(BacktestResponse.INVALID_MA_WINDOWS);
    }
    // 재투자 주식은 교차 매매 뒤에 더해져 매도와 순서가 맞지 않음
    if (Boolean.TRUE.equals(request.getReinvestDividends())) {
      throw new BacktestException(BacktestResponse.SELL_WITH_REINVEST);
    }
  }

  // 시작일부터 일봉마다 양도세를 낸 뒤 교차를 보고 골든 크로스에 사고 데드 크로스에 팜(계산이 실패한 날은 사고팔지 않고 이유를 남김)
  private StrategyPurchases trade(MaCrossStrategyRequest request, Map<LocalDate, OHLCPriceDto> priceMap,
    MovingAverages averages, LocalDate actualEndDate, CapitalGainsTaxPayer taxPayer) {
    LocalDate start = request.getStartDate();
    List<OHLCPriceDto> candles = averages.candles();
    LocalDate firstInPeriod = candles.stream()
      .map(OHLCPriceDto::date)
      .filter(date -> !date.isBefore(start))
      .findFirst()
      .orElseThrow(() -> new BacktestException(BacktestResponse.DATA_NOT_FOUND,
        "이동평균 교차 전략 실행 가능한 데이터가 없습니다"));

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

    for (int i = averages.longWindow(); i < candles.size(); i++) {
      OHLCPriceDto candle = candles.get(i);
      if (candle.date().isBefore(start)) {
        continue;
      }
      SignalChart.CrossType cross = averages.cross(i);
      boolean holding = heldShares.signum() > 0;
      try {
        LocalDate date = candle.date();
        StrategyTransaction payment = taxPayer.pay(date, transactions, cash, fxOn(request, fxRates, date),
          fxRates.isDefault(date));
        if (payment != null) {
          transactions.add(payment);
          cash = cash.subtract(payment.getTaxPaid());
        }
        // 판 돈을 양도세로 다 냈으면 사지 않음
        if (!holding && cross == SignalChart.CrossType.GOLDEN && (cash == null || cash.signum() > 0)) {
          StrategyTransaction buy = buy(request, candle, fxRates, cash);
          transactions.add(buy);
          heldShares = buy.getShares();
          heldCost = buy.getCashUsed() != null
            ? buy.getCashUsed()
            : buy.getAmount().divide(buy.getFxRate(), 8, RoundingMode.HALF_UP);
          cash = BigDecimal.ZERO;
        } else if (holding && cross == SignalChart.CrossType.DEAD) {
          StrategyTransaction sale = sell(request, candle, fxRates, heldShares, heldCost);
          transactions.add(sale);
          cash = sale.getProceeds();
          heldShares = BigDecimal.ZERO;
          heldCost = BigDecimal.ZERO;
        }
      } catch (RuntimeException e) {
        skippedRounds.add(new SkippedRound(candle.date(), SkipReason.CALCULATION_ERROR));
        log.warn("이동평균 교차 {} 매매 계산 실패, 사고팔지 않음: {}", candle.date(), e.getMessage());
      }
    }

    log.info("이동평균 교차 전략 완료: 거래 {}건, 계산 실패 {}회", transactions.size(), skippedRounds.size());
    return new StrategyPurchases(transactions, skippedRounds, priceMap, fxRates);
  }

  // 긴 기간 거래일이 넉넉히 들어가는 달력 날수
  private static int lookbackDays(int longWindow) {
    return longWindow * 3 / 2 + 14;
  }

  // 쓸 수 있는 일봉(오래된 날부터)과 이동평균에 쓰는 값의 누적 합 · 두 기간
  private record MovingAverages(List<OHLCPriceDto> candles, BigDecimal[] sums, int shortWindow, int longWindow) {

    static MovingAverages of(Map<LocalDate, OHLCPriceDto> priceMap, int shortWindow, int longWindow) {
      List<OHLCPriceDto> candles = priceMap.values().stream()
        .filter(PriceLookup::isUsable)
        .sorted(Comparator.comparing(OHLCPriceDto::date))
        .toList();
      BigDecimal[] sums = new BigDecimal[candles.size() + 1];
      sums[0] = BigDecimal.ZERO;
      for (int i = 0; i < candles.size(); i++) {
        sums[i + 1] = sums[i].add(signalValue(candles.get(i)));
      }
      return new MovingAverages(candles, sums, shortWindow, longWindow);
    }

    // 이동평균에 쓰는 값(수정 종가, 없거나 0 이하면 종가)
    private static BigDecimal signalValue(OHLCPriceDto candle) {
      BigDecimal adjusted = candle.adjustedClose();
      return adjusted != null && adjusted.signum() > 0 ? adjusted : PriceLookup.effectiveClose(candle);
    }

    // i 번째 일봉의 교차(앞 일봉 짧은 선 - 긴 선 ≤ 0 · 그 날 > 0 이면 골든, 앞 ≥ 0 · 그 날 < 0 이면 데드, i 는 긴 기간 이상)
    SignalChart.CrossType cross(int i) {
      int before = side(i - 1);
      int today = side(i);
      if (before <= 0 && today > 0) {
        return SignalChart.CrossType.GOLDEN;
      }
      return before >= 0 && today < 0 ? SignalChart.CrossType.DEAD : null;
    }

    // i 번째 일봉에서 짧은 선 - 긴 선의 부호(두 합에 서로의 기간을 곱해 나눗셈 없이 견줌)
    private int side(int i) {
      BigDecimal shortSum = sums[i + 1].subtract(sums[i + 1 - shortWindow]);
      BigDecimal longSum = sums[i + 1].subtract(sums[i + 1 - longWindow]);
      return shortSum.multiply(BigDecimal.valueOf(longWindow))
        .compareTo(longSum.multiply(BigDecimal.valueOf(shortWindow)));
    }

    // i 번째 일봉까지 window 거래일 평균(소수 둘째, 일봉이 모자라면 null)
    private BigDecimal average(int i, int window) {
      return i + 1 < window ? null
        : sums[i + 1].subtract(sums[i + 1 - window]).divide(BigDecimal.valueOf(window), MoneyUtils.USD_SCALE,
          MoneyUtils.ROUND_MODE);
    }

    // 시작일부터 일봉마다 수정 종가 · 두 평균과 교차한 날
    SignalChart chart(String symbol, LocalDate start) {
      List<SignalChart.Point> points = new ArrayList<>();
      List<SignalChart.Cross> crosses = new ArrayList<>();
      for (int i = 0; i < candles.size(); i++) {
        LocalDate date = candles.get(i).date();
        if (date.isBefore(start)) {
          continue;
        }
        points.add(new SignalChart.Point(date, MoneyUtils.roundUsd(signalValue(candles.get(i))),
          average(i, shortWindow), average(i, longWindow)));
        SignalChart.CrossType type = i >= longWindow ? cross(i) : null;
        if (type != null) {
          crosses.add(new SignalChart.Cross(date, type));
        }
      }
      return new SignalChart(symbol, points, crosses);
    }
  }

  // 그 날짜 종가에 원금(첫 매수) 또는 판 돈 전부로 삼(수수료를 뺀 달러로 소수 주)
  private static StrategyTransaction buy(MaCrossStrategyRequest request, OHLCPriceDto candle, FxRates fxRates,
    BigDecimal cash) {
    LocalDate date = candle.date();
    BigDecimal price = PriceLookup.effectiveClose(candle);
    BigDecimal fxRate = fxOn(request, fxRates, date);
    StrategyTransaction.StrategyTransactionBuilder builder = StrategyTransaction.builder()
      .date(date)
      .actualDate(date)
      .price(price)
      .fxRate(fxRate)
      .defaultFxDate(fxRates.isDefault(date) ? date : null)
      .trigger(TransactionTrigger.GOLDEN_CROSS);
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

  // 그 날짜 종가에 전량 팔고 매도 수수료를 뺀 판 돈 · 보유분 원가 대비 실현 손익을 남김
  private static StrategyTransaction sell(MaCrossStrategyRequest request, OHLCPriceDto candle, FxRates fxRates,
    BigDecimal shares, BigDecimal cost) {
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
      .trigger(TransactionTrigger.DEAD_CROSS)
      .build();
  }

  // 그 날짜 환율(수동 환율이 있으면 그 값)
  private static BigDecimal fxOn(MaCrossStrategyRequest request, FxRates fxRates, LocalDate date) {
    return request.getPurchaseFxRate() != null ? request.getPurchaseFxRate() : fxRates.get(date);
  }
}
