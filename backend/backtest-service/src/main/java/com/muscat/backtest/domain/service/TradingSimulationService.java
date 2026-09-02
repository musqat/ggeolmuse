package com.muscat.backtest.domain.service;

import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.SimulationResponse;

/**
 * 단일 시점 투자 시뮬레이션 서비스
 * 과거 특정 시점에 투자했을 때의 수익률을 시뮬레이션
 */
public interface TradingSimulationService {

  /**
   * 과거 특정 시점 투자 시뮬레이션을 실행
   * 지정된 날짜에 투자했을 경우의 수익률과 배당금을 계산하여 반환
   *
   * @param request 시뮬레이션 요청 정보 (종목, 투자일, 투자금액 등)
   * @return 시뮬레이션 결과 (수익률, 배당금, 현재 가치 등)
   */
  SimulationResponse runSimulation(SimulationRequest request);

  /**
   * 과거 특정 시점 투자 시뮬레이션 실행 (이력 기록 여부 지정)
   * 비교/분석처럼 다수 시뮬을 돌릴 때 recordHistory=false로 사용자 백테스트 이력 오염을 막는다
   *
   * @param request 시뮬레이션 요청 정보
   * @param recordHistory 백테스트 이력 저장 여부
   * @return 시뮬레이션 결과
   */
  default SimulationResponse runSimulation(SimulationRequest request, boolean recordHistory) {
    return runSimulation(request);
  }
}
