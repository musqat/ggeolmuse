package com.muscat.backtest.domain.service;

import com.muscat.backtest.domain.dto.request.InvestmentRequest;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.dto.response.InvestmentResponse;
import com.muscat.backtest.domain.dto.response.SimulationResponse;
import com.muscat.backtest.domain.entity.InvestmentBacktestResult;
import java.util.Optional;

/**
 * 거래 시뮬레이션 및 투자 실행 서비스
 * <p>
 * 과거 특정 시점에 투자했을 때의 수익률을 시뮬레이션하고,
 * 실제 투자를 실행하는 기능을 제공합니다.
 * </p>
 */
public interface TradingSimulationService {

  /**
   * 과거 특정 시점 투자 시뮬레이션을 실행합니다.
   * <p>
   * 지정된 날짜에 투자했을 경우의 수익률과 배당금을 계산하여 반환합니다.
   * </p>
   *
   * @param request 시뮬레이션 요청 정보 (종목, 투자일, 투자금액 등)
   * @return 시뮬레이션 결과 (수익률, 배당금, 현재 가치 등)
   */
  SimulationResponse runSimulation(SimulationRequest request);

  /**
   * 과거 특정 시점 투자 시뮬레이션을 실행합니다. (히스토리 기록 여부 지정)
   * <p>
   * 백테스트 히스토리 저장 여부를 선택할 수 있습니다.
   * </p>
   *
   * @param request 시뮬레이션 요청 정보
   * @param recordHistory 히스토리 기록 여부
   * @return 시뮬레이션 결과
   */
  default SimulationResponse runSimulation(SimulationRequest request, boolean recordHistory) {
    return runSimulation(request);
  }

  /**
   * 사용자의 실제 투자를 실행합니다.
   * <p>
   * 시뮬레이션 결과를 기반으로 실제 거래를 생성하고 포트폴리오에 반영합니다.
   * </p>
   *
   * @param request 투자 요청 정보
   * @param authorization 사용자 인증 토큰
   * @return 투자 실행 결과 (거래 정보, 포트폴리오 상태 등)
   */
  InvestmentResponse executeInvestment(InvestmentRequest request, String authorization);

  /**
   * 사용자의 캐시된 투자 백테스트 결과를 조회합니다.
   * <p>
   * 최근 실행한 투자 시뮬레이션 결과를 캐시에서 조회합니다.
   * </p>
   *
   * @param userId 사용자 ID
   * @return 캐시된 투자 결과 (존재하지 않으면 Empty)
   */
  Optional<InvestmentResponse> getCachedInvestmentResult(String userId);

  /**
   * 사용자의 캐시된 투자 백테스트 결과 Entity를 조회합니다.
   * <p>
   * Trade 서비스에서 사용하기 위한 Entity 형식의 결과를 반환합니다.
   * </p>
   *
   * @param userId 사용자 ID
   * @return 캐시된 투자 결과 Entity (존재하지 않으면 Empty)
   */
  Optional<InvestmentBacktestResult> getCachedInvestmentResultEntity(String userId);
}
