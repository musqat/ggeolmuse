package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.constants.BacktestConstants;
import com.muscat.backtest.common.enums.BacktestResponse;
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
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
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

/**
 * DCA(Dollar Cost Averaging) 적립식 투자 전략
 * 매월 지정일 고정금액 자동 매수
 * 배당재투자·원천징수·환율변동 옵션
 */
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

  /**
   * DCA 전략 실행 → 백테스트 결과
   * 시작일~종료일 매월 지정일 고정금액 매수, 배당재투자·환율·원천징수 반영
   * @param request 종목·기간·월투자액·매수일
   * @return 총투자금·수익률·거래내역·배당
   */
  @Override
  public StrategyResponse executeDca(DcaStrategyRequest request) {
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

      List<StrategyTransaction> transactions = executeRegularInvestments(
        request, actualEndDate, investmentDay);

      if (transactions.isEmpty()) {
        throw new BacktestException(BacktestResponse.DATA_NOT_FOUND,
          "적립식 투자 전략 실행 가능한 데이터가 없습니다");
      }

      // 마무리(현재가/환율/배당재투자/계산) — 공통 로직 위임
      StrategyFinalizer.Result finalized = StrategyFinalizer.run(
        marketDataClient, request.getSymbol(), request.getCurrentFxRate(),
        request.getPurchaseFxRate(), Boolean.TRUE.equals(request.getReinvestDividends()),
        request.getDividendTaxRate(), transactions);

      log.info("DCA 전략 실행 완료: {} - {}회 투자, 수익률 {}%",
        request.getSymbol(), finalized.transactions().size(),
        finalized.calculation().getTotalReturnPercent());

      return responseMapper.toStrategyResponse(
        request, finalized.transactions(), finalized.calculation(), finalized.currentPrice());

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

  // 정기 적립식 투자 실행
  private List<StrategyTransaction> executeRegularInvestments(
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

    //  BULK API 사용: 매월 투자일의 환율 데이터를 한 번에 조회
    Map<LocalDate, BigDecimal> fxRateMap = new HashMap<>();
    if (request.getPurchaseFxRate() == null) {
      // 매월 투자일 계산
      List<LocalDate> investmentDates = new ArrayList<>();
      LocalDate date = request.getStartDate().withDayOfMonth(
        Math.min(investmentDay, request.getStartDate().lengthOfMonth()));

      while (!date.isAfter(actualEndDate)) {
        investmentDates.add(date);
        date = date.plusMonths(interval)
          .withDayOfMonth(Math.min(investmentDay, date.plusMonths(interval).lengthOfMonth()));
      }

      fxRateMap = BacktestDataUtils.getBulkFxRates(marketDataClient, investmentDates);
      log.info("환율 데이터 조회 완료: {}개", fxRateMap.size());
    }

    // 정해진 날짜와 주기에 따라 투자 실행
    LocalDate currentDate = request.getStartDate().withDayOfMonth(
      Math.min(investmentDay, request.getStartDate().lengthOfMonth()));

    //  메모리에서 데이터 조회 (API 호출 없음)
    while (!currentDate.isAfter(actualEndDate)) {
      // 총 투자금 한도 체크
      if (investmentLimit != null && totalInvestedSoFar.compareTo(investmentLimit) >= 0) {
        log.info("총 투자금 한도({})에 도달하여 투자 종료", investmentLimit);
        break;
      }

      try {
        // 메모리에서 주가 조회 (시장 휴일 대응: 최대 5일 전까지 검색)
        OHLCPriceDto priceData = PriceLookup.fromMap(priceMap, currentDate, 5);

        // 환율 조회 (메모리에서)
        BigDecimal purchaseFxRate;
        if (request.getPurchaseFxRate() != null) {
          purchaseFxRate = request.getPurchaseFxRate();
        } else {
          purchaseFxRate = fxRateMap.get(currentDate);
        }

        if (priceData != null && priceData.available() && purchaseFxRate != null) {
          // 매수 실행
          BigDecimal shares = BacktestCalculationUtils.calculateShares(
            request.getMonthlyAmount(), purchaseFxRate, PriceLookup.effectiveClose(priceData));

          // 거래 기록 생성
          StrategyTransaction transaction = StrategyTransaction.builder()
            .date(currentDate)              // 계획된 매수일
            .actualDate(priceData.date())   // 실제 거래일
            .price(PriceLookup.effectiveClose(priceData))
            .shares(shares)
            .amount(request.getMonthlyAmount())
            .fxRate(purchaseFxRate)
            .trigger(BacktestConstants.TransactionTrigger.MONTHLY_INVESTMENT)
            .build();

          transactions.add(transaction);
          totalInvestedSoFar = totalInvestedSoFar.add(request.getMonthlyAmount());

          log.debug("DCA 투자 실행: {} - {}원 투자, {}주 매수 (총 {}원)",
            currentDate, request.getMonthlyAmount(), shares, totalInvestedSoFar);
        }

      } catch (Exception e) {
        log.warn("적립식 투자 전략 - {} 날짜 데이터 처리 실패: {}", currentDate, e.getMessage());
      }

      currentDate = currentDate.plusMonths(interval)
        .withDayOfMonth(Math.min(investmentDay,
          currentDate.plusMonths(interval).lengthOfMonth()));
    }

    return transactions;
  }

}
