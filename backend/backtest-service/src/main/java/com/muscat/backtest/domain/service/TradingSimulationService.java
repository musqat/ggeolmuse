package com.muscat.backtest.domain.service;

import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.SimulationResponse;

// 단일 시점 투자 시뮬레이션: 과거 특정 시점에 투자했을 때의 수익률
public interface TradingSimulationService {

  SimulationResponse runSimulation(SimulationRequest request);

  // 비교 · 분석처럼 여러 번 돌릴 때 recordHistory=false 로 이력을 남기지 않음
  default SimulationResponse runSimulation(SimulationRequest request, boolean recordHistory) {
    return runSimulation(request);
  }
}
