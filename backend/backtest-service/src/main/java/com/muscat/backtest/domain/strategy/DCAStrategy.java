package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.DcaFrequency;
import com.muscat.backtest.common.enums.type.SkipReason;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.common.engine.StrategyFinalizer;
import com.muscat.backtest.common.engine.YearEndHarvest;
import com.muscat.backtest.common.util.RoundPurchase;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.FxRates;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.common.validation.BacktestRequestValidator;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.StrategyRequest;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.SkippedRound;
import com.muscat.backtest.domain.model.SkippedSpan;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// DCA 적립식 투자 전략(매일 · 매주 · 매달 회당 고정 금액이나 고정 주식 수 매수, 배당 재투자 · 원천징수 · 환율 변동은 옵션)
// 예산을 넘는 회차부터는 사지 않고 예산 부족 구간으로 셈
@Component
@RequiredArgsConstructor
@Slf4j
public class DCAStrategy implements InvestmentStrategy {

  private final MarketDataClientWrapper marketData;
  private final ResponseMapper responseMapper;

  @Override
  public StrategyType getStrategyType() {
    return StrategyType.DCA;
  }

  @Override
  public StrategyResponse execute(StrategyRequest request) {
    // null 은 검증이 막음
    if (request != null && !(request instanceof DcaStrategyRequest)) {
      throw new BacktestException(BacktestResponse.STRATEGY_TYPE_MISMATCH,
        "적립식 전략에 다른 요청이 왔습니다: " + request.getStrategyType());
    }
    return runDca((DcaStrategyRequest) request);
  }

  private StrategyResponse runDca(DcaStrategyRequest request) {
    validateDcaRequest(request);

    BacktestLogger.setStrategyContext(request.getUserId(), "DCA", request.getSymbol());

    try {
      DcaFrequency frequency = request.getFrequency() != null ? request.getFrequency() : DcaFrequency.MONTHLY;
      log.info("DCA 전략 실행 시작: {} - {} 회당 {}", request.getSymbol(), frequency, request.perPurchase());

      LocalDate today = LocalDate.now();
      LocalDate actualEndDate = request.getEndDate().isAfter(today) ? today : request.getEndDate();

      if (!actualEndDate.equals(request.getEndDate())) {
        log.info("종료일이 미래로 설정되어 오늘 날짜로 조정: {} -> {}", request.getEndDate(), actualEndDate);
      }

      StrategyPurchases purchases = executeRegularInvestments(request, frequency, actualEndDate);
      List<StrategyTransaction> transactions = purchases.transactions();
      List<SkippedRound> skippedRounds = purchases.skippedRounds();

      // 마무리(평가일 시세/환율/배당재투자/계산) — 공통 로직 위임
      StrategyFinalizer.Result finalized = StrategyFinalizer.run(
        marketData, request.getSymbol(), request.getStartDate(), actualEndDate, request.getCurrentFxRate(),
        request.getPurchaseFxRate(), Boolean.TRUE.equals(request.getReinvestDividends()),
        request.getDividendTaxRate(), transactions, purchases.priceMap(), purchases.dailyFx(), request.getPeriodEnd(),
        request.getTradingFeeRate(), YearEndHarvest.enabled(request.getCapitalGainsTax(), request.getYearEndHarvest()));

      log.info("DCA 전략 실행 완료: {} - {}회 투자, 수익률 {}%",
        request.getSymbol(), finalized.transactions().size(),
        finalized.calculation().getTotalReturnPercent());

      StrategyResponse response = finalized.fill(responseMapper.toStrategyResponse(
        request, finalized.transactions(), finalized.calculation(), finalized.currentPrice(), skippedRounds));
      response.setSkippedSpan(purchases.skippedSpan());
      response.setCapitalGainsTax(Boolean.TRUE.equals(request.getCapitalGainsTax()) ? finalized.capitalGainsTax() : null);
      return response;

    } finally {
      BacktestLogger.remove("operation");
    }
  }

  // DCA 전략 특화 요청 검증
  private void validateDcaRequest(DcaStrategyRequest request) {
    BacktestRequestValidator.requireNonNull(request);
    BacktestRequestValidator.requireSymbol(request.getSymbol());
    BacktestRequestValidator.requireDateRange(request.getStartDate(), request.getEndDate());
    if ((request.getAmountPerPurchase() == null) == (request.getSharesPerPurchase() == null)) {
      throw new BacktestException(BacktestResponse.AMOUNT_OR_SHARES_REQUIRED);
    }
    if (request.getAmountPerPurchase() != null && !Decimals.isPositive(request.getAmountPerPurchase())) {
      throw new BacktestException(BacktestResponse.STRATEGY_AMOUNT_PER_PURCHASE_REQUIRED);
    }
    BacktestRequestValidator.requireShares(request.getSharesPerPurchase());
    Integer day = request.getPurchaseDay();
    if (day != null && (day < 1 || day > 31)) {
      throw new BacktestException(BacktestResponse.INVALID_PURCHASE_DAY);
    }
    Integer interval = request.getInvestmentInterval();
    if (interval != null && (interval < 1 || interval > 12)) {
      throw new BacktestException(BacktestResponse.INVALID_INVESTMENT_INTERVAL);
    }
    BacktestRequestValidator.requireFirstPurchaseInPeriod(request.getFirstPurchaseDate(), request.getStartDate(),
      request.getEndDate());
    if (request.getTotalInvestmentLimit() != null && !Decimals.isPositive(request.getTotalInvestmentLimit())) {
      throw new BacktestException(BacktestResponse.INVALID_TOTAL_INVESTMENT);
    }
  }

