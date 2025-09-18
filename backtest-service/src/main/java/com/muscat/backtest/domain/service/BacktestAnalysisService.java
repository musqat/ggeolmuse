package com.muscat.backtest.domain.service;

import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.StrategyComparisonRequest;
import com.muscat.backtest.domain.dto.request.SymbolComparisonRequest;
import com.muscat.backtest.domain.dto.request.TimingComparisonRequest;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;

// 백테스팅 전략 실행 및 비교 분석을 통합한 서비스 인터페이스
public interface BacktestAnalysisService {

  // DCA(적립식) 투자 전략을 실행하고 백테스팅 결과를 반환합니다
  StrategyResponse runDcaStrategy(DcaStrategyRequest request);

  // 조건부 매수 전략을 실행하고 백테스팅 결과를 반환합니다
  StrategyResponse runConditionalStrategy(ConditionalStrategyRequest request);

  // 여러 종목의 동일 기간 투자 성과를 비교 분석합니다
  ComparisonResponse compareSymbols(SymbolComparisonRequest request);

  // 동일 종목에 대한 다양한 투자 전략의 성과를 비교 분석합니다
  ComparisonResponse compareStrategies(StrategyComparisonRequest request);

  // 동일 종목의 서로 다른 매수 시점들의 성과를 비교 분석합니다
  ComparisonResponse compareTiming(TimingComparisonRequest request);

}