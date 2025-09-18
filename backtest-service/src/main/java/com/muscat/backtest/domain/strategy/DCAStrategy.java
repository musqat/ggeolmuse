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

// DCA(Dollar Cost Averaging) 적립식 투자 전략 - 매월 지정된 날짜에 정해진 금액을 자동으로 투자
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

      // 현재 가격과 환율 조회
      var currentPrice = BacktestDataUtils.getCurrentPrice(marketDataClient, request.getSymbol());
      var currentFxRate = BacktestDataUtils.getCurrentFxRate(marketDataClient);

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

      StrategyCalculationResult calculation = StrategyCalculator.calculate(
          transactions, totalInvested, totalShares, totalFxRateSum, currentPrice, currentFxRate);

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
    if (request.getPurchaseDay() == null || request.getPurchaseDay() < 1 || request.getPurchaseDay() > 31) {
      throw new BacktestException(BacktestResponse.INVALID_PURCHASE_DAY);
    }
  }

  // 정기 투자 실행
  private List<StrategyTransaction> executeRegularInvestments(
      DcaStrategyRequest request, LocalDate actualEndDate, int investmentDay) {

    List<StrategyTransaction> transactions = new ArrayList<>();

    // 매월 정해진 날짜에 투자 실행
    LocalDate currentDate = request.getStartDate().withDayOfMonth(
        Math.min(investmentDay, request.getStartDate().lengthOfMonth()));

    while (!currentDate.isAfter(actualEndDate)) {
      try {
        // 해당 날짜의 주가와 환율 조회
        var priceData = BacktestDataUtils.getHistoricalPrice(
            marketDataClient, request.getSymbol(), currentDate);
        var fxData = BacktestDataUtils.getHistoricalFxRate(
            marketDataClient, currentDate);

        if (priceData.isAvailable() && fxData != null) {
          // 투자 실행
          BigDecimal shares = BacktestCalculationUtils.calculateShares(
              request.getMonthlyAmount(), fxData.rate(), priceData.getClosePrice());

          // 거래 기록 생성 (실제 거래일 포함)
          LocalDate actualTradeDate = priceData.getDate(); // OHLC 데이터에서 실제 날짜 가져오기
          StrategyTransaction transaction = StrategyTransaction.builder()
              .date(currentDate)              // 계획된 매수일
              .actualDate(actualTradeDate)    // 실제 거래일
              .price(priceData.getClosePrice())
              .shares(shares)
              .amount(request.getMonthlyAmount())
              .fxRate(fxData.rate())
              .trigger("월정액")
              .build();

          transactions.add(transaction);

          log.debug("DCA 투자 실행: {} - {}원 투자, {}주 매수",
              currentDate, request.getMonthlyAmount(), shares);
        }

      } catch (Exception e) {
        log.warn("적립식 투자 전략 - {} 날짜 데이터 처리 실패: {}", currentDate, e.getMessage());
      }

      currentDate = currentDate.plusMonths(1)
          .withDayOfMonth(Math.min(investmentDay,
              currentDate.plusMonths(1).lengthOfMonth()));
    }

    return transactions;
  }

}