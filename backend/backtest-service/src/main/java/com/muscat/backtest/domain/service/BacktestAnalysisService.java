package com.muscat.backtest.domain.service;

import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.StrategyComparisonRequest;
import com.muscat.backtest.domain.dto.request.SymbolComparisonRequest;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;

// 백테스트 전략(DCA · 조건부) 실행과 종목 · 전략 비교 분석
public interface BacktestAnalysisService {

  // 매일 · 매주 · 매달 정한 금액을 삼
  StrategyResponse runDcaStrategy(DcaStrategyRequest request);

  // 전 거래일 종가보다 장중에 정한 비율 이상 떨어진(또는 오른) 날마다 삼
  StrategyResponse runConditionalStrategy(ConditionalStrategyRequest request);

  // 같은 기간 · 조건으로 여러 종목을 견줌
  ComparisonResponse compareSymbols(SymbolComparisonRequest request);

  // 한 종목에 여러 전략을 견줌
  ComparisonResponse compareStrategies(StrategyComparisonRequest request);

}