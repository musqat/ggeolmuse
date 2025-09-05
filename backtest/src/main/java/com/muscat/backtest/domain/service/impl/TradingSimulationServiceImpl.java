package com.muscat.backtest.domain.service.impl;

import com.muscat.backtest.common.enums.BacktestResponseCode;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.logging.BacktestLogger;
import com.muscat.backtest.common.util.BacktestCalculationUtils;
import com.muscat.backtest.common.util.BacktestDataUtils;
import com.muscat.backtest.domain.dto.request.InvestmentRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.InvestmentResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.domain.service.TradingSimulationService;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.backtest.infra.client.TradeServiceClient;
import com.muscat.backtest.infra.client.dto.HoldingDto;
import com.muscat.backtest.infra.client.dto.TradeDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

  // 과거 특정 시점 투자 시뮬레이션을 실행하고 결과를 반환
  @Override
  public SimulationResponse runSimulation(SimulationRequest request) {
    BacktestLogger.setBacktestContext(request.getUserId(), "SIMULATION", request.getSymbol());

    try {
      log.info("백테스팅 시뮬레이션 시작: {}", request);

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
      BigDecimal usdAmount = BacktestCalculationUtils.convertKrwToUsd(
          request.getInvestmentAmount(), purchaseFxRate.rate());

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
      BigDecimal currentValueKrw = BacktestCalculationUtils.convertUsdToKrw(
          currentValueUsd, currentFxRate.rate());

      // 주식 수익률 계산
      BigDecimal stockReturn = currentPriceUsd.subtract(purchasePriceUsd);
      BigDecimal stockReturnPercent = BacktestCalculationUtils.calculatePercentageReturn(
          currentPriceUsd, purchasePriceUsd);

      // 환율 수익률 계산
      BigDecimal fxReturn = currentFxRate.rate().subtract(purchaseFxRate.rate());
      BigDecimal fxReturnPercent = BacktestCalculationUtils.calculatePercentageReturn(
          currentFxRate.rate(), purchaseFxRate.rate());

      // 배당금 계산
      var dividendHistory = BacktestDataUtils.getDividendHistory(marketDataClient,
          request.getSymbol(),
          request.getPurchaseDate(), LocalDate.now());
      BigDecimal totalDividends = BacktestCalculationUtils.calculateTotalDividends(
          dividendHistory, shares, request.getPurchaseDate(), LocalDate.now());
      BigDecimal dividendYield = BacktestCalculationUtils.calculateDividendYield(
          totalDividends, shares, currentPriceUsd);

      // 전체 수익률 계산 (배당 포함)
      BigDecimal totalDividendsKrw = BacktestCalculationUtils.convertUsdToKrw(totalDividends,
          currentFxRate.rate());
      BigDecimal totalReturnKrw = currentValueKrw.subtract(request.getInvestmentAmount())
          .add(totalDividendsKrw);
      BigDecimal totalReturnPercent = BacktestCalculationUtils.calculatePercentageReturn(
          currentValueKrw.add(totalDividendsKrw), request.getInvestmentAmount());

      return responseMapper.toSimulationResponse(
          request, purchasePriceUsd, shares, currentPriceUsd, currentValueUsd, currentValueKrw,
          stockReturn, stockReturnPercent, purchaseFxRate.rate(), currentFxRate.rate(),
          fxReturn, fxReturnPercent, totalDividends, dividendYield, tradingFee,
          remainingCash, totalReturnKrw, totalReturnPercent);
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
        throw new BacktestException(BacktestResponseCode.DATA_NOT_FOUND,
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
        throw new BacktestException(BacktestResponseCode.FX_CONVERSION_ERROR,
            "환율 변환 중 오류: " + e.getMessage());
      } else if (e.getMessage().contains("배당") || e.getMessage().contains("dividend")) {
        throw new BacktestException(BacktestResponseCode.DIVIDEND_CALCULATION_ERROR,
            "배당금 계산 중 오류: " + e.getMessage());
      } else if (e.getMessage().contains("수익률") || e.getMessage().contains("return")) {
        throw new BacktestException(BacktestResponseCode.RETURN_CALCULATION_ERROR,
            "수익률 계산 중 오류: " + e.getMessage());
      } else {
        throw new BacktestException(BacktestResponseCode.CALCULATION_ERROR,
            "백테스트 계산 중 오류: " + e.getMessage());
      }
    }
  }
}