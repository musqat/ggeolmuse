package com.muscat.backtest.domain.service;

import com.muscat.backtest.domain.dto.request.InvestmentRequest;
import com.muscat.backtest.domain.dto.response.InvestmentResponse;
import com.muscat.backtest.domain.entity.InvestmentBacktestResult;
import java.util.Optional;

// 보유 종목(holdings) 기반 투자 백테스트: 매수 시점부터 지금까지 성과를 계산해 저장
public interface InvestmentBacktestService {

  InvestmentResponse executeInvestment(InvestmentRequest request, String authorization);

  Optional<InvestmentBacktestResult> getCachedInvestmentResultEntity(String userId);
}
