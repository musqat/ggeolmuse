package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.calculation.StrategyCalculationResult;
import com.muscat.backtest.common.calculation.StrategyCalculator;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// DCA(적립식) 투자 전략 - 매월 지정된 날짜에 정해진 금액을 자동으로 투자
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
      LocalDate firstPurchaseDate = transactions.get(0).getDate();
      var dividendHistory = BacktestDataUtils.getDividendHistory(
        marketDataClient, request.getSymbol(), firstPurchaseDate, LocalDate.now());

      // 결과 계산
      BigDecimal totalInvested = transactions.stream()
        .map(StrategyTransaction::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      BigDecimal totalShares = transactions.stream()
        .map(StrategyTransaction::getShares)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      BigDecimal totalFxRateSum = transactions.stream()
        .map(StrategyTransaction::getFxRate)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      // 재투자된 배당금 추적용 변수
      final BigDecimal[] dividendsReinvestedArray = {BigDecimal.ZERO};

      // 배당금 재투자 처리 (각 배당일에 순차적으로 재투자)
      if (Boolean.TRUE.equals(request.getReinvestDividends()) &&
        dividendHistory != null &&
        dividendHistory.getDividends() != null &&
        !dividendHistory.getDividends().isEmpty()) {

        log.info("배당금 재투자 실행 시작: {} 개의 배당 내역", dividendHistory.getDividends().size());

        // 배당 내역을 날짜순 정렬 후 순차 재투자
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
                .setScale(2, java.math.RoundingMode.HALF_UP);

              // 배당 원천징수세 적용
              BigDecimal taxRate = request.getDividendTaxRate();
              BigDecimal afterTaxDividend = dividendAmount;
              if (taxRate != null && taxRate.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal taxAmount = dividendAmount.multiply(taxRate)
                  .setScale(2, java.math.RoundingMode.HALF_UP);
                afterTaxDividend = dividendAmount.subtract(taxAmount);
                log.info("배당 원천징수: {} - 배당금 ${} → 세후 ${} (세율 {}%)",
                  dividend.getExDate(), dividendAmount, afterTaxDividend,
                  taxRate.multiply(BigDecimal.valueOf(100)));
              }

              // 배당금 지급일의 주가와 환율 조회
              var priceAtDividendDate = BacktestDataUtils.getHistoricalPrice(
                marketDataClient, request.getSymbol(), dividend.getExDate());

              BigDecimal fxRateAtDividendDate;
              if (request.getPurchaseFxRate() != null) {
                fxRateAtDividendDate = request.getPurchaseFxRate();
              } else {
                var fxData = BacktestDataUtils.getHistoricalFxRate(marketDataClient,
                  dividend.getExDate());
                fxRateAtDividendDate = fxData != null ? fxData.rate() : currentFxRate.rate();
              }

              // 세후 배당금으로 추가 매수 가능한 주식수 계산
              BigDecimal additionalShares = afterTaxDividend.divide(
                priceAtDividendDate.getClosePrice(), 8, java.math.RoundingMode.HALF_UP);

              // 배당 재투자 거래 기록 추가
              StrategyTransaction dividendReinvestment = StrategyTransaction.builder()
                .date(dividend.getExDate())
                .actualDate(priceAtDividendDate.getDate())
                .price(priceAtDividendDate.getClosePrice())
                .shares(additionalShares)
                .amount(BigDecimal.ZERO)  // 배당금 재투자이므로 추가 투자금 없음
                .fxRate(fxRateAtDividendDate)
                .trigger("배당 재투자")
                .build();

              transactions.add(dividendReinvestment);
              dividendsReinvestedArray[0] = dividendsReinvestedArray[0].add(afterTaxDividend);

              log.info("배당 재투자: {} - ${} ({}주 보유) -> {}주 추가 매수 @${}",
                dividend.getExDate(), afterTaxDividend, sharesAtDividendDate,
                additionalShares, priceAtDividendDate.getClosePrice());
            }
          });

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

    // 정해진 날짜와 주기에 따라 투자 실행
    LocalDate currentDate = request.getStartDate().withDayOfMonth(
      Math.min(investmentDay, request.getStartDate().lengthOfMonth()));

    while (!currentDate.isAfter(actualEndDate)) {
      // 총 투자금 한도 체크
      if (investmentLimit != null && totalInvestedSoFar.compareTo(investmentLimit) >= 0) {
        log.info("총 투자금 한도({})에 도달하여 투자 종료", investmentLimit);
        break;
      }

      try {
        // 해당 날짜의 주가 조회
        var priceData = BacktestDataUtils.getHistoricalPrice(
          marketDataClient, request.getSymbol(), currentDate);

        // 환율 조회 (수동 설정 우선)
        BigDecimal purchaseFxRate;
        if (request.getPurchaseFxRate() != null) {
          purchaseFxRate = request.getPurchaseFxRate();
        } else {
          var fxData = BacktestDataUtils.getHistoricalFxRate(marketDataClient, currentDate);
          purchaseFxRate = fxData != null ? fxData.rate() : null;
        }

        if (priceData.isAvailable() && purchaseFxRate != null) {
          // 매수 실행
          BigDecimal shares = BacktestCalculationUtils.calculateShares(
            request.getMonthlyAmount(), purchaseFxRate, priceData.getClosePrice());

          // 거래 기록 생성
          LocalDate actualTradeDate = priceData.getDate(); // 실제 거래일
          StrategyTransaction transaction = StrategyTransaction.builder()
            .date(currentDate)              // 계획된 매수일
            .actualDate(actualTradeDate)    // 실제 거래일
            .price(priceData.getClosePrice())
            .shares(shares)
            .amount(request.getMonthlyAmount())
            .fxRate(purchaseFxRate)
            .trigger("월정액")
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
