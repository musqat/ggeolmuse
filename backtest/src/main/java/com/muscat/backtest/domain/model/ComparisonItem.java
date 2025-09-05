package com.muscat.backtest.domain.model;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

// 비교 분석 개별 항목
@Data
@Builder
public class ComparisonItem {

  // 비교 대상 정보
  private String name;                   // 종목명 또는 전략명
  private String code;                   // 종목코드 또는 전략코드
  private String category;               // "SYMBOL", "STRATEGY", "TIMING"

  // 투자 정보
  private BigDecimal totalInvested;
  private BigDecimal totalShares;
  private BigDecimal averagePrice;

  // 현재 가치
  private BigDecimal currentValue;
  private BigDecimal currentValueKrw;

  // 수익률 분석
  private BigDecimal totalReturn;
  private BigDecimal totalReturnPercent;
  private BigDecimal totalReturnKrw;

  // 환율 분석
  private BigDecimal fxReturn;
  private BigDecimal fxReturnPercent;

  // 배당금
  private BigDecimal dividends;

  // 순위
  private Integer rank;                  // 수익률 기준 순위

  // 추가 메타데이터
  private Object additionalData;         // SimulationResponse, StrategyResponse 등
  private String performanceNote;        // 성과 특이사항

}