  // 정기 적립식 투자 실행(쓸 시세가 없거나 계산이 실패한 회차는 사지 않고 이유를 남김)
  private StrategyPurchases executeRegularInvestments(
    DcaStrategyRequest request, DcaFrequency frequency, LocalDate actualEndDate) {

    List<StrategyTransaction> transactions = new ArrayList<>();
    Integer sharesPerPurchase = request.getSharesPerPurchase();

    // 넣은 돈 · 예산 부족으로 사지 못한 회차 수와 그 첫 계획일
    BigDecimal totalInvestedSoFar = BigDecimal.ZERO;
    BigDecimal investmentLimit = request.getTotalInvestmentLimit();
    int shortRounds = 0;
    LocalDate shortFrom = null;

    //  BULK API 사용: 전체 기간의 가격 데이터를 한 번에 조회
    log.info("DCA 전략 - Bulk 데이터 조회 시작: {} ~ {}", request.getStartDate(), actualEndDate);
    Map<LocalDate, OHLCPriceDto> priceMap = BacktestDataUtils.buildPriceMap(
      marketData, request.getSymbol(), request.getStartDate(), actualEndDate);
    if (priceMap.values().stream().noneMatch(PriceLookup::isUsable)) {
      throw new BacktestException(BacktestResponse.DATA_NOT_FOUND,
        "적립식 투자 전략 실행 가능한 데이터가 없습니다");
    }

    LocalDate first = request.getFirstPurchaseDate() != null ? request.getFirstPurchaseDate() : request.getStartDate();
    int purchaseDay = request.getPurchaseDay() != null ? request.getPurchaseDay() : first.getDayOfMonth();
    int interval = request.getInvestmentInterval() != null ? request.getInvestmentInterval() : 1;
    List<DcaSchedule.Round> rounds = DcaSchedule.rounds(priceMap, frequency, first, actualEndDate, purchaseDay, interval);
    log.info("적립식 회차 {}개: {} · 첫 매수일 {} · 투자일 {} · {}개월마다", rounds.size(), frequency, first, purchaseDay,
      interval);

    //  BULK API 사용: 기간의 날마다 환율을 한 번에 조회
    FxRates fxRateMap = FxRates.EMPTY;
    if (request.getPurchaseFxRate() == null) {
      // 첫 매수일부터 평가일까지
      LocalDate firstTrade = rounds.stream().map(DcaSchedule.Round::trade).filter(Objects::nonNull)
        .map(OHLCPriceDto::date).min(LocalDate::compareTo).orElse(actualEndDate);
      fxRateMap = BacktestDataUtils.buildDailyFxRateMap(marketData, firstTrade, actualEndDate);
      log.info("환율 데이터 조회 완료: {}개", fxRateMap.size());
    }

    List<SkippedRound> skippedRounds = new ArrayList<>();

    //  메모리에서 데이터 조회 (API 호출 없음)
    for (DcaSchedule.Round round : rounds) {
      LocalDate currentDate = round.planned();
      OHLCPriceDto priceData = round.trade();
      if (!PriceLookup.isUsable(priceData)) {
        skippedRounds.add(new SkippedRound(currentDate, SkipReason.NO_PRICE));
        continue;
      }

      // 처음 예산을 넘은 회차부터 끝까지 예산 부족
      if (shortFrom != null) {
        shortRounds++;
        continue;
      }

      // 환율 조회 (메모리에서, 실제로 사는 날)
      BigDecimal purchaseFxRate = request.getPurchaseFxRate() != null
        ? request.getPurchaseFxRate() : fxRateMap.get(priceData.date());

      try {
        // 회당 금액이면 수수료를 차감하고 남은 달러로, 주식 수면 종가 × 주식 수 + 수수료를 원화로
        BigDecimal price = PriceLookup.effectiveClose(priceData);
        RoundPurchase purchase = sharesPerPurchase != null
          ? RoundPurchase.byShares(sharesPerPurchase, price, purchaseFxRate, request.getTradingFeeRate())
          : RoundPurchase.byAmount(request.getAmountPerPurchase(), price, purchaseFxRate, request.getTradingFeeRate());
        if (investmentLimit != null && totalInvestedSoFar.add(purchase.amountKrw()).compareTo(investmentLimit) > 0) {
          shortRounds++;
          shortFrom = currentDate;
          continue;
        }

        // 거래 기록 생성
        StrategyTransaction transaction = StrategyTransaction.builder()
          .date(currentDate)              // 계획된 매수일
          .actualDate(priceData.date())   // 실제 거래일
          .price(price)
          .shares(purchase.shares())
          .fee(purchase.feeUsd())
          .amount(purchase.amountKrw())
          .fxRate(purchaseFxRate)
          .defaultFxDate(fxRateMap.isDefault(priceData.date()) ? priceData.date() : null)
          .trigger(sharesPerPurchase != null ? frequency.trigger(sharesPerPurchase, interval) : frequency.trigger())
          .build();

        transactions.add(transaction);
        totalInvestedSoFar = totalInvestedSoFar.add(purchase.amountKrw());

        log.debug("DCA 투자 실행: {} - {}원 투자, {}주 매수 (총 {}원)",
          currentDate, purchase.amountKrw(), purchase.shares(), totalInvestedSoFar);
      } catch (RuntimeException e) {
        skippedRounds.add(new SkippedRound(currentDate, SkipReason.CALCULATION_ERROR));
        log.warn("적립식 {} 회차 계산 실패, 사지 않음: {}", currentDate, e.getMessage());
      }
    }

    log.info("적립식 못 산 회차 {}, 예산 부족 {}회", skippedRounds.size(), shortRounds);
    SkippedSpan shortfall = shortFrom == null ? null : new SkippedSpan(shortFrom, shortRounds, SkipReason.OVER_BUDGET);
    return new StrategyPurchases(transactions, skippedRounds, priceMap, fxRateMap, shortfall);
  }

}
