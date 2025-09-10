package com.muscat.backtest.domain.service.impl;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.domain.dto.request.InvestmentRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.InvestmentResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.service.TradingSimulationService;
import com.muscat.backtest.domain.repository.BacktestHistoryRepository;
import com.muscat.backtest.domain.entity.BacktestHistory;
import com.muscat.backtest.domain.repository.InvestmentBacktestResultRepository;
import com.muscat.backtest.domain.entity.InvestmentBacktestResult;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.backtest.infra.client.TradeServiceClient;
import com.muscat.backtest.infra.client.dto.HoldingDto;
import com.muscat.backtest.infra.client.dto.TradeDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 거래 시뮬레이션 및 투자 실행 통합 서비스 구현체
@Service
@RequiredArgsConstructor
@Slf4j
public class TradingSimulationServiceImpl implements TradingSimulationService {

  private final MarketDataClient marketDataClient;
  private final TradeServiceClient tradeServiceClient;
  private final ResponseMapper responseMapper;
  private final BacktestHistoryRepository backtestHistoryRepository;
  private final InvestmentBacktestResultRepository investmentBacktestResultRepository;
  private final ObjectMapper objectMapper;

  // 과거 특정 시점 투자 시뮬레이션을 실행하고 결과를 반환
  @Override
  public SimulationResponse runSimulation(SimulationRequest request) {
    BacktestLogger.setBacktestContext(request.getUserId(), "SIMULATION", request.getSymbol());

    log.info("백테스팅 시뮬레이션 시작: {}", request);

    try {
      // 과거 매수 시점 데이터 조회
      var purchaseData = BacktestDataUtils.getHistoricalPrice(marketDataClient, request.getSymbol(),
          request.getPurchaseDate());
      var purchaseFxRate = BacktestDataUtils.getHistoricalFxRate(marketDataClient,
          request.getPurchaseDate());

      // 현재 시점 데이터 조회
      var currentData = BacktestDataUtils.getCurrentPrice(marketDataClient, request.getSymbol());
      var currentFxRate = BacktestDataUtils.getCurrentFxRate(marketDataClient);

      // 매수 가격 및 USD 환산
      BigDecimal purchasePriceUsd = purchaseData.getClosePrice();
      
      // 디버깅 로그 추가
      log.info("환전 계산 - KRW: {}, 환율: {}", request.getInvestmentAmount(), purchaseFxRate.rate());
      
      BigDecimal usdAmount = MoneyUtils.calculateKrwToUsd(
          request.getInvestmentAmount(), purchaseFxRate.rate());
          
      log.info("환전 결과 - USD: {}", usdAmount);

      // 수수료를 고려한 정수 주식수 계산
      BigDecimal shares = BacktestCalculationUtils.calculateWholeSharesWithFee(
          usdAmount, purchasePriceUsd, request.getTradingFeeRate());

      // 수수료 및 실제 비용 계산
      BigDecimal tradingFee = BacktestCalculationUtils.calculateTradingFee(usdAmount,
          request.getTradingFeeRate());
      BigDecimal totalCost = BacktestCalculationUtils.calculateTotalCost(shares, purchasePriceUsd,
          tradingFee);
      BigDecimal remainingCash = BacktestCalculationUtils.calculateRemainingCash(usdAmount,
          totalCost);

      // 현재 가치 계산
      BigDecimal currentPriceUsd = currentData.getCurrentPrice();
      BigDecimal currentValueUsd = shares.multiply(currentPriceUsd);
      BigDecimal currentValueKrw = MoneyUtils.calculateUsdToKrw(
          currentValueUsd, currentFxRate.rate());

      // 주식 수익률 계산
      BigDecimal stockReturn = currentPriceUsd.subtract(purchasePriceUsd);
      BigDecimal stockReturnPercent = MoneyUtils.calculateReturnRate(
          purchasePriceUsd, currentPriceUsd);

      // 환율 수익률 계산
      BigDecimal fxReturn = currentFxRate.rate().subtract(purchaseFxRate.rate());
      BigDecimal fxReturnPercent = MoneyUtils.calculateReturnRate(
          purchaseFxRate.rate(), currentFxRate.rate());

      // 배당금 계산
      var dividendHistory = BacktestDataUtils.getDividendHistory(marketDataClient,
          request.getSymbol(),
          request.getPurchaseDate(), LocalDate.now());
      BigDecimal totalDividends = BacktestCalculationUtils.calculateTotalDividends(
          dividendHistory, shares, request.getPurchaseDate(), LocalDate.now());
      BigDecimal dividendYield = BacktestCalculationUtils.calculateDividendYield(
          totalDividends, shares, currentPriceUsd);

      // 전체 수익률 계산 (배당 포함)
      BigDecimal totalDividendsKrw = totalDividends.compareTo(BigDecimal.ZERO) > 0 
          ? MoneyUtils.calculateUsdToKrw(totalDividends, currentFxRate.rate())
          : BigDecimal.ZERO;
      BigDecimal totalReturnKrw = currentValueKrw.subtract(request.getInvestmentAmount())
          .add(totalDividendsKrw);
      BigDecimal totalReturnPercent = MoneyUtils.calculateReturnRate(
          request.getInvestmentAmount(), currentValueKrw.add(totalDividendsKrw));

      SimulationResponse response = responseMapper.toSimulationResponse(
          request, purchasePriceUsd, shares, currentPriceUsd, currentValueUsd, currentValueKrw,
          stockReturn, stockReturnPercent, purchaseFxRate.rate(), currentFxRate.rate(),
          fxReturn, fxReturnPercent, totalDividends, dividendYield, tradingFee,
          remainingCash, totalReturnKrw, totalReturnPercent);

      // 백테스트 히스토리 기록
      saveBacktestHistory(request.getUserId(), BacktestHistory.BacktestType.SYMBOL_COMPARISON, request);
      
      return response;
    } finally {
      BacktestLogger.remove("operation");
    }
  }

