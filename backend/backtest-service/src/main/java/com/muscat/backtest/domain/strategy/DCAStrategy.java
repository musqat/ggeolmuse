package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.constants.BacktestConstants;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.SkipReason;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.common.engine.StrategyFinalizer;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.common.util.Decimals;
import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.backtest.common.validation.BacktestRequestValidator;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.StrategyRequest;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.SkippedRound;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// DCA 적립식 투자 전략: 매월 지정일에 고정 금액 매수. 배당 재투자 · 원천징수 · 환율 변동은 옵션
@Component
@RequiredArgsConstructor
@Slf4j
public class DCAStrategy implements InvestmentStrategy {

  private final MarketDataClient marketDataClient;
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
      log.info("DCA 전략 실행 시작: {} - 월{}원씩", request.getSymbol(), request.getMonthlyAmount());

      LocalDate today = LocalDate.now();
      LocalDate actualEndDate = request.getEndDate().isAfter(today) ? today : request.getEndDate();

      if (!actualEndDate.equals(request.getEndDate())) {
        log.info("종료일이 미래로 설정되어 오늘 날짜로 조정: {} -> {}", request.getEndDate(), actualEndDate);
      }

      int investmentDay = request.getPurchaseDay() != null ? request.getPurchaseDay() : 1;
      log.info("적립식 투자 전략 실행: 월{}원씩 {}일에 매수", request.getMonthlyAmount(), investmentDay);

      StrategyPurchases purchases = executeRegularInvestments(request, actualEndDate, investmentDay);
      List<StrategyTransaction> transactions = purchases.transactions();

      if (transactions.isEmpty()) {
        throw new BacktestException(BacktestResponse.DATA_NOT_FOUND,
          "적립식 투자 전략 실행 가능한 데이터가 없습니다");
      }

      List<SkippedRound> skippedRounds = purchases.skippedRounds();

      // 마무리(평가일 시세/환율/배당재투자/계산) — 공통 로직 위임
      StrategyFinalizer.Result finalized = StrategyFinalizer.run(
        marketDataClient, request.getSymbol(), actualEndDate, request.getCurrentFxRate(),
        request.getPurchaseFxRate(), Boolean.TRUE.equals(request.getReinvestDividends()),
        request.getDividendTaxRate(), transactions);

      log.info("DCA 전략 실행 완료: {} - {}회 투자, 수익률 {}%",
        request.getSymbol(), finalized.transactions().size(),
        finalized.calculation().getTotalReturnPercent());

