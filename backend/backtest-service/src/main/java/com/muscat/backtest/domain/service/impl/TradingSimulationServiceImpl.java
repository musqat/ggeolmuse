package com.muscat.backtest.domain.service.impl;

import com.muscat.backtest.common.calculation.PerformanceCalculator;
import com.muscat.backtest.common.constants.BacktestConstants;
import com.muscat.backtest.common.engine.PurchaseDayReturns;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.common.util.BacktestHistoryUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.FxPath;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.common.validation.BacktestRequestValidator;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.DailyValue;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.HoldingStep;
import com.muscat.backtest.domain.model.OptimalTiming;
import com.muscat.backtest.domain.model.SimulationCalculationResult;
import com.muscat.backtest.domain.model.SimulationContext;
import com.muscat.backtest.domain.service.TradingSimulationService;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.backtest.infra.client.dto.DividendDto;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingSimulationServiceImpl implements TradingSimulationService {

  private final MarketDataClientWrapper marketDataClientWrapper;
  private final ResponseMapper responseMapper;
  private final BacktestHistoryUtils backtestHistoryUtils;

  @Override
  public SimulationResponse runSimulation(SimulationRequest request) {
    return runSimulation(request, true);
  }

  @Override
  public SimulationResponse runSimulation(SimulationRequest request, boolean recordHistory) {
    BacktestLogger.setBacktestContext(request.getUserId(), "SIMULATION", request.getSymbol());
    log.info("백테스팅 시뮬레이션 시작: {}", request);

    try {
      SimulationContext context = prepareSimulationContext(request);
      SimulationCalculationResult calculation = calculateSimulation(context);
      SimulationResponse response = buildSimulationResponse(context, calculation);

      if (recordHistory) {
        recordSimulationHistory(request);
      }

      return response;
    } finally {
      BacktestLogger.remove("operation");
    }
  }

  // ====== 헬퍼 메소드 ======

  // 시뮬레이션 입력 컨텍스트 구성 (매수가·환율·평가일 시세·배당이력 조회)
  private SimulationContext prepareSimulationContext(SimulationRequest request) {
    if (request.getSaleDate() != null) {
      BacktestRequestValidator.requireDateRange(request.getPurchaseDate(), request.getSaleDate());
    }
    LocalDate today = LocalDate.now();
    LocalDate valuationDate = request.valuationDate(today);
    boolean valuedToday = valuationDate.equals(today);

    Map<LocalDate, OHLCPriceDto> priceMap = loadPriceMap(request.getSymbol(),
      request.getPurchaseDate(), valuationDate);

    OHLCPriceDto purchaseData = getPurchasePrice(request.getSymbol(),
      request.getPurchaseDate(), valuationDate, priceMap);
    LocalDate tradeDate = purchaseData.date();

    // 수동 환율이 설정되어 있으면 사용, 없으면 실제로 산 날 환율
    FxRateDto purchaseFxRate;
    if (request.getPurchaseFxRate() != null) {
      purchaseFxRate = new FxRateDto(tradeDate, request.getPurchaseFxRate());
    } else {
      purchaseFxRate = marketDataClientWrapper.getFxRate(tradeDate.toString());
    }

    // 평가일 시세(오늘이면 현재가, 과거면 그날 조정 종가)
    BigDecimal valuationPrice = valuedToday
      ? marketDataClientWrapper.getCurrentPrice(request.getSymbol()).currentPrice()
      : PriceLookup.effectiveClose(getHistoricalPrice(request.getSymbol(), valuationDate, priceMap));

    FxRateDto currentFxRate;
    if (request.getCurrentFxRate() != null) {
      currentFxRate = new FxRateDto(valuationDate, request.getCurrentFxRate());
    } else if (valuedToday) {
      currentFxRate = marketDataClientWrapper.getLatestFxRate();
    } else {
      currentFxRate = marketDataClientWrapper.getFxRate(valuationDate.toString());
    }

    // 배당 이력 조회 (실제 매수일 ~ 평가일)
    List<DividendDto> dividendList = marketDataClientWrapper.getDividendHistory(
      request.getSymbol(), tradeDate.toString(), valuationDate.toString());
    DividendHistoryDto dividendHistory = DividendHistoryDto.of(request.getSymbol(), dividendList);

    return new SimulationContext(request, purchaseData, purchaseFxRate, valuationPrice,
      currentFxRate, dividendHistory, valuationDate, priceMap);
  }

  // 손익 계산 (매수가·주식수·수수료·배당재투자·환차익 → 총자산/수익률)
  private SimulationCalculationResult calculateSimulation(SimulationContext context) {
    BigDecimal purchasePriceUsd = PriceLookup.effectiveClose(context.purchaseData());
    BigDecimal purchaseFxRate = context.purchaseFxRate().rate();
    BigDecimal currentFxRate = context.currentFxRate().rate();
    BigDecimal currentPriceUsd = context.valuationPrice();

    BigDecimal usdAmount = MoneyUtils.convertKrwToUsd(
      context.request().getInvestmentAmount(), purchaseFxRate);
    log.debug("환전 계산: KRW {} → USD {} (환율: {})",
      context.request().getInvestmentAmount(), usdAmount, purchaseFxRate);

    BigDecimal shares = BacktestCalculationUtils.calculateSharesWithFee(
      usdAmount, purchasePriceUsd, context.request().getTradingFeeRate());
    BigDecimal tradingFee = BacktestCalculationUtils.calculateTradingFee(
      usdAmount, context.request().getTradingFeeRate());
    BigDecimal totalCost = BacktestCalculationUtils.calculateTotalCost(
      shares, purchasePriceUsd, tradingFee);
    BigDecimal remainingCash = BacktestCalculationUtils.calculateRemainingCash(usdAmount,
      totalCost);

    // 매수일부터의 보유 단계
    List<HoldingStep> holdingSteps = new ArrayList<>();
    holdingSteps.add(new HoldingStep(context.purchaseData().date(), shares, remainingCash));

    BigDecimal currentValueUsd = shares.multiply(currentPriceUsd);
    BigDecimal currentValueKrw = MoneyUtils.convertUsdToKrw(currentValueUsd, currentFxRate);

    BigDecimal stockReturn = currentPriceUsd.subtract(purchasePriceUsd);
    BigDecimal stockReturnPercent = MoneyUtils.calculateReturnRate(purchasePriceUsd,
      currentPriceUsd);
    BigDecimal fxReturn = currentFxRate.subtract(purchaseFxRate);
    BigDecimal fxReturnPercent = MoneyUtils.calculateReturnRate(purchaseFxRate, currentFxRate);

    // 재투자된 배당금 추적
    BigDecimal dividendsReinvested = BigDecimal.ZERO;
    BigDecimal grossDividends = BigDecimal.ZERO; // 배당마다 그때 주식 수로 받은 세전 배당의 합
    List<LocalDate> dividendReinvestDates = new ArrayList<>(); // 배당 재투자 날짜 목록

    // 배당금 재투자 처리 - 각 배당일에 순차적으로 재투자
    if (Boolean.TRUE.equals(context.request().getReinvestDividends())
      && context.dividendHistory() != null
      && context.dividendHistory().getDividends() != null
      && !context.dividendHistory().getDividends().isEmpty()) {

      log.info("배당금 재투자 실행 시작: {} 개의 배당 내역", context.dividendHistory().getDividends().size());

      LocalDate purchaseDate = context.purchaseData().date();

      // 배당 내역을 ex-date 순으로 정렬해 순차 재투자
      var dividends = context.dividendHistory().getDividends().stream()
        .filter(d -> d.getExDate() != null)
        .filter(d -> BacktestCalculationUtils.receivesDividend(purchaseDate, d.getExDate())
          && !d.getExDate().isAfter(context.valuationDate()))
        .sorted((d1, d2) -> d1.getExDate().compareTo(d2.getExDate()))
        .toList();

      for (var dividend : dividends) {
        // 이 배당금 계산 (현재 보유 주식수 기준)
        BigDecimal dividendAmount = dividend.getAmount().multiply(shares)
          .setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);
        if (!Decimals.isPositive(dividendAmount)) {
          continue;
        }
        grossDividends = grossDividends.add(dividendAmount);

        // 배당 원천징수 적용 (설정된 경우)
        BigDecimal taxRate = context.request().getDividendTaxRate();
        BigDecimal afterTaxDividend = dividendAmount;
        if (taxRate != null && Decimals.isPositive(taxRate)) {
          BigDecimal taxAmount = dividendAmount.multiply(taxRate)
            .setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);
          afterTaxDividend = dividendAmount.subtract(taxAmount);
          log.info("배당 원천징수: {} - 배당금 ${} → 세후 ${} (세율 {}%)",
            dividend.getExDate(), dividendAmount, afterTaxDividend,
            taxRate.multiply(BacktestConstants.Money.PERCENTAGE_MULTIPLIER));
        }

        // 배당일 주가(휴장일이면 그 전 거래일)
        BigDecimal dividendDayPrice = PriceLookup.effectiveClose(getHistoricalPrice(
          context.request().getSymbol(), dividend.getExDate(), context.priceMap()));

        // 세후 배당금으로 매수 가능한 주식수
        BigDecimal additionalShares = afterTaxDividend.divide(
          dividendDayPrice, 8, java.math.RoundingMode.HALF_UP);

        shares = shares.add(additionalShares);
        dividendsReinvested = dividendsReinvested.add(afterTaxDividend);
        dividendReinvestDates.add(dividend.getExDate());
        holdingSteps.add(new HoldingStep(dividend.getExDate(), shares, remainingCash));

        log.info("배당 재투자: {} - ${} ({}주 보유) -> {}주 추가 매수 @${}",
          dividend.getExDate(), afterTaxDividend, shares, additionalShares, dividendDayPrice);
      }

      log.info("배당금 재투자 완료: 총 ${} 재투자, 최종 보유 {}주", dividendsReinvested, shares);
    }

    // 재투자 후 현재 가치 재계산
    currentValueUsd = shares.multiply(currentPriceUsd);
    currentValueKrw = MoneyUtils.convertUsdToKrw(currentValueUsd, currentFxRate);

    // 받은 배당 총액(세전, 재투자했으면 위에서 더한 값, 아니면 처음 주식 수로)
    boolean reinvested = Boolean.TRUE.equals(context.request().getReinvestDividends());
    if (!reinvested) {
      holdingSteps.addAll(cashDividendSteps(context, shares, remainingCash));
    }
    BigDecimal totalDividends = reinvested
      ? grossDividends
      : BacktestCalculationUtils.calculateTotalDividends(
        context.dividendHistory(), shares, context.purchaseData().date(), context.valuationDate());

    BigDecimal dividendYield = BacktestCalculationUtils.calculateDividendYield(
      totalDividends, shares, currentPriceUsd);

    // 재투자를 끄면 세후 배당을 현금으로(켜면 이미 주식 수에 포함)
    BigDecimal cashDividendKrw = BigDecimal.ZERO;
    if (!reinvested && Decimals.isPositive(totalDividends)) {
      BigDecimal dividendTaxRate = context.request().getDividendTaxRate();
      BigDecimal afterTax = dividendTaxRate != null && Decimals.isPositive(dividendTaxRate)
        ? totalDividends.subtract(totalDividends.multiply(dividendTaxRate))
        : totalDividends;
      cashDividendKrw = MoneyUtils.convertUsdToKrw(afterTax, currentFxRate);
    }

    // 남은 현금도 자산에 포함 (USD → KRW 환산)
    BigDecimal remainingCashKrw = Decimals.isPositive(remainingCash)
      ? MoneyUtils.convertUsdToKrw(remainingCash, currentFxRate)
      : BigDecimal.ZERO;

    BigDecimal totalAssetKrw = currentValueKrw.add(remainingCashKrw).add(cashDividendKrw);

    // 총 수익 = 총 자산 - 투자금
    BigDecimal totalReturnKrw = totalAssetKrw.subtract(context.request().getInvestmentAmount());

    // 총 수익률 = (총 자산 / 투자금) - 1
    BigDecimal totalReturnPercent = MoneyUtils.calculateReturnRate(
      context.request().getInvestmentAmount(), totalAssetKrw);

    return new SimulationCalculationResult(
      purchasePriceUsd, shares, currentPriceUsd, currentValueUsd, currentValueKrw,
      stockReturn, stockReturnPercent, purchaseFxRate, currentFxRate, fxReturn, fxReturnPercent,
      totalDividends, dividendYield, tradingFee, remainingCash, totalAssetKrw, totalReturnKrw,
      totalReturnPercent,
      dividendsReinvested, dividendReinvestDates, holdingSteps);
  }

  // 재투자를 끄면 배당락일마다 세후 배당이 현금으로 쌓임
  private static List<HoldingStep> cashDividendSteps(SimulationContext context, BigDecimal shares,
    BigDecimal remainingCash) {
    if (context.dividendHistory() == null || context.dividendHistory().getDividends() == null) {
      return List.of();
    }
    LocalDate purchaseDate = context.purchaseData().date();
    BigDecimal taxRate = context.request().getDividendTaxRate();
    List<DividendHistoryDto.DividendPayment> received = context.dividendHistory().getDividends().stream()
      .filter(d -> d.getExDate() != null && d.getAmount() != null)
      .filter(d -> BacktestCalculationUtils.receivesDividend(purchaseDate, d.getExDate())
        && !d.getExDate().isAfter(context.valuationDate()))
      .sorted(Comparator.comparing(DividendHistoryDto.DividendPayment::getExDate))
      .toList();

    List<HoldingStep> steps = new ArrayList<>();
    BigDecimal cash = remainingCash;
    for (DividendHistoryDto.DividendPayment dividend : received) {
      BigDecimal gross = dividend.getAmount().multiply(shares);
      BigDecimal afterTax = taxRate != null && Decimals.isPositive(taxRate)
        ? gross.subtract(gross.multiply(taxRate))
        : gross;
      cash = cash.add(afterTax);
      steps.add(new HoldingStep(dividend.getExDate(), shares, cash));
    }
    return steps;
  }

  // 최적 타이밍 · 일별 가치 · 지표를 더한 응답
  private SimulationResponse buildSimulationResponse(SimulationContext context,
    SimulationCalculationResult calculation) {

    // 매수일 ~ 평가일 일봉과 그날 환율
    List<OHLCPriceDto> candles = candlesBetween(context.priceMap(), context.purchaseData().date(),
      context.valuationDate());
    Map<String, BigDecimal> fxRates = bulkFxRates(candles);
    OptimalTiming optimalTiming = calculateOptimalTiming(candles, fxRates);

    SimulationResponse response = responseMapper.toSimulationResponse(
      context.request(),
      calculation.purchasePriceUsd(),
      calculation.shares(),
      calculation.currentPriceUsd(),
      calculation.currentValueUsd(),
      calculation.currentValueKrw(),
      calculation.stockReturn(),
      calculation.stockReturnPercent(),
      calculation.purchaseFxRate(),
      calculation.currentFxRate(),
      calculation.fxReturn(),
      calculation.fxReturnPercent(),
      calculation.totalDividends(),
      calculation.dividendYield(),
      calculation.tradingFee(),
      calculation.remainingCash(),
      calculation.totalAssetKrw(),
      calculation.totalReturnKrw(),
      calculation.totalReturnPercent(),
      optimalTiming.buyDate(),
      optimalTiming.buyPrice(),
      optimalTiming.sellDate(),
      optimalTiming.sellPrice(),
      optimalTiming.returnPercent(),
      calculation.dividendsReinvested(),
      calculation.dividendReinvestDates());
    // 응답의 매수일은 실제로 산 날
    response.setPurchaseDate(context.purchaseData().date());
    response.setDefaultFxDates(defaultFxDates(context));

    Function<LocalDate, BigDecimal> fx = fxPath(context, calculation, fxRates);
    List<DailyValue> dailyValues = dailyValues(candles, fx, calculation, context);
    // 원금: 원화는 투자금, 달러는 그것을 매수 환율로 바꾼 값
    BigDecimal krwCapital = context.request().getInvestmentAmount();
    BigDecimal usdCapital = MoneyUtils.convertKrwToUsd(krwCapital, calculation.purchaseFxRate());
    List<Double> usdIndex = index(dailyValues, usdCapital, DailyValue::valueUsd);
    List<Double> krwIndex = index(dailyValues, krwCapital, DailyValue::valueKrw);
    List<LocalDate> dates = dailyValues.stream().map(DailyValue::date).toList();

    response.setMetricsKrw(PerformanceCalculator.compute(dates, krwIndex, context.request().getPurchaseDate()));
    response.setMetricsUsd(PerformanceCalculator.compute(dates, usdIndex, context.request().getPurchaseDate()));
    response.setDailyValues(DailyValue.withDrawdowns(dailyValues,
      PerformanceCalculator.drawdownPercents(usdIndex), PerformanceCalculator.drawdownPercents(krwIndex)));
    response.setPurchaseDayReturns(PurchaseDayReturns.of(candles, purchaseFx(context, calculation, fx),
      new PurchaseDayReturns.Inputs(context.request().getInvestmentAmount(), context.request().getTradingFeeRate(),
        Boolean.TRUE.equals(context.request().getReinvestDividends()), context.request().getDividendTaxRate(),
        context.dividendHistory(), exDatePrices(context), calculation.currentPriceUsd(), calculation.currentFxRate(),
        context.valuationDate())));
    return response;
  }

  // 매수일별 산 날 환율: 실제로 산 날은 결과의 매수 환율, 수동 환율이면 그 값, 아니면 일별 환율 경로
  private static Function<LocalDate, BigDecimal> purchaseFx(SimulationContext context,
    SimulationCalculationResult calculation, Function<LocalDate, BigDecimal> fx) {
    LocalDate tradeDate = context.purchaseData().date();
    BigDecimal manual = context.request().getPurchaseFxRate();
    return date -> date.equals(tradeDate) ? calculation.purchaseFxRate() : manual != null ? manual : fx.apply(date);
  }

  // 재투자면 받는 배당(금액이 있는 것)의 배당락일 주가(결과 계산과 같은 조회, 재투자가 아니면 비움)
  private Map<LocalDate, BigDecimal> exDatePrices(SimulationContext context) {
    Map<LocalDate, BigDecimal> prices = new HashMap<>();
    if (!Boolean.TRUE.equals(context.request().getReinvestDividends()) || context.dividendHistory() == null
      || context.dividendHistory().getDividends() == null) {
      return prices;
    }
    LocalDate tradeDate = context.purchaseData().date();
    for (DividendHistoryDto.DividendPayment dividend : context.dividendHistory().getDividends()) {
      LocalDate exDate = dividend.getExDate();
      if (exDate != null && dividend.getAmount() != null && Decimals.isPositive(dividend.getAmount())
        && BacktestCalculationUtils.receivesDividend(tradeDate, exDate)
        && !exDate.isAfter(context.valuationDate())) {
        prices.computeIfAbsent(exDate, date -> PriceLookup.effectiveClose(
          getHistoricalPrice(context.request().getSymbol(), date, context.priceMap())));
      }
    }
    return prices;
  }

  // 매수일 ~ 평가일 일봉(날짜순)
  private static List<OHLCPriceDto> candlesBetween(Map<LocalDate, OHLCPriceDto> priceMap,
    LocalDate startDate, LocalDate endDate) {
    return priceMap.values().stream()
      .filter(candle -> !candle.date().isBefore(startDate) && !candle.date().isAfter(endDate))
      .sorted(Comparator.comparing(OHLCPriceDto::date))
      .toList();
  }

  // 일봉 날짜마다 환율(조회가 실패하면 null)
  private Map<String, BigDecimal> bulkFxRates(List<OHLCPriceDto> candles) {
    if (candles.isEmpty()) {
      return Map.of();
    }
    try {
      return marketDataClientWrapper.getBulkFxRates(
        candles.stream().map(candle -> candle.date().toString()).toList());
    } catch (Exception e) {
      log.warn("기간 환율 조회 실패: {}", e.getMessage());
      return null;
    }
  }

  // 일별 원화 환율: 수동 환율이거나 기간 환율 기록이 없으면 매수 · 평가일 환율을 날짜 비례로, 아니면 시장 환율
  private static Function<LocalDate, BigDecimal> fxPath(SimulationContext context,
    SimulationCalculationResult calculation, Map<String, BigDecimal> fxRates) {
    SimulationRequest request = context.request();
    if (request.getPurchaseFxRate() != null || request.getCurrentFxRate() != null
      || fxRates == null || fxRates.isEmpty()) {
      return FxPath.interpolated(context.purchaseData().date(), calculation.purchaseFxRate(),
        context.valuationDate(), calculation.currentFxRate());
    }
    return FxPath.market(fxRecords(fxRates), calculation.purchaseFxRate());
  }

  // 날짜 문자열 키를 날짜로 바꾼 환율
  private static Map<LocalDate, BigDecimal> fxRecords(Map<String, BigDecimal> fxRates) {
    Map<LocalDate, BigDecimal> records = new HashMap<>();
    if (fxRates != null) {
      fxRates.forEach((date, rate) -> records.put(LocalDate.parse(date), rate));
    }
    return records;
  }

  // 일봉마다 보유 주식 × 종가 + 현금(원화는 그날 경로 환율), 평가일은 결과의 총자산
  private static List<DailyValue> dailyValues(List<OHLCPriceDto> candles, Function<LocalDate, BigDecimal> fx,
    SimulationCalculationResult calculation, SimulationContext context) {
    List<HoldingStep> steps = calculation.holdingSteps();
    List<DailyValue> values = new ArrayList<>();
    int step = 0;
    for (OHLCPriceDto candle : candles) {
      if (!candle.date().isBefore(context.valuationDate())) {
        break;
      }
      if (!PriceLookup.isUsable(candle)) {
        continue;
      }
      while (step + 1 < steps.size() && !steps.get(step + 1).from().isAfter(candle.date())) {
        step++;
      }
      HoldingStep holding = steps.get(step);
      BigDecimal valueUsd = MoneyUtils.roundUsd(
        PriceLookup.effectiveClose(candle).multiply(holding.shares()).add(holding.cashUsd()));
      values.add(new DailyValue(candle.date(), valueUsd, DailyValue.toKrw(valueUsd, fx.apply(candle.date()))));
    }
    values.add(new DailyValue(context.valuationDate(),
      calculation.totalAssetKrw().divide(calculation.currentFxRate(), 2, RoundingMode.HALF_UP),
      calculation.totalAssetKrw()));
    return values;
  }

  // 원금을 1 로 둔 일별 지수
  private static List<Double> index(List<DailyValue> values, BigDecimal capital,
    Function<DailyValue, BigDecimal> value) {
    List<Double> index = new ArrayList<>();
    double base = capital.doubleValue();
    for (DailyValue daily : values) {
      index.add(value.apply(daily).doubleValue() / base);
    }
    return index;
  }

  // 산 날 · 평가일 가운데 환율이 없어 기본 환율로 계산한 날
  private static List<LocalDate> defaultFxDates(SimulationContext context) {
    return java.util.stream.Stream.of(context.purchaseFxRate(), context.currentFxRate())
      .filter(FxRateDto::defaulted)
      .map(FxRateDto::date)
      .distinct()
      .sorted()
      .toList();
  }

  // 최적 타이밍 계산 (원화 주가 기준, 매수일 <= 매도일 제약 아래 최대 수익)
  private OptimalTiming calculateOptimalTiming(List<OHLCPriceDto> ordered, Map<String, BigDecimal> fxRates) {
    if (ordered.isEmpty() || fxRates == null) {
      return OptimalTiming.empty();
    }
    try {
      // 전 구간 최저가 · 최고가를 따로 뽑으면 매도일이 매수일보다 앞설 수 있음
      // 날짜순으로 그때까지 원화 주가가 가장 낮은 날에 산 수익률만 계산
      Function<LocalDate, BigDecimal> fx = FxPath.market(fxRecords(fxRates), marketDataClientWrapper.defaultFxRate());
      OHLCPriceDto minSoFar = ordered.get(0);
      OHLCPriceDto bestBuy = ordered.get(0);
      OHLCPriceDto bestSell = ordered.get(0);
      BigDecimal bestReturn = BigDecimal.ZERO;

      for (OHLCPriceDto candle : ordered) {
        if (krwPrice(candle, fx).compareTo(krwPrice(minSoFar, fx)) < 0) {
          minSoFar = candle;
        }

        BigDecimal ret = MoneyUtils.calculateReturnRate(
          krwPrice(minSoFar, fx), krwPrice(candle, fx));

        if (ret.compareTo(bestReturn) > 0) {
          bestReturn = ret;
          bestBuy = minSoFar;
          bestSell = candle;
        }
      }

      // 내내 하락이면 매수일 = 매도일 = 시작일 · 수익률 0(사지 않는 게 최선)
      return new OptimalTiming(
        bestBuy.date(),
        PriceLookup.effectiveClose(bestBuy),
        bestSell.date(),
        PriceLookup.effectiveClose(bestSell),
        bestReturn
      );
    } catch (Exception e) {
      log.warn("최적 타이밍 계산 실패: {}", e.getMessage());
      return OptimalTiming.empty();
    }
  }

  private static BigDecimal krwPrice(OHLCPriceDto candle, Function<LocalDate, BigDecimal> fx) {
    return PriceLookup.effectiveClose(candle).multiply(fx.apply(candle.date()));
  }

  // 백테스트 이력 저장 (userId 없으면 skip)
  private void recordSimulationHistory(SimulationRequest request) {
    if (request.getUserId() == null) {
      return;
    }
    backtestHistoryUtils.saveBacktestHistory(request.getUserId(), BacktestType.COMPARISON, request);
  }


  // 매수일 5일 전 ~ 평가일 일봉(못 받으면 빈 맵이라 날짜별 조회로)
  private Map<LocalDate, OHLCPriceDto> loadPriceMap(String symbol, LocalDate purchaseDate,
    LocalDate valuationDate) {
    try {
      return BacktestDataUtils.buildPriceMap(marketDataClientWrapper, symbol, purchaseDate.minusDays(5),
        valuationDate);
    } catch (RuntimeException e) {
      log.warn("기간 시세 조회 실패, 날짜별 조회로: symbol={}, error={}", symbol, e.getMessage());
      return Map.of();
    }
  }

  // 매수 시세, 휴장일이면 평가일까지 다음 거래일(7일 안) · 그런 날이 없으면 그 전 거래일(기간 일봉에 없을 때만 날짜별 조회)
  private OHLCPriceDto getPurchasePrice(String symbol, LocalDate purchaseDate, LocalDate valuationDate,
    Map<LocalDate, OHLCPriceDto> priceMap) {
    OHLCPriceDto fromRange = PriceLookup.nextFromMap(priceMap, purchaseDate, valuationDate, 7);
    if (fromRange == null) {
      fromRange = PriceLookup.fromMap(priceMap, purchaseDate, 5);
    }
    if (fromRange != null) {
      return fromRange;
    }
    OHLCPriceDto next = PriceLookup.nextAvailable(
      marketDataClientWrapper::getOHLCPrice, symbol, purchaseDate, valuationDate, 7);
    return next != null ? next : getHistoricalPriceWithRetry(symbol, purchaseDate);
  }

  // 그날 시세(휴장일이면 그 전 거래일), 기간 일봉에 없을 때만 날짜별 조회
  private OHLCPriceDto getHistoricalPrice(String symbol, LocalDate date, Map<LocalDate, OHLCPriceDto> priceMap) {
    OHLCPriceDto fromRange = PriceLookup.fromMap(priceMap, date, 5);
    return fromRange != null ? fromRange : getHistoricalPriceWithRetry(symbol, date);
  }

  // 주가 데이터 조회 with retry (시장 휴일 대응)
  private OHLCPriceDto getHistoricalPriceWithRetry(String symbol, LocalDate date) {
    log.info("주가 데이터 요청: symbol={}, date={}", symbol, date);
    return PriceLookup.withFallback(marketDataClientWrapper::getOHLCPrice, symbol, date, 5);
  }
}