  // Trade에서 과거에 매수한 주식의 현재까지 백테스트 결과 계산
  @Override
  public InvestmentResponse executeInvestment(InvestmentRequest request, String authorization) {
    log.info("과거 매수 백테스트 시작: {}", request);

    try {
      // 사용자의 모든 portfolio 조회
      List<HoldingDto> holdings = tradeServiceClient.getPortfolio(authorization);

      if (holdings.isEmpty()) {
        throw new BacktestException(BacktestResponse.DATA_NOT_FOUND,
            "해당 조건에 맞는 보유 주식을 찾을 수 없습니다");
      }

      // 여러 holdings에 대한 백테스트 결과 계산
      List<InvestmentResponse> results = new ArrayList<>();

      for (HoldingDto holding : holdings) {
        // 실제 거래 내역을 조회하여 최초 매수 날짜를 찾기
        List<TradeDto> tradeHistory = tradeServiceClient.getTradeHistoryBySymbol(authorization,
            holding.getSymbol());

        // BUY 거래 중에서 가장 이른 날짜를 찾기
        LocalDate earliestBuyDate = tradeHistory.stream()
            .filter(trade -> "BUY".equals(trade.getTradeType()))
            .map(TradeDto::getTradeDate)
            .min(LocalDate::compareTo)
            .orElse(holding.getPurchaseDate()); // fallback to createdAt if no BUY trades found

        // 각 holding에 대해 시뮬레이션 요청 생성
        SimulationRequest simulationRequest = SimulationRequest.builder()
            .symbol(holding.getSymbol())
            .purchaseDate(earliestBuyDate)
            .investmentAmount(holding.getTotalInvested())
            .userId(request.getUserId())
            .build();

        // 과거 매수 시점부터 현재까지의 백테스트 실행
        SimulationResponse backtestResult = runSimulation(simulationRequest);

        // 백테스트 결과를 포트폴리오 형태로 변환
        InvestmentResponse investmentResult = responseMapper.toInvestmentResponse(holding,
            backtestResult);
        results.add(investmentResult);
      }

      // 백테스트 결과 저장
      InvestmentResponse finalResult = results.size() == 1 ? results.get(0) : results.get(0);
      saveInvestmentBacktestResult(request.getUserId(), finalResult);

      // 백테스트 히스토리 기록
      saveBacktestHistory(request.getUserId(), BacktestHistory.BacktestType.INVESTMENT_BACKTEST, request);

      // 단일 결과인 경우 첫 번째 결과 반환, 다중 결과인 경우 통합된 결과 반환
      if (results.size() == 1) {
        return results.get(0);
      } else {
        // 다중 holdings의 경우 통합 결과 생성 (추후 구현 필요)
        return results.get(0); // 임시로 첫 번째 결과 반환
      }

    } catch (BacktestException e) {
      throw e;
    } catch (Exception e) {
      log.error("과거 매수 백테스트 중 예상치 못한 오류: {}", e.getMessage(), e);

      if (e.getMessage().contains("환율") || e.getMessage().contains("FX")) {
        throw new BacktestException(BacktestResponse.FX_CONVERSION_ERROR,
            "환율 변환 중 오류: " + e.getMessage());
      } else if (e.getMessage().contains("배당") || e.getMessage().contains("dividend")) {
        throw new BacktestException(BacktestResponse.DIVIDEND_CALCULATION_ERROR,
            "배당금 계산 중 오류: " + e.getMessage());
      } else if (e.getMessage().contains("수익률") || e.getMessage().contains("return")) {
        throw new BacktestException(BacktestResponse.RETURN_CALCULATION_ERROR,
            "수익률 계산 중 오류: " + e.getMessage());
      } else {
        throw new BacktestException(BacktestResponse.CALCULATION_ERROR,
            "백테스트 계산 중 오류: " + e.getMessage());
      }
    }
  }

