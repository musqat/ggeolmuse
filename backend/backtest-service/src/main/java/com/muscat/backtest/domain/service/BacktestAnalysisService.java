package com.muscat.backtest.domain.service;

import com.muscat.backtest.domain.dto.request.ConditionalStrategyRequest;
import com.muscat.backtest.domain.dto.request.DcaStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaBandStrategyRequest;
import com.muscat.backtest.domain.dto.request.MaCrossStrategyRequest;
import com.muscat.backtest.domain.dto.request.PortfolioRequest;
import com.muscat.backtest.domain.dto.request.StrategyComparisonRequest;
import com.muscat.backtest.domain.dto.request.SymbolComparisonRequest;
import com.muscat.backtest.domain.dto.response.ComparisonResponse;
import com.muscat.backtest.domain.dto.response.PortfolioResponse;
import com.muscat.backtest.domain.dto.response.StrategyResponse;

// 백테스트 전략 실행과 종목 · 전략 비교 분석
public interface BacktestAnalysisService {

  // 매일 · 매주 · 매달 정한 금액이나 주식 수를 삼
  StrategyResponse runDcaStrategy(DcaStrategyRequest request);

  // 전 거래일 종가보다 장중에 정한 비율 이상 떨어진(또는 오른) 날마다 삼
  StrategyResponse runConditionalStrategy(ConditionalStrategyRequest request);

  // 짧은 이동평균이 긴 이동평균을 아래에서 위로 넘는 날 가진 돈 전부로 사고 위에서 아래로 넘는 날 전량 팜
  StrategyResponse runMaCrossStrategy(MaCrossStrategyRequest request);

  // 수정 종가가 평균 위 선보다 높은 날 가진 돈 전부로 사고 아래 선보다 낮은 날 전량 팜
  StrategyResponse runMaBandStrategy(MaBandStrategyRequest request);

  // 여러 종목을 현금 하나로 줄마다 한 번에 또는 적립식으로 사고 줄마다 익절 · 손절, 정한 주기마다 처음 비중으로 리밸런싱하며 날마다 합쳐 평가함
  PortfolioResponse runPortfolio(PortfolioRequest request);

  // 같은 기간 · 조건으로 여러 종목을 견줌
  ComparisonResponse compareSymbols(SymbolComparisonRequest request);

  // 한 종목에 여러 전략을 같은 원금으로 견줌
  ComparisonResponse compareStrategies(StrategyComparisonRequest request);

}