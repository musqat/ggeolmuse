package com.muscat.backtest.domain.service;

import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.OptimalTimingRequest;
import com.muscat.backtest.domain.dto.request.StrategyComparisonRequest;
import com.muscat.backtest.domain.dto.request.SymbolComparisonRequest;
import com.muscat.backtest.domain.dto.request.TimingComparisonRequest;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.OptimalTimingResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;

// 백테스트 전략(DCA · 조건부) 실행과 종목 · 전략 · 타이밍 비교 분석
public interface BacktestAnalysisService {

  // 매월 정한 날 정한 금액을 삼
  StrategyResponse runDcaStrategy(DcaStrategyRequest request);

  // 주가가 정한 비율 이상 떨어질 때마다 삼
  StrategyResponse runConditionalStrategy(ConditionalStrategyRequest request);

  // 같은 기간 · 조건으로 여러 종목을 견줌
  ComparisonResponse compareSymbols(SymbolComparisonRequest request);

  // 한 종목에 여러 전략을 견줌
  ComparisonResponse compareStrategies(StrategyComparisonRequest request);

  // 한 종목의 여러 매수일을 견줌
  ComparisonResponse compareTiming(TimingComparisonRequest request);

  // 목표 수익률 이상이었던 매수일을 수익률 순으로
  OptimalTimingResponse analyzeOptimalTiming(OptimalTimingRequest request);

}