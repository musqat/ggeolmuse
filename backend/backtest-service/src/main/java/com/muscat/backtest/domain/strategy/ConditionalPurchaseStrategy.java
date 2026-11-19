package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.domain.calculation.TradeStatsCalculator;
import com.muscat.backtest.domain.engine.CapitalGainsTaxPayer;
import com.muscat.backtest.domain.engine.StrategyFinalizer;
import com.muscat.backtest.domain.engine.YearEndHarvest;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.SkipReason;
import com.muscat.backtest.domain.validation.BacktestRequestValidator;
import com.muscat.backtest.domain.validation.RoundLimit;
import com.muscat.backtest.common.enums.type.PriceDirection;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.domain.service.BacktestDataUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.StrategyRequest;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.SkippedRound;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.domain.port.MarketDataPort;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 조건부 매수 전략: 전 거래일 종가보다 장중에 정한 비율만큼 떨어진(또는 오른) 날마다 회당 금액이나 정한 주식 수만큼 삼
@Component
@RequiredArgsConstructor
@Slf4j
public class ConditionalPurchaseStrategy implements InvestmentStrategy {

  private final MarketDataPort marketData;
  private final ResponseMapper responseMapper;

  @Override
  public StrategyType getStrategyType() {
    return StrategyType.CONDITIONAL_PURCHASE;
  }

  @Override
  public StrategyResponse execute(StrategyRequest request) {
    // null 은 검증이 막음
    if (request != null && !(request instanceof ConditionalStrategyRequest)) {
      throw new BacktestException(BacktestResponse.STRATEGY_TYPE_MISMATCH);
    }
    return runConditional((ConditionalStrategyRequest) request);
  }

  private StrategyResponse runConditional(ConditionalStrategyRequest request) {
    validateConditionalRequest(request);

    BacktestLogger.setStrategyContext(request.getUserId(), "CONDITIONAL", request.getSymbol());

    try {
      log.info("조건부 매수 전략 실행 시작: {} - 전 거래일 종가 대비 {}% {} 시 회당 {} 매수 (예산 {}, 최대 {}회)",
        request.getSymbol(), request.getDropPercentage().movePointRight(2),
        PriceDirection.orDrop(request.getDirection()).label(), request.perPurchase(),
        request.getTotalInvestment(), request.getMaxPurchases());

      // 종료일이 미래인지 검증
      LocalDate today = LocalDate.now();
      LocalDate actualEndDate = request.getEndDate().isAfter(today) ? today : request.getEndDate();

      if (!actualEndDate.equals(request.getEndDate())) {
        log.info("종료일이 미래로 설정되어 오늘 날짜로 조정: {} -> {}", request.getEndDate(), actualEndDate);
      }

      CapitalGainsTaxPayer taxPayer = new CapitalGainsTaxPayer(Boolean.TRUE.equals(request.getCapitalGainsTax()));
      StrategyPurchases purchases = executeConditionalPurchases(request, actualEndDate, taxPayer);
      List<StrategyTransaction> transactions = purchases.transactions();
      List<SkippedRound> skippedRounds = purchases.skippedRounds();

      // 마무리(평가일 시세/환율/배당재투자/계산) — 공통 로직 위임
      StrategyFinalizer.Result finalized = StrategyFinalizer.run(
        marketData,
        StrategyFinalizer.Settings.of(request, actualEndDate, Boolean.TRUE.equals(request.getReinvestDividends()),
          !request.sells() && harvests(request)),
        transactions, purchases.priceMap(), purchases.dailyFx());

      log.info("조건부 매수 전략 실행 완료: {} - {}회 투자, 수익률 {}%",
        request.getSymbol(), finalized.transactions().size(),
        finalized.calculation().getTotalReturnPercent());

      StrategyResponse response = finalized.fill(responseMapper.toStrategyResponse(
        request, finalized.transactions(), finalized.calculation(), finalized.currentPrice(), skippedRounds));
      response.setSkippedSpan(purchases.skippedSpan());
      response.setCapitalGainsTax(Boolean.TRUE.equals(request.getCapitalGainsTax())
        ? finalized.capitalGainsTax(taxPayer.payments())
        : null);
      response.setTradeStats(TradeStatsCalculator.of(finalized.transactions(), request.getStartDate(), actualEndDate));
      return response;

    } finally {
      BacktestLogger.remove("operation");
    }
  }

  // 조건부 매수 전략 특화 요청 검증
  private void validateConditionalRequest(ConditionalStrategyRequest request) {
    BacktestRequestValidator.requireNonNull(request);
    BacktestRequestValidator.requireSymbol(request.getSymbol());
    BacktestRequestValidator.requireDateRange(request.getStartDate(), request.getEndDate());
    validateLimits(request);
    validateConditionalSpecificFields(request);
    RoundLimit.requireConditional(request, LocalDate.now());
  }

  // 회당 금액 · 주식 수 가운데 하나만, 예산 · 최대 횟수는 비우거나 0 보다 큼
  private void validateLimits(ConditionalStrategyRequest request) {
    BacktestRequestValidator.requirePerPurchase(request.getAmountPerPurchase(), request.getSharesPerPurchase());
    BacktestRequestValidator.requireBudget(request.getTotalInvestment());
    if (request.getMaxPurchases() != null && request.getMaxPurchases() <= 0) {
      throw new BacktestException(BacktestResponse.INVALID_MAX_PURCHASES);
    }
  }