      return responseMapper.toStrategyResponse(
        request, finalized.transactions(), finalized.calculation(), finalized.currentPrice(), skippedRounds);

    } finally {
      BacktestLogger.remove("operation");
    }
  }

  // DCA 전략 특화 요청 검증
  private void validateDcaRequest(DcaStrategyRequest request) {
    BacktestRequestValidator.requireNonNull(request);
    BacktestRequestValidator.requireSymbol(request.getSymbol());
    BacktestRequestValidator.requireDateRange(request.getStartDate(), request.getEndDate());
    if (request.getMonthlyAmount() == null
      || !Decimals.isPositive(request.getMonthlyAmount())) {
      throw new BacktestException(BacktestResponse.STRATEGY_MONTHLY_AMOUNT_REQUIRED);
    }
    if (request.getPurchaseDay() == null || request.getPurchaseDay() < 1
      || request.getPurchaseDay() > 31) {
      throw new BacktestException(BacktestResponse.INVALID_PURCHASE_DAY);
    }
  }

  // 정기 적립식 투자 실행. 투자일에 쓸 시세가 없거나 계산이 실패한 회차는 사지 않고 이유와 함께 남김
  private StrategyPurchases executeRegularInvestments(
    DcaStrategyRequest request, LocalDate actualEndDate, int investmentDay) {

    List<StrategyTransaction> transactions = new ArrayList<>();

    // 투자 주기 설정 (기본: 1개월)
    int interval = request.getInvestmentInterval() != null ? request.getInvestmentInterval() : 1;

    // 총 투자금액 추적
    BigDecimal totalInvestedSoFar = BigDecimal.ZERO;
    BigDecimal investmentLimit = request.getTotalInvestmentLimit();

    //  BULK API 사용: 전체 기간의 가격 데이터를 한 번에 조회
    log.info("DCA 전략 - Bulk 데이터 조회 시작: {} ~ {}", request.getStartDate(), actualEndDate);
    Map<LocalDate, OHLCPriceDto> priceMap = BacktestDataUtils.buildPriceMap(
      marketDataClient, request.getSymbol(), request.getStartDate(), actualEndDate);

    // 투자일 목록
    List<LocalDate> investmentDates = investmentDates(
      request.getStartDate(), actualEndDate, investmentDay, interval);

    // 투자일마다 실제로 사는 날 시세, 휴장일이면 종료일까지 다음 거래일 · 그런 날이 없으면 그 전 거래일
    Map<LocalDate, OHLCPriceDto> tradeByDate = new HashMap<>();
    for (LocalDate date : investmentDates) {
      OHLCPriceDto trade = PriceLookup.nextFromMap(priceMap, date, actualEndDate, 7);
      if (trade == null) {
        trade = PriceLookup.fromMap(priceMap, date, 5);
      }
      if (trade != null) {
        tradeByDate.put(date, trade);
      }
    }

    //  BULK API 사용: 실제로 사는 날의 환율 데이터를 한 번에 조회
    Map<LocalDate, BigDecimal> fxRateMap = new HashMap<>();
    if (request.getPurchaseFxRate() == null) {
      fxRateMap = BacktestDataUtils.getBulkFxRates(marketDataClient,
        tradeByDate.values().stream().map(OHLCPriceDto::date).distinct().sorted().toList());
      log.info("환율 데이터 조회 완료: {}개", fxRateMap.size());
    }

    List<SkippedRound> skippedRounds = new ArrayList<>();

    //  메모리에서 데이터 조회 (API 호출 없음)
    for (LocalDate currentDate : investmentDates) {
      // 총 투자금 한도 체크
      if (investmentLimit != null && totalInvestedSoFar.compareTo(investmentLimit) >= 0) {
        log.info("총 투자금 한도({})에 도달하여 투자 종료", investmentLimit);
        break;
      }

      OHLCPriceDto priceData = tradeByDate.get(currentDate);
      if (!PriceLookup.isUsable(priceData)) {
        skippedRounds.add(new SkippedRound(currentDate, SkipReason.NO_PRICE));
        continue;
      }

      // 환율 조회 (메모리에서, 실제로 사는 날)
      BigDecimal purchaseFxRate = request.getPurchaseFxRate() != null
        ? request.getPurchaseFxRate() : fxRateMap.get(priceData.date());

      try {
        // 매수 실행. 수수료를 떼고 남은 달러로 산다
        BigDecimal fee = BacktestCalculationUtils.calculatePurchaseFee(
          request.getMonthlyAmount(), purchaseFxRate, request.getTradingFeeRate());
        BigDecimal shares = BacktestCalculationUtils.calculateSharesAfterFee(
          request.getMonthlyAmount(), purchaseFxRate, PriceLookup.effectiveClose(priceData), fee);

        // 거래 기록 생성
        StrategyTransaction transaction = StrategyTransaction.builder()
          .date(currentDate)              // 계획된 매수일
          .actualDate(priceData.date())   // 실제 거래일
          .price(PriceLookup.effectiveClose(priceData))
          .shares(shares)
          .fee(fee)
          .amount(request.getMonthlyAmount())
          .fxRate(purchaseFxRate)
          .trigger(BacktestConstants.TransactionTrigger.MONTHLY_INVESTMENT)
          .build();

        transactions.add(transaction);
        totalInvestedSoFar = totalInvestedSoFar.add(request.getMonthlyAmount());

        log.debug("DCA 투자 실행: {} - {}원 투자, {}주 매수 (총 {}원)",
          currentDate, request.getMonthlyAmount(), shares, totalInvestedSoFar);
      } catch (RuntimeException e) {
        skippedRounds.add(new SkippedRound(currentDate, SkipReason.CALCULATION_ERROR));
        log.warn("적립식 {} 회차 계산 실패, 사지 않음: {}", currentDate, e.getMessage());
      }
    }

    if (!skippedRounds.isEmpty()) {
      log.info("적립식 못 산 회차 {}", skippedRounds.size());
    }
    return new StrategyPurchases(transactions, skippedRounds);
  }

  // 시작일부터 종료일까지 주기마다의 투자일. 그달에 투자일이 없으면 말일
  private static List<LocalDate> investmentDates(LocalDate startDate, LocalDate endDate,
    int investmentDay, int interval) {
    List<LocalDate> dates = new ArrayList<>();
    LocalDate date = startDate.withDayOfMonth(Math.min(investmentDay, startDate.lengthOfMonth()));
    while (!date.isAfter(endDate)) {
      dates.add(date);
      LocalDate next = date.plusMonths(interval);
      date = next.withDayOfMonth(Math.min(investmentDay, next.lengthOfMonth()));
    }
    return dates;
  }

}
