package com.muscat.backtest.domain.service;

import com.muscat.backtest.domain.dto.request.InvestmentRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.InvestmentResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.entity.InvestmentBacktestResult;
import java.util.Optional;

// 거래 시뮬레이션 및 투자 실행 서비스
public interface TradingSimulationService {

  // 과거 특정 시점 투자 시뮬레이션 실행
  SimulationResponse runSimulation(SimulationRequest request);

  // 사용자의 투자 내역을 시뮬레이션 실행
  InvestmentResponse executeInvestment(InvestmentRequest request, String authorization);

  // 사용자의 캐시된 투자 백테스트 결과 조회
  Optional<InvestmentResponse> getCachedInvestmentResult(String userId);

  // 사용자의 캐시된 투자 백테스트 결과 Entity 조회 (Trade 서비스용)
  Optional<InvestmentBacktestResult> getCachedInvestmentResultEntity(String userId);
}