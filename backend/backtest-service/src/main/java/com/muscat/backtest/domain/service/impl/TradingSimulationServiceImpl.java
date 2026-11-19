package com.muscat.backtest.domain.service.impl;

import com.muscat.backtest.domain.calculation.CapitalGainsTaxCalculator;
import com.muscat.backtest.domain.engine.PeriodEndSale;
import com.muscat.backtest.domain.engine.PurchaseDayReturns;
import com.muscat.backtest.domain.engine.SimulationCalculator;
import com.muscat.backtest.domain.engine.SimulationPerformance;
import com.muscat.backtest.domain.engine.StrategyPerformance;
import com.muscat.backtest.domain.engine.YearEndHarvest;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.domain.calculation.BacktestCalculationUtils;
import com.muscat.backtest.domain.service.BacktestDataUtils;
import com.muscat.backtest.domain.service.BacktestHistoryUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.FxPath;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.domain.validation.BacktestRequestValidator;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.CapitalGainsTax;
import com.muscat.backtest.domain.dto.response.PurchaseDayReturn;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.OptimalTiming;
import com.muscat.backtest.domain.model.SimulationCalculationResult;
import com.muscat.backtest.domain.model.SimulationContext;
import com.muscat.backtest.domain.service.TradingSimulationService;
import com.muscat.backtest.domain.port.MarketDataPort;
import com.muscat.backtest.domain.dto.market.FxRateDto;
import com.muscat.backtest.domain.dto.market.DividendDto;
import com.muscat.backtest.domain.dto.market.DividendHistoryDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
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

  private final MarketDataPort marketDataPort;
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
      SimulationResponse response = simulate(request);
      compareYearEnd(request, response);

      if (recordHistory) {
        recordSimulationHistory(request);
      }

      return response;
    } finally {
      BacktestLogger.remove("operation");
    }
  }

  // ====== 헬퍼 메소드 ======

  // 시세 · 환율을 받아 계산한 시뮬레이션 결과
  private SimulationResponse simulate(SimulationRequest request) {
    SimulationContext context = prepareSimulationContext(request);
    // 매수일 ~ 평가일 일봉과 일봉 날짜마다 환율
    List<OHLCPriceDto> candles = candlesBetween(context.priceMap(), context.purchaseData().date(),
      context.valuationDate());
    Map<String, BigDecimal> fxRates = bulkFxRates(candles);
    Function<LocalDate, BigDecimal> fx = fxPath(context, fxRates);
    SimulationCalculationResult calculation = SimulationCalculator.calculate(context, fx, closeOn(context));
    return buildSimulationResponse(context, calculation, candles, fxRates, fx);
  }

  // 연말 정리를 켠 2년 이상 결과에 기간 끝에 한 번에 판 양도세와 연말 정리하고 기간 끝에 판 양도세 비교를 더함
  private void compareYearEnd(SimulationRequest request, SimulationResponse response) {
    if (!YearEndHarvest.compares(request.getCapitalGainsTax(), request.getYearEndHarvest(), request.getPurchaseDate(),
      request.valuationDate(LocalDate.now()))) {
      return;
    }
    BigDecimal sellAtEndTax = simulate(request.yearEndVariant(false)).getCapitalGainsTax().totalTaxKrw();
    BigDecimal harvestTax = PeriodEnd.sells(request.getPeriodEnd())
      ? response.getCapitalGainsTax().totalTaxKrw()
      : simulate(request.yearEndVariant(true)).getCapitalGainsTax().totalTaxKrw();
    response.setCapitalGainsTax(response.getCapitalGainsTax().withHarvestComparison(
      CapitalGainsTax.HarvestComparison.of(sellAtEndTax, harvestTax)));
  }

  // 시뮬레이션 입력 컨텍스트 구성 (매수가·환율·평가일 시세·배당이력 조회)
  private SimulationContext prepareSimulationContext(SimulationRequest request) {
    BacktestRequestValidator.requireTradable(request.getSymbol());
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
      purchaseFxRate = marketDataPort.getFxRate(tradeDate.toString());
    }

    // 평가일 시세(오늘이면 현재가, 과거면 평가일 조정 종가)
    BigDecimal valuationPrice = valuedToday
      ? marketDataPort.getCurrentPrice(request.getSymbol()).currentPrice()
      : PriceLookup.effectiveClose(getHistoricalPrice(request.getSymbol(), valuationDate, priceMap));

    FxRateDto currentFxRate;
    if (request.getCurrentFxRate() != null) {
      currentFxRate = new FxRateDto(valuationDate, request.getCurrentFxRate());
    } else if (valuedToday) {
      currentFxRate = marketDataPort.getLatestFxRate();
    } else {
      currentFxRate = marketDataPort.getFxRate(valuationDate.toString());
    }

    // 배당 이력 조회 (실제 매수일 ~ 평가일, 기간 끝에 팔면 판 날까지)
    LocalDate dividendsUntil = PeriodEnd.sells(request.getPeriodEnd())
      ? PeriodEndSale.saleDate(priceMap, valuationDate)
      : valuationDate;
    List<DividendDto> dividendList = marketDataPort.getDividendHistory(
      request.getSymbol(), tradeDate.toString(), dividendsUntil.toString());
    DividendHistoryDto dividendHistory = DividendHistoryDto.of(request.getSymbol(), dividendList);

    return new SimulationContext(request, purchaseData, purchaseFxRate, valuationPrice,
      currentFxRate, dividendHistory, valuationDate, priceMap);
  }

  // 최적 타이밍 · 일별 가치 · 지표를 더한 응답
  private SimulationResponse buildSimulationResponse(SimulationContext context,
                                                     SimulationCalculationResult calculation, List<OHLCPriceDto> candles, Map<String, BigDecimal> fxRates,
                                                     Function<LocalDate, BigDecimal> fx) {
    OptimalTiming optimalTiming = calculateOptimalTiming(candles, fxRates);

    SimulationResponse response = responseMapper.toSimulationResponse(context.request(), calculation, optimalTiming);
    // 응답의 매수일은 실제로 산 날, 투자 금액은 주식 수로 샀으면 산 날 비용
    response.setPurchaseDate(context.purchaseData().date());
    response.setInvestmentAmount(calculation.investmentKrw());
    response.setDefaultFxDates(defaultFxDates(context));
    response.setDividendUnavailableSymbols(context.dividendHistory().isUnavailable()
      ? List.of(context.request().getSymbol())
      : List.of());

    response.setCapitalGainsTax(Boolean.TRUE.equals(context.request().getCapitalGainsTax())
      ? CapitalGainsTaxCalculator.of(CapitalGainsTaxCalculator.realizedGains(calculation.taxTransactions()),
      CapitalGainsTaxCalculator.Payments.NONE, calculation.totalAssetKrw(), calculation.currentValueKrw(),
      calculation.investmentKrw())
      : null);
    response.setTransactions(calculation.transactions());
    StrategyPerformance.Result performance = SimulationPerformance.of(candles, fx, calculation, context.valuationDate(),
      context.request().getPurchaseDate());
    response.setMetricsKrw(performance.metricsKrw());
    response.setMetricsUsd(performance.metricsUsd());
    response.setDailyValues(performance.dailyValues());
    response.setPurchaseDayReturns(purchaseDayReturns(candles, context, calculation, fxRates));
    return response;
  }

  // 매수일별 수익률(기간 환율을 못 받았거나 계산이 실패하면 빈 목록, 결과는 그대로)
  private List<PurchaseDayReturn> purchaseDayReturns(List<OHLCPriceDto> candles, SimulationContext context,
                                                     SimulationCalculationResult calculation, Map<String, BigDecimal> fxRates) {
    SimulationRequest request = context.request();
    if (candles.isEmpty() || (fxRates == null && request.getPurchaseFxRate() == null)) {
      return List.of();
    }
    try {
      return PurchaseDayReturns.of(candles, purchaseFx(context, calculation, fxRates),
        new PurchaseDayReturns.Inputs(request.getInvestmentAmount(), request.getSharesPerPurchase(),
          request.getTradingFeeRate(),
          Boolean.TRUE.equals(request.getReinvestDividends()), request.getDividendTaxRate(), context.dividendHistory(),
          exDatePrices(context), calculation.currentPriceUsd(), calculation.currentFxRate(), context.valuationDate()));
    } catch (RuntimeException e) {
      log.warn("매수일별 수익률 계산 실패: {}", e.getMessage());
      return List.of();
    }
  }

  // 매수일별 산 날 환율: 실제로 산 날은 결과의 매수 환율, 매수 환율을 넣었으면 넣은 환율, 아니면 산 날 시장 환율
  private Function<LocalDate, BigDecimal> purchaseFx(SimulationContext context,
                                                     SimulationCalculationResult calculation, Map<String, BigDecimal> fxRates) {
    LocalDate tradeDate = context.purchaseData().date();
    BigDecimal manual = context.request().getPurchaseFxRate();
    Function<LocalDate, BigDecimal> market = FxPath.marketFromBulk(fxRates, marketDataPort.defaultFxRate());
    return date -> date.equals(tradeDate) ? calculation.purchaseFxRate() : manual != null ? manual : market.apply(date);
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
        prices.computeIfAbsent(exDate, closeOn(context));
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
      return marketDataPort.getBulkFxRates(
        candles.stream().map(candle -> candle.date().toString()).toList());
    } catch (Exception e) {
      log.warn("기간 환율 조회 실패: {}", e.getMessage());
      return null;
    }
  }

  // 일별 원화 환율: 수동 환율이거나 기간 환율 기록이 없으면 매수 · 평가일 환율을 날짜 비례로, 아니면 시장 환율
  private static Function<LocalDate, BigDecimal> fxPath(SimulationContext context, Map<String, BigDecimal> fxRates) {
    SimulationRequest request = context.request();
    BigDecimal purchaseFxRate = context.purchaseFxRate().rate();
    if (request.getPurchaseFxRate() != null || request.getCurrentFxRate() != null
      || fxRates == null || fxRates.isEmpty()) {
      return FxPath.interpolated(context.purchaseData().date(), purchaseFxRate,
        context.valuationDate(), context.currentFxRate().rate());
    }
    return FxPath.marketFromBulk(fxRates, purchaseFxRate);
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

  // 최적 매매 시점(일봉이 없거나 기간 환율을 못 받았거나 계산이 실패하면 빈 값)
  private OptimalTiming calculateOptimalTiming(List<OHLCPriceDto> ordered, Map<String, BigDecimal> fxRates) {
    if (ordered.isEmpty() || fxRates == null) {
      return OptimalTiming.empty();
    }
    try {
      return OptimalTiming.best(ordered, FxPath.marketFromBulk(fxRates, marketDataPort.defaultFxRate()));
    } catch (Exception e) {
      log.warn("최적 타이밍 계산 실패: {}", e.getMessage());
      return OptimalTiming.empty();
    }
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
      return BacktestDataUtils.buildPriceMap(marketDataPort, symbol, purchaseDate.minusDays(5),
        valuationDate);
    } catch (RuntimeException e) {
      log.warn("기간 시세 조회 실패, 날짜별 조회로: symbol={}, error={}", symbol, e.getMessage());
      return Map.of();
    }
  }

  // 매수 시세, 휴장일이면 평가일까지 다음 거래일(7일 안) · 다음 거래일이 없으면 직전 거래일(기간 일봉에 없을 때만 날짜별 조회)
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
      marketDataPort::getOHLCPrice, symbol, purchaseDate, valuationDate, 7);
    return next != null ? next : getHistoricalPriceWithRetry(symbol, purchaseDate);
  }

  // 받은 날짜 종가(휴장일이면 직전 거래일, 기간 일봉에 없을 때만 날짜별 조회)
  private Function<LocalDate, BigDecimal> closeOn(SimulationContext context) {
    return date -> PriceLookup.effectiveClose(getHistoricalPrice(context.request().getSymbol(), date, context.priceMap()));
  }

  // 받은 날짜 시세(휴장일이면 직전 거래일), 기간 일봉에 없을 때만 날짜별 조회
  private OHLCPriceDto getHistoricalPrice(String symbol, LocalDate date, Map<LocalDate, OHLCPriceDto> priceMap) {
    OHLCPriceDto fromRange = PriceLookup.fromMap(priceMap, date, 5);
    return fromRange != null ? fromRange : getHistoricalPriceWithRetry(symbol, date);
  }

  // 주가 데이터 조회 with retry (시장 휴일 대응)
  private OHLCPriceDto getHistoricalPriceWithRetry(String symbol, LocalDate date) {
    log.info("주가 데이터 요청: symbol={}, date={}", symbol, date);
    return PriceLookup.withFallback(marketDataPort::getOHLCPrice, symbol, date, 5);
  }
}
