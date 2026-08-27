package com.muscat.backtest.domain.service.impl;

import com.muscat.backtest.common.constants.BacktestConstants;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.BacktestHistoryUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.PriceLookup;
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
import com.muscat.commonlib.dto.StockPriceDto;
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

/**
 * 단일 시점 매수 시뮬레이션
 * "특정 과거일 1회 매수 후 현재까지 보유" 손익 계산
 * 매수가 조정종가 기준, 배당 옵션 시 재투자(보유주 × 주당배당 → 세후 → 그날 조정종가 추가매수)
 * 환차익·수수료·잔여현금 합산 → 총자산/수익률, 기간 내 최적 매수/매도 타이밍 분석
 * 다종목 holdings 백테스트는 {@link InvestmentBacktestServiceImpl}로 분리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradingSimulationServiceImpl implements TradingSimulationService {

  private final MarketDataClientWrapper marketDataClientWrapper;
  private final ResponseMapper responseMapper;
  private final BacktestHistoryUtils backtestHistoryUtils;

  /**
   * 단일 매수 시뮬레이션 실행 (이력 기록 포함)
   * @param request 종목·매수일·투자금·환율/배당 옵션
   * @return 평가금액·수익률·배당·최적타이밍
   */
  @Override
  public SimulationResponse runSimulation(SimulationRequest request) {
    return runSimulation(request, true);
  }

  /**
   * 단일 매수 시뮬레이션 실행 (recordHistory=false면 이력 미기록)
   * 비교/분석(BacktestAnalysisService)이 다수 시뮬을 돌릴 때 false로 호출
   * @param request 종목·매수일·투자금·환율/배당 옵션
   * @param recordHistory 백테스트 이력 저장 여부
   * @return 평가금액·수익률·배당·최적타이밍
   */
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

  // 시뮬레이션 입력 컨텍스트 구성 (매수가·환율·현재가·배당이력 조회)
  private SimulationContext prepareSimulationContext(SimulationRequest request) {
    // 주가 데이터 조회 - Wrapper 직접 호출로 resilience 패턴 적용
    OHLCPriceDto purchaseData = getHistoricalPriceWithRetry(request.getSymbol(),
      request.getPurchaseDate());

    // 수동 환율이 설정되어 있으면 사용, 없으면 자동 조회
    FxRateDto purchaseFxRate;
    if (request.getPurchaseFxRate() != null) {
      purchaseFxRate = new FxRateDto(request.getPurchaseDate(),
        request.getPurchaseFxRate());
    } else {
      purchaseFxRate = marketDataClientWrapper.getFxRate(request.getPurchaseDate().toString());
    }

    StockPriceDto currentPrice = marketDataClientWrapper.getCurrentPrice(request.getSymbol());

    FxRateDto currentFxRate;
    if (request.getCurrentFxRate() != null) {
      currentFxRate = new FxRateDto(LocalDate.now(), request.getCurrentFxRate());
    } else {
      currentFxRate = marketDataClientWrapper.getLatestFxRate();
    }

    // 배당 이력 조회
    List<DividendDto> dividendList = marketDataClientWrapper.getDividendHistory(
      request.getSymbol(), request.getPurchaseDate().toString(), LocalDate.now().toString());
    DividendHistoryDto dividendHistory = DividendHistoryDto.of(request.getSymbol(), dividendList);

    return new SimulationContext(request, purchaseData, purchaseFxRate, currentPrice, currentFxRate,
      dividendHistory);
  }

  // 손익 계산 (매수가·주식수·수수료·배당재투자·환차익 → 총자산/수익률)
  private SimulationCalculationResult calculateSimulation(SimulationContext context) {
    // adjustedClose 사용 (액면분할/배당 반영), 없으면 closePrice fallback
    BigDecimal purchasePriceUsd = PriceLookup.effectiveClose(context.purchaseData());
    BigDecimal purchaseFxRate = context.purchaseFxRate().rate();
    BigDecimal currentFxRate = context.currentFxRate().rate();
    BigDecimal currentPriceUsd = context.currentPrice().currentPrice();

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
    List<LocalDate> dividendReinvestDates = new ArrayList<>(); // 배당 재투자 날짜 목록

    // 배당금 재투자 처리 - 각 배당일에 순차적으로 재투자
    if (Boolean.TRUE.equals(context.request().getReinvestDividends())
      && context.dividendHistory() != null
      && context.dividendHistory().getDividends() != null
      && !context.dividendHistory().getDividends().isEmpty()) {

      log.info("배당금 재투자 실행 시작: {} 개의 배당 내역", context.dividendHistory().getDividends().size());

      LocalDate purchaseDate = context.request().getPurchaseDate();

      // 배당 내역을 ex-date 순으로 정렬해 순차 재투자
      var dividends = context.dividendHistory().getDividends().stream()
        .filter(d -> d.getExDate() != null)
        .filter(d -> !d.getExDate().isBefore(purchaseDate) && !d.getExDate().isAfter(LocalDate.now()))
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

    // 배당금 계산 (표시용 - 재투자 여부와 관계없이 총 배당금 계산)
    BigDecimal totalDividends = BacktestCalculationUtils.calculateTotalDividends(
      context.dividendHistory(), shares, context.request().getPurchaseDate(), LocalDate.now());

    BigDecimal dividendYield = BacktestCalculationUtils.calculateDividendYield(
      totalDividends, shares, currentPriceUsd);

    BigDecimal totalDividendsKrw = Decimals.isPositive(totalDividends)
      ? MoneyUtils.convertUsdToKrw(totalDividends, currentFxRate)
      : BigDecimal.ZERO;

    // 남은 현금도 자산에 포함 (USD → KRW 환산)
    BigDecimal remainingCashKrw = Decimals.isPositive(remainingCash)
      ? MoneyUtils.convertUsdToKrw(remainingCash, currentFxRate)
      : BigDecimal.ZERO;

    // 재투자된 배당금이 있으면, 이미 주식 가치에 포함되어 있으므로 배당금을 중복 더하지 않음
    boolean hasReinvested = Decimals.isPositive(dividendsReinvested);
    BigDecimal dividendsToAdd = hasReinvested ? BigDecimal.ZERO : totalDividendsKrw;

    // 총 자산 = 주식 가치 + 남은 현금 + 배당금 (재투자 시 배당금 제외)
    BigDecimal totalAssetKrw = currentValueKrw.add(remainingCashKrw).add(dividendsToAdd);

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

    // 최적 타이밍 계산 (매수일 ~ 현재)
    OptimalTiming optimalTiming = calculateOptimalTiming(
      context.request().getSymbol(),
      context.request().getPurchaseDate(),
      LocalDate.now());

    return responseMapper.toSimulationResponse(
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
  }

  // 최적 타이밍 계산 (매수일 <= 매도일 제약 아래 최대 수익)
  private OptimalTiming calculateOptimalTiming(String symbol, LocalDate startDate,
    LocalDate endDate) {
    try {
      List<OHLCPriceDto> prices = marketDataClientWrapper.getOHLCPriceRange(
        symbol, startDate.toString(), endDate.toString());

      if (prices == null || prices.isEmpty()) {
        return OptimalTiming.empty();
      }

      // 전 구간 최저가와 최고가를 각각 뽑으면 최고가가 최저가보다 먼저 오는 날이 생긴다.
      // 8월에 사서 7월에 파는 답이 나오는데, 그건 할 수 없는 매매다.
      // 매수일 <= 매도일 제약을 지키려면 앞에서부터 훑으며 그때까지의 최저가를 들고 가야 한다.
      //
      // 날짜 오름차순 전제라 여기서 한 번 더 정렬한다.
      List<OHLCPriceDto> ordered = prices.stream()
        .sorted(Comparator.comparing(OHLCPriceDto::date))
        .toList();

      OHLCPriceDto minSoFar = ordered.get(0);
      OHLCPriceDto bestBuy = ordered.get(0);
      OHLCPriceDto bestSell = ordered.get(0);
      BigDecimal bestReturn = BigDecimal.ZERO;

      for (OHLCPriceDto candle : ordered) {
        // 분할·배당 반영을 위해 effectiveClose 기준
        if (PriceLookup.effectiveClose(candle)
          .compareTo(PriceLookup.effectiveClose(minSoFar)) < 0) {
          minSoFar = candle;
        }

        BigDecimal ret = MoneyUtils.calculateReturnRate(
          PriceLookup.effectiveClose(minSoFar), PriceLookup.effectiveClose(candle));

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

  // 백테스트 이력 저장 (userId 없으면 skip)
  private void recordSimulationHistory(SimulationRequest request) {
    if (request.getUserId() == null) {
      return;
    }
    backtestHistoryUtils.saveBacktestHistory(request.getUserId(), BacktestType.COMPARISON, request);
  }


  // 주가 데이터 조회 with retry (시장 휴일 대응)
  private OHLCPriceDto getHistoricalPriceWithRetry(String symbol, LocalDate date) {
    log.info("주가 데이터 요청: symbol={}, date={}", symbol, date);
    return PriceLookup.withFallback(marketDataClientWrapper::getOHLCPrice, symbol, date, 5);
  }
}
