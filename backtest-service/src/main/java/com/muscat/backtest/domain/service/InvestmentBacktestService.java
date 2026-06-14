package com.muscat.backtest.domain.service;

import com.muscat.backtest.domain.dto.request.InvestmentRequest;
import com.muscat.backtest.domain.dto.response.InvestmentResponse;
import com.muscat.backtest.domain.entity.InvestmentBacktestResult;
import java.util.Optional;

/**
 * 보유종목(holdings) 기반 투자 백테스트 서비스
 * Trade 서비스의 사용자 보유종목을 가져와 매수 시점부터 현재까지의 성과를 계산하고 결과를 저장
 */
public interface InvestmentBacktestService {

  /**
   * 사용자 보유종목 기반 투자 백테스트를 실행
   *
   * @param request 투자 요청 정보
   * @param authorization 사용자 인증 토큰
   * @return 투자 백테스트 결과 (포트폴리오 성과)
   */
  InvestmentResponse executeInvestment(InvestmentRequest request, String authorization);

  /**
   * 캐시된 투자 백테스트 결과를 조회
   *
   * @param userId 사용자 ID
   * @return 캐시된 결과 (없으면 Empty)
   */
  Optional<InvestmentResponse> getCachedInvestmentResult(String userId);

  /**
   * 캐시된 투자 백테스트 결과 Entity를 조회(Trade 서비스)
   *
   * @param userId 사용자 ID
   * @return 캐시된 Entity (없으면 Empty)
   */
  Optional<InvestmentBacktestResult> getCachedInvestmentResultEntity(String userId);
}