  private void validateConditionalSpecificFields(ConditionalStrategyRequest request) {
    if (request.getDropPercentage() == null
      || !Decimals.isPositive(request.getDropPercentage())) {
      throw new BacktestException(BacktestResponse.STRATEGY_DROP_PERCENTAGE_REQUIRED);
    }
    BacktestRequestValidator.requireSellRates(request.getTakeProfitRate(), request.getStopLossRate());
    BacktestRequestValidator.requireTrailingStop(request.getTrailingStopRate(), request.getTrailingSellRate());
    BacktestRequestValidator.requirePartialTakeProfits(request.getPartialTakeProfits());
    // 재투자 주식은 루프 뒤에 더해져 매도와 순서가 맞지 않음
    if (request.sells() && Boolean.TRUE.equals(request.getReinvestDividends())) {
      throw new BacktestException(BacktestResponse.SELL_WITH_REINVEST);
    }
  }

  // 양도세와 연말 정리를 둘 다 켰는지
  private static boolean harvests(ConditionalStrategyRequest request) {
    return YearEndHarvest.enabled(request.getCapitalGainsTax(), request.getYearEndHarvest());
  }

  // 조건부 매수 실행(시세가 깨진 날 종가 0 이하 · 계산이 실패한 날은 사지 않고 이유를 남긴 뒤 다음 날 다시)
  private StrategyPurchases executeConditionalPurchases(
    ConditionalStrategyRequest request, LocalDate actualEndDate, CapitalGainsTaxPayer taxPayer) {
    java.util.Map<LocalDate, OHLCPriceDto> priceMap = loadPrices(request, actualEndDate);
    FxRates fxRateMap = loadFxRates(request, priceMap, actualEndDate);
    // 매도 규칙을 켠 요청의 연말 정리 날
    Set<LocalDate> harvestDates = request.sells() && harvests(request)
      ? YearEndHarvest.dates(priceMap, request.getStartDate(), actualEndDate)
      : Set.of();
    ConditionalRun run = new ConditionalRun(request, taxPayer, fxRateMap, harvestDates, request.sells());
    List<SkippedRound> skippedRounds = new ArrayList<>();
    BigDecimal previousClose = null;
    LocalDate currentDate = request.getStartDate();

    // 메모리에서 데이터 조회 (API 호출 없음), 파는 규칙을 켜면 새 돈 회차를 다 쓴 뒤에도 끝까지 봄
    while (!currentDate.isAfter(actualEndDate) && run.active()) {
      OHLCPriceDto priceData = priceMap.get(currentDate);
      boolean usable = PriceLookup.isUsable(priceData);
      try {
        if (priceData != null && priceData.available() && !usable) {
          skippedRounds.add(new SkippedRound(currentDate, SkipReason.NO_PRICE));
        } else if (usable) {
          run.day(currentDate, priceData, previousClose);
        }

      } catch (RuntimeException e) {
        skippedRounds.add(new SkippedRound(currentDate, SkipReason.CALCULATION_ERROR));
        log.warn("조건부 {} 매수 계산 실패, 사지 않음: {}", currentDate, e.getMessage());
      }

      if (usable) {
        previousClose = PriceLookup.effectiveClose(priceData);
      }
      currentDate = currentDate.plusDays(1);
    }

    log.info("조건부 매수 전략 완료: {}회 매수, 사지 못한 날 {}, 예산 부족 {}회",
      run.transactions().size(), skippedRounds.size(), run.shortRounds());
    return new StrategyPurchases(new ArrayList<>(run.transactions()), skippedRounds, priceMap, fxRateMap,
      run.span());
  }

  // 기간 일봉(쓸 수 있는 일봉이 없으면 DATA_NOT_FOUND)
  private java.util.Map<LocalDate, OHLCPriceDto> loadPrices(ConditionalStrategyRequest request,
                                                            LocalDate actualEndDate) {
    // 기간 일봉을 한 번에 조회
    log.info("조건부 매수 전략 - Bulk 데이터 조회 시작: {} ~ {}", request.getStartDate(), actualEndDate);
    java.util.Map<LocalDate, OHLCPriceDto> priceMap = BacktestDataUtils.buildPriceMap(
      marketData, request.getSymbol(), request.getStartDate(), actualEndDate);
    if (priceMap.values().stream().noneMatch(PriceLookup::isUsable)) {
      throw new BacktestException(BacktestResponse.DATA_NOT_FOUND);
    }
    return priceMap;
  }

  // 첫 시세일부터 평가일까지 날마다 환율(수동 환율이면 빈 값)
  private FxRates loadFxRates(ConditionalStrategyRequest request, java.util.Map<LocalDate, OHLCPriceDto> priceMap,
                              LocalDate actualEndDate) {
    if (request.getPurchaseFxRate() != null) {
      return FxRates.EMPTY;
    }
    LocalDate firstPrice = priceMap.keySet().stream().min(LocalDate::compareTo).orElse(actualEndDate);
    FxRates fxRateMap = BacktestDataUtils.buildDailyFxRateMap(marketData, firstPrice, actualEndDate);
    log.info("환율 데이터 조회 완료: {}개", fxRateMap.size());
    return fxRateMap;
  }
}
