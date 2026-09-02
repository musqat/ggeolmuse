package com.muscat.backtest.domain.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 최적 매수 타이밍 분석 응답 DTO
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimalTimingResponse {

  // 종목 심볼
  private String symbol;

  // 분석 기준일 (현재일)
  private LocalDate analysisDate;

  // 목표 수익률 (%)
  private BigDecimal targetReturnPercent;

  // 목표 수익률 이상 달성한 매수 타이밍 목록 (수익률 높은 순)
  private List<TimingResult> qualifyingTimings;

  // 최고 수익률 타이밍
  private TimingResult bestTiming;

  // 목표 달성 일수
  private Integer totalQualifyingDays;

  // 전체 분석 일수
  private Integer totalAnalyzedDays;

  // 투자 금액 (KRW)
  private BigDecimal investmentAmount;

  // 현재 주가
  private BigDecimal currentPrice;

  // 현재 환율
  private BigDecimal currentFxRate;
}
