package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.calculation.StrategyCalculationResult;
import com.muscat.backtest.common.calculation.StrategyCalculator;
import com.muscat.backtest.common.constants.BacktestConstants;
import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DCA(Dollar Cost Averaging) 적립식 투자 전략
 * <p>
 * 매월 지정된 날짜에 정해진 금액을 자동으로 투자하는 전략입니다.
 * 배당금 재투자, 원천징수세 적용, 환율 변동 등을 고려하여
 * 장기적인 투자 성과를 시뮬레이션합니다.
 * </p>
 *
 * <p>주요 기능:</p>
 * <ul>
 *   <li>정기적인 월 투자 실행</li>
 *   <li>배당금 자동 재투자 (선택 가능)</li>
 *   <li>배당 원천징수세 적용</li>
 *   <li>환율 변동 추적</li>
 *   <li>주말/공휴일 처리 (이전 영업일 적용)</li>
 * </ul>
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
   * DCA 전략을 실행하고 백테스팅 결과를 반환합니다.
   * <p>
   * 시작일부터 종료일까지 매월 지정된 날짜에 정해진 금액을 투자하며,
   * 배당금 재투자, 환율 변동, 원천징수세 등을 모두 고려합니다.
   * </p>
   *
   * @param request DCA 전략 실행 요청 (종목, 기간, 월투자액, 매수일 등)
   * @return 전략 실행 결과 (총 투자금, 수익률, 거래 내역, 배당금 등)
   * @throws BacktestException 요청 검증 실패 또는 데이터 조회 실패 시
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

      // 현재 주가와 환율 조회
      var currentPrice = BacktestDataUtils.getCurrentPrice(marketDataClient, request.getSymbol());

      // 환율 조회 (수동 설정 우선, 없으면 자동 조회)
      MarketDataClient.FxRate currentFxRate;
      if (request.getCurrentFxRate() != null) {
        currentFxRate = new MarketDataClient.FxRate(LocalDate.now(), request.getCurrentFxRate());
      } else {
        currentFxRate = BacktestDataUtils.getCurrentFxRate(marketDataClient);
      }

      // 배당금 조회 (첫 매수일부터 현재까지)
      LocalDate firstPurchaseDate = transactions.getFirst().getDate();
      var dividendHistory = BacktestDataUtils.getDividendHistory(
        marketDataClient, request.getSymbol(), firstPurchaseDate, LocalDate.now());

      // 결과 계산
      BigDecimal totalInvested = transactions.stream()
        .map(StrategyTransaction::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      BigDecimal totalShares = transactions.stream()
        .map(StrategyTransaction::getShares)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      // 투자금액으로 가중평균한 환율 합계 계산 (가중평균 환율 = Σ(투자금액 × 환율) / Σ투자금액)
      BigDecimal totalFxRateSum = transactions.stream()
        .map(t -> t.getFxRate().multiply(t.getAmount()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      // 재투자된 배당금 추적용 변수
      final BigDecimal[] dividendsReinvestedArray = {BigDecimal.ZERO};

      // 배당금 재투자 처리 (각 배당일에 순차적으로 재투자)
      if (Boolean.TRUE.equals(request.getReinvestDividends()) &&
        dividendHistory != null &&
        dividendHistory.getDividends() != null &&
        !dividendHistory.getDividends().isEmpty()) {

        log.info("배당금 재투자 실행 시작: {} 개의 배당 내역", dividendHistory.getDividends().size());

        // BULK API: 배당 날짜들의 가격 및 환율 데이터를 한 번에 조회
        List<LocalDate> dividendDates = dividendHistory.getDividends().stream()
          .filter(dividend -> dividend.getExDate() != null)
          .filter(dividend -> !dividend.getExDate().isBefore(firstPurchaseDate) &&
            !dividend.getExDate().isAfter(LocalDate.now()))
          .map(d -> d.getExDate())
          .toList();

        if (!dividendDates.isEmpty()) {
          // 배당 날짜들의 가격 데이터 조회
          List<OHLCPriceDto> dividendPrices = marketDataClient.getOHLCPriceRange(
            request.getSymbol(),
            dividendDates.get(0).toString(),
            dividendDates.get(dividendDates.size() - 1).toString()
          );

          java.util.Map<LocalDate, OHLCPriceDto> dividendPriceMap = dividendPrices.stream()
            .filter(OHLCPriceDto::available)
            .collect(java.util.stream.Collectors.toMap(OHLCPriceDto::date, p -> p));

          // 배당 날짜들의 환율 데이터 조회
          final java.util.Map<LocalDate, BigDecimal> dividendFxRateMap =
            request.getPurchaseFxRate() == null
              ? BacktestDataUtils.getBulkFxRates(marketDataClient, dividendDates)
              : new java.util.HashMap<>();

          log.info("배당 재투자 데이터 조회 완료: 가격 {}개, 환율 {}개",
            dividendPriceMap.size(), dividendFxRateMap.size());

          //  메모리에서 데이터 조회하여 배당 재투자 실행
          dividendHistory.getDividends().stream()
            .filter(dividend -> dividend.getExDate() != null)
            .filter(dividend -> !dividend.getExDate().isBefore(firstPurchaseDate) &&
              !dividend.getExDate().isAfter(LocalDate.now()))
            .sorted((d1, d2) -> d1.getExDate().compareTo(d2.getExDate()))
            .forEach(dividend -> {
              // 배당 지급 시점의 보유 주식수 계산
              BigDecimal sharesAtDividendDate = transactions.stream()
                .filter(tx -> !tx.getDate().isAfter(dividend.getExDate()))
                .map(StrategyTransaction::getShares)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

              if (sharesAtDividendDate.compareTo(BigDecimal.ZERO) > 0) {
                // 배당금 계산
                BigDecimal dividendAmount = dividend.getAmount().multiply(sharesAtDividendDate)
                  .setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);

                // 배당 원천징수세 적용
                BigDecimal taxRate = request.getDividendTaxRate();
                BigDecimal afterTaxDividend = dividendAmount;
                if (taxRate != null && taxRate.compareTo(BigDecimal.ZERO) > 0) {
                  BigDecimal taxAmount = dividendAmount.multiply(taxRate)
                    .setScale(BacktestConstants.Money.SCALE, BacktestConstants.Money.ROUNDING_MODE);
                  afterTaxDividend = dividendAmount.subtract(taxAmount);
                  log.debug("배당 원천징수: {} - 배당금 ${} → 세후 ${} (세율 {}%)",
                    dividend.getExDate(), dividendAmount, afterTaxDividend,
                    taxRate.multiply(BacktestConstants.Money.PERCENTAGE_MULTIPLIER));
                }

                // 메모리에서 배당금 지급일의 주가와 환율 조회
                OHLCPriceDto priceAtDividendDate = dividendPriceMap.get(dividend.getExDate());

                BigDecimal fxRateAtDividendDate;
                if (request.getPurchaseFxRate() != null) {
                  fxRateAtDividendDate = request.getPurchaseFxRate();
                } else {
                  fxRateAtDividendDate = dividendFxRateMap.getOrDefault(
                    dividend.getExDate(), currentFxRate.rate());
                }

                if (priceAtDividendDate != null && priceAtDividendDate.available()) {
                  // 세후 배당금으로 추가 매수 가능한 주식수 계산
                  BigDecimal additionalShares = afterTaxDividend.divide(
                    priceAtDividendDate.closePrice(), BacktestConstants.Money.SHARES_SCALE,
                    BacktestConstants.Money.ROUNDING_MODE);

                  // 배당 재투자 거래 기록 추가
                  StrategyTransaction dividendReinvestment = StrategyTransaction.builder()
                    .date(dividend.getExDate())
                    .actualDate(priceAtDividendDate.date())
                    .price(priceAtDividendDate.closePrice())
                    .shares(additionalShares)
                    .amount(BigDecimal.ZERO)  // 배당금 재투자이므로 추가 투자금 없음
                    .fxRate(fxRateAtDividendDate)
                    .trigger(BacktestConstants.TransactionTrigger.DIVIDEND_REINVESTMENT)
                    .build();

                  transactions.add(dividendReinvestment);
                  dividendsReinvestedArray[0] = dividendsReinvestedArray[0].add(afterTaxDividend);

                  log.debug("배당 재투자: {} - ${} ({}주 보유) -> {}주 추가 매수 @${}",
                    dividend.getExDate(), afterTaxDividend, sharesAtDividendDate,
                    additionalShares, priceAtDividendDate.closePrice());
                }
              }
            });
        }

        log.info("배당금 재투자 완료: 총 ${} 재투자", dividendsReinvestedArray[0]);
      }

      BigDecimal dividendsReinvested = dividendsReinvestedArray[0];

      // 배당 재투자 후 총 보유 주식수 재계산
      totalShares = transactions.stream()
        .map(StrategyTransaction::getShares)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      // 총 배당금 계산 (표시용, 재투자 여부와 무관)
      BigDecimal totalDividends = BacktestCalculationUtils.calculateTotalDividends(
        dividendHistory, transactions, firstPurchaseDate, LocalDate.now());

      StrategyCalculationResult calculation = StrategyCalculator.calculate(
        transactions, totalInvested, totalShares, totalFxRateSum, currentPrice, currentFxRate,
        totalDividends, dividendsReinvested);

      log.info("DCA 전략 실행 완료: {} - {}회 투자, 수익률 {}%",
        request.getSymbol(), transactions.size(), calculation.getTotalReturnPercent());

      return responseMapper.toStrategyResponse(request, transactions, calculation, currentPrice);

    } finally {
      BacktestLogger.remove("operation");
    }
  }

  // DCA 전략 특화 요청 검증
  private void validateDcaRequest(DcaStrategyRequest request) {
    if (request == null) {
      throw new BacktestException(BacktestResponse.STRATEGY_REQUEST_NULL);
    }
    if (request.getSymbol() == null || request.getSymbol().trim().isEmpty()) {
      throw new BacktestException(BacktestResponse.STRATEGY_SYMBOL_REQUIRED);
    }
    if (request.getStartDate() == null) {
      throw new BacktestException(BacktestResponse.STRATEGY_START_DATE_REQUIRED);
    }
    if (request.getEndDate() == null) {
      throw new BacktestException(BacktestResponse.STRATEGY_END_DATE_REQUIRED);
    }
    if (request.getStartDate().isAfter(request.getEndDate())) {
      throw new BacktestException(BacktestResponse.STRATEGY_DATE_RANGE_INVALID);
    }
    if (request.getMonthlyAmount() == null
      || request.getMonthlyAmount().compareTo(BigDecimal.ZERO) <= 0) {
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
    List<OHLCPriceDto> allPrices = marketDataClient.getOHLCPriceRange(
      request.getSymbol(),
      request.getStartDate().toString(),
      actualEndDate.toString()
    );

    // 날짜별 빠른 조회를 위한 Map 생성
    java.util.Map<LocalDate, OHLCPriceDto> priceMap = allPrices.stream()
      .filter(OHLCPriceDto::available)
      .collect(java.util.stream.Collectors.toMap(OHLCPriceDto::date, p -> p));

    log.info("가격 데이터 조회 완료: {}개", priceMap.size());

    //  BULK API 사용: 매월 투자일의 환율 데이터를 한 번에 조회
    java.util.Map<LocalDate, BigDecimal> fxRateMap = new java.util.HashMap<>();
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
        OHLCPriceDto priceData = null;
        LocalDate searchDate = currentDate;
        for (int i = 0; i < 5 && priceData == null; i++) {
          priceData = priceMap.get(searchDate);
          if (priceData == null) {
            searchDate = searchDate.minusDays(1);
          }
        }

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
            request.getMonthlyAmount(), purchaseFxRate, priceData.closePrice());

          // 거래 기록 생성
          StrategyTransaction transaction = StrategyTransaction.builder()
            .date(currentDate)              // 계획된 매수일
            .actualDate(priceData.date())   // 실제 거래일
            .price(priceData.closePrice())
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
