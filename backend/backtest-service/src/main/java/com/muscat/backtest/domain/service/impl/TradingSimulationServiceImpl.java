package com.muscat.backtest.domain.service.impl;

import com.muscat.backtest.common.constants.BacktestConstants;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.BacktestHistoryUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.FxFallback;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.common.validation.BacktestRequestValidator;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    OHLCPriceDto purchaseData = getPurchasePrice(request.getSymbol(),
      request.getPurchaseDate(), valuationDate);
    LocalDate tradeDate = purchaseData.date();

    // 수동 환율이 설정되어 있으면 사용, 없으면 실제로 산 날 환율
    FxRateDto purchaseFxRate;
    if (request.getPurchaseFxRate() != null) {
      purchaseFxRate = new FxRateDto(tradeDate, request.getPurchaseFxRate());
    } else {
      purchaseFxRate = marketDataClientWrapper.getFxRate(tradeDate.toString());
    }

    // 평가일 시세. 오늘이면 현재가, 과거면 그날 조정 종가
    BigDecimal valuationPrice = valuedToday
      ? marketDataClientWrapper.getCurrentPrice(request.getSymbol()).currentPrice()
      : PriceLookup.effectiveClose(getHistoricalPriceWithRetry(request.getSymbol(), valuationDate));

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
      currentFxRate, dividendHistory, valuationDate);
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

      // 배당일 가격을 BULK로 선조회(N+1 제거): 배당 구간 전체 OHLC를 range 1콜로 받아 날짜 맵 구성
      // (배당마다 getOHLCPrice를 5일 fallback HTTP로 치던 것 대체. 휴장일 대응 위해 시작 -5일)
      Map<LocalDate, OHLCPriceDto> dividendPriceMap = java.util.Collections.emptyMap();
      if (!dividends.isEmpty()) {
        LocalDate firstExDate = dividends.get(0).getExDate();
        LocalDate lastExDate = dividends.get(dividends.size() - 1).getExDate();
        dividendPriceMap = marketDataClientWrapper.getOHLCPriceRange(
            context.request().getSymbol(),
            firstExDate.minusDays(5).toString(), lastExDate.toString())
          .stream()
          .filter(OHLCPriceDto::available)
          .collect(Collectors.toMap(OHLCPriceDto::date, p -> p, (a, b) -> b));
      }

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

        // 배당일 주가: BULK 맵에서 5일 역순 조회(휴장일 대응), 없으면 단건 HTTP fallback
        OHLCPriceDto priceAtDividendDate = PriceLookup.fromMap(
          dividendPriceMap, dividend.getExDate(), 5);
        if (priceAtDividendDate == null) {
          priceAtDividendDate = getHistoricalPriceWithRetry(
            context.request().getSymbol(), dividend.getExDate());
        }
        BigDecimal dividendDayPrice = PriceLookup.effectiveClose(priceAtDividendDate);

        // 세후 배당금으로 매수 가능한 주식수
        BigDecimal additionalShares = afterTaxDividend.divide(
          dividendDayPrice, 8, java.math.RoundingMode.HALF_UP);

        shares = shares.add(additionalShares);
        dividendsReinvested = dividendsReinvested.add(afterTaxDividend);
        dividendReinvestDates.add(dividend.getExDate());

        log.info("배당 재투자: {} - ${} ({}주 보유) -> {}주 추가 매수 @${}",
          dividend.getExDate(), afterTaxDividend, shares, additionalShares, dividendDayPrice);
      }

      log.info("배당금 재투자 완료: 총 ${} 재투자, 최종 보유 {}주", dividendsReinvested, shares);
    }

    // 재투자 후 현재 가치 재계산
    currentValueUsd = shares.multiply(currentPriceUsd);
    currentValueKrw = MoneyUtils.convertUsdToKrw(currentValueUsd, currentFxRate);

    // 받은 배당 총액(세전). 재투자했으면 위에서 더한 값, 아니면 처음 주식 수로 구한다
    boolean reinvested = Boolean.TRUE.equals(context.request().getReinvestDividends());
    BigDecimal totalDividends = reinvested
      ? grossDividends
      : BacktestCalculationUtils.calculateTotalDividends(
        context.dividendHistory(), shares, context.purchaseData().date(), context.valuationDate());

    BigDecimal dividendYield = BacktestCalculationUtils.calculateDividendYield(
      totalDividends, shares, currentPriceUsd);

    // 재투자를 끄면 세후 배당을 현금으로 받는다. 켜면 이미 주식 수에 들어 있다
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
      dividendsReinvested, dividendReinvestDates);
  }

  // 최적타이밍 계산 + 응답 DTO 매핑
  private SimulationResponse buildSimulationResponse(SimulationContext context,
    SimulationCalculationResult calculation) {

    // 최적 타이밍 계산 (매수일 ~ 평가일)
    OptimalTiming optimalTiming = calculateOptimalTiming(
      context.request().getSymbol(),
      context.purchaseData().date(),
      context.valuationDate());

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
    return response;
  }

  // 최적 타이밍 계산 (원화 주가 기준, 매수일 <= 매도일 제약 아래 최대 수익)
  private OptimalTiming calculateOptimalTiming(String symbol, LocalDate startDate,
    LocalDate endDate) {
    try {
      List<OHLCPriceDto> prices = marketDataClientWrapper.getOHLCPriceRange(
        symbol, startDate.toString(), endDate.toString());

      if (prices == null || prices.isEmpty()) {
        return OptimalTiming.empty();
      }

      // 전 구간 최저가 · 최고가를 따로 뽑으면 매도일이 매수일보다 앞설 수 있음
      // 날짜순으로 훑으며 그때까지 원화 주가가 가장 낮은 날에 산 수익률만 봄
      List<OHLCPriceDto> ordered = prices.stream()
        .sorted(Comparator.comparing(OHLCPriceDto::date))
        .toList();

      // 환율이 빠진 날은 기본 환율이라, 하나도 못 받으면 달러 주가 기준과 같음
      Map<String, BigDecimal> fxRates = marketDataClientWrapper.getBulkFxRates(
        ordered.stream().map(candle -> candle.date().toString()).toList());

      OHLCPriceDto minSoFar = ordered.get(0);
      OHLCPriceDto bestBuy = ordered.get(0);
      OHLCPriceDto bestSell = ordered.get(0);
      BigDecimal bestReturn = BigDecimal.ZERO;

      for (OHLCPriceDto candle : ordered) {
        if (krwPrice(candle, fxRates).compareTo(krwPrice(minSoFar, fxRates)) < 0) {
          minSoFar = candle;
        }

        BigDecimal ret = MoneyUtils.calculateReturnRate(
          krwPrice(minSoFar, fxRates), krwPrice(candle, fxRates));

        if (ret.compareTo(bestReturn) > 0) {
          bestReturn = ret;
          bestBuy = minSoFar;
          bestSell = candle;
        }
      }

      // 내내 하락하기만 하면 갱신이 없어 매수일 = 매도일 = 시작일, 수익률 0 이 된다.
      // 그 구간에서는 사지 않는 것이 최선이라는 뜻이다.
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

  // 그날 종가 × 그날 환율, 환율이 없으면 기본 환율
  private static BigDecimal krwPrice(OHLCPriceDto candle, Map<String, BigDecimal> fxRates) {
    return PriceLookup.effectiveClose(candle)
      .multiply(fxRates.getOrDefault(candle.date().toString(), FxFallback.DEFAULT_RATE));
  }

  // 백테스트 이력 저장 (userId 없으면 skip)
  private void recordSimulationHistory(SimulationRequest request) {
    if (request.getUserId() == null) {
      return;
    }
    backtestHistoryUtils.saveBacktestHistory(request.getUserId(), BacktestType.COMPARISON, request);
  }


  // 매수 시세, 휴장일이면 평가일까지 다음 거래일(7일 안) · 그런 날이 없으면 그 전 거래일
  private OHLCPriceDto getPurchasePrice(String symbol, LocalDate purchaseDate, LocalDate valuationDate) {
    OHLCPriceDto next = PriceLookup.nextAvailable(
      marketDataClientWrapper::getOHLCPrice, symbol, purchaseDate, valuationDate, 7);
    return next != null ? next : getHistoricalPriceWithRetry(symbol, purchaseDate);
  }

  // 주가 데이터 조회 with retry (시장 휴일 대응)
  private OHLCPriceDto getHistoricalPriceWithRetry(String symbol, LocalDate date) {
    log.info("주가 데이터 요청: symbol={}, date={}", symbol, date);
    return PriceLookup.withFallback(marketDataClientWrapper::getOHLCPrice, symbol, date, 5);
  }
}