  private void saveBacktestHistory(String userId, BacktestHistory.BacktestType backtestType, Object requestParams) {
    executeWithFallback(() -> {
      try {
        String paramsJson = objectMapper.writeValueAsString(requestParams);
        BacktestHistory history = BacktestHistory.builder()
            .userId(userId)
            .backtestType(backtestType)
            .requestParams(paramsJson)
            .build();
        backtestHistoryRepository.save(history);
        return null;
      } catch (JsonProcessingException e) {
        log.warn("JSON 변환 오류: userId={}, error={}", userId, e.getMessage());
        return null;
      }
    }, "백테스트 히스토리 저장", userId);
  }

  private void saveInvestmentBacktestResult(String userId, InvestmentResponse result) {
    executeWithFallback(() -> {
      try {
        String resultJson = objectMapper.writeValueAsString(result);
        LocalDateTime now = LocalDateTime.now();

        InvestmentBacktestResult entity = investmentBacktestResultRepository
            .findByUserId(userId)
            .orElse(InvestmentBacktestResult.builder().userId(userId).build());

        entity.setBacktestResult(resultJson);
        entity.setCalculatedAt(now);
        entity.setNextScheduledAt(now.plusHours(24));
        entity.setStatus(InvestmentBacktestResult.CalculationStatus.COMPLETED);

        investmentBacktestResultRepository.save(entity);
        log.info("투자 백테스트 결과 저장 완료: userId={}, calculatedAt={}", userId, now);
        return null;
      } catch (JsonProcessingException e) {
        log.warn("JSON 변환 오류: userId={}, error={}", userId, e.getMessage());
        return null;
      }
    }, "투자 백테스트 결과 저장", userId);
  }

  @Override
  public Optional<InvestmentResponse> getCachedInvestmentResult(String userId) {
    Optional<Optional<InvestmentResponse>> result = executeWithFallback(() -> {
      Optional<InvestmentBacktestResult> cachedResult = getValidCachedEntity(userId);
      if (cachedResult.isEmpty()) return Optional.empty();

      try {
        InvestmentResponse response = objectMapper.readValue(
            cachedResult.get().getBacktestResult(), InvestmentResponse.class);
        log.debug("캐시된 투자 백테스트 결과 조회 성공: userId={}", userId);
        return Optional.of(response);
      } catch (JsonProcessingException e) {
        log.warn("JSON 파싱 오류: userId={}, error={}", userId, e.getMessage());
        return Optional.empty();
      }
    }, "캐시된 투자 백테스트 결과 조회", userId);
    
    return result.orElse(Optional.empty());
  }

  @Override
  public Optional<InvestmentBacktestResult> getCachedInvestmentResultEntity(String userId) {
    return executeWithFallback(() -> getValidCachedEntity(userId), 
        "캐시된 투자 백테스트 Entity 조회", userId).orElse(Optional.empty());
  }

  private Optional<InvestmentBacktestResult> getValidCachedEntity(String userId) {
    return investmentBacktestResultRepository.findByUserId(userId)
        .filter(entity -> entity.getStatus() == InvestmentBacktestResult.CalculationStatus.COMPLETED);
  }

  private <T> Optional<T> executeWithFallback(Supplier<T> operation, String operationName, String userId) {
    try {
      return Optional.ofNullable(operation.get());
    } catch (Exception e) {
      log.warn("{} 실패: userId={}, error={}", operationName, userId, e.getMessage());
    }
    return Optional.empty();
  }
}