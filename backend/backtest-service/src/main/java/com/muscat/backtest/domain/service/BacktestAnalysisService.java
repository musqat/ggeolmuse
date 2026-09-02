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

/**
 * 백테스팅 전략 실행 및 비교 분석을 통합한 서비스 인터페이스
 * <p>
 * 다양한 투자 전략(DCA, 조건부 매수)을 실행하고,
 * 종목, 전략, 타이밍을 비교 분석하는 기능을 제공합니다.
 * </p>
 */
public interface BacktestAnalysisService {

  /**
   * DCA(적립식) 투자 전략을 실행하고 백테스팅 결과를 반환합니다.
   * <p>
   * 매월 지정된 날짜에 정해진 금액을 자동으로 투자하는 전략을 시뮬레이션합니다.
   * 배당금 재투자, 원천징수세 적용 등을 포함합니다.
   * </p>
   *
   * @param request DCA 전략 요청 정보 (종목, 월투자액, 매수일 등)
   * @return 전략 실행 결과 (수익률, 거래 내역, 배당금 등)
   */
  StrategyResponse runDcaStrategy(DcaStrategyRequest request);

  /**
   * 조건부 매수 전략을 실행하고 백테스팅 결과를 반환합니다.
   * <p>
   * 주가가 특정 비율 이상 하락할 때마다 자동으로 매수하는 전략을 시뮬레이션합니다.
   * </p>
   *
   * @param request 조건부 전략 요청 정보 (종목, 하락률, 최대 매수 횟수 등)
   * @return 전략 실행 결과
   */
  StrategyResponse runConditionalStrategy(ConditionalStrategyRequest request);

  /**
   * 여러 종목의 동일 기간 투자 성과를 비교 분석합니다.
   * <p>
   * 같은 기간, 같은 조건에서 여러 종목에 투자했을 때의 성과를 비교합니다.
   * </p>
   *
   * @param request 종목 비교 요청 정보 (종목 리스트, 기간, 투자금액 등)
   * @return 종목별 비교 분석 결과 (수익률 순위, 통계 등)
   */
  ComparisonResponse compareSymbols(SymbolComparisonRequest request);

  /**
   * 동일 종목에 대한 다양한 투자 전략의 성과를 비교 분석합니다.
   * <p>
   * 한 종목에 여러 전략(DCA, 조건부 매수, 일시불 등)을 적용했을 때의 성과를 비교합니다.
   * </p>
   *
   * @param request 전략 비교 요청 정보 (종목, 전략 리스트, 파라미터 등)
   * @return 전략별 비교 분석 결과
   */
  ComparisonResponse compareStrategies(StrategyComparisonRequest request);

  /**
   * 동일 종목의 서로 다른 매수 시점들의 성과를 비교 분석합니다.
   * <p>
   * 같은 종목에 여러 날짜에 투자했을 때의 성과를 비교하여 최적 타이밍을 파악합니다.
   * </p>
   *
   * @param request 타이밍 비교 요청 정보 (종목, 매수일 리스트, 투자금액 등)
   * @return 타이밍별 비교 분석 결과
   */
  ComparisonResponse compareTiming(TimingComparisonRequest request);

  /**
   * 목표 수익률 이상을 달성할 수 있는 최적 매수 타이밍을 분석합니다.
   * <p>
   * 과거 데이터를 기반으로 목표 수익률을 달성할 수 있었던 매수 시점들을 찾아
   * 수익률 순으로 정렬하여 반환합니다.
   * </p>
   *
   * @param request 최적 타이밍 분석 요청 정보 (종목, 목표 수익률, 분석 기간 등)
   * @return 최적 매수 타이밍 분석 결과 (달성 가능 일수, 최고 수익률 등)
   */
  OptimalTimingResponse analyzeOptimalTiming(OptimalTimingRequest request);

}