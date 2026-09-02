package com.muscat.backtest.domain.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// 최적 매수 타이밍 분석 요청 DTO
// 지정된 기간 내에서 목표 수익률 이상을 달성할 수 있는 매수 타이밍을 분석
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class OptimalTimingRequest {

  private String userId;

  // 종목 심볼
  private String symbol;

  // 분석 시작일
  private LocalDate startDate;

  // 분석 종료일
  private LocalDate endDate;

  // 투자 금액 (KRW)
  private BigDecimal investmentAmount;

  // 목표 수익률 (%) - 이 수익률 이상인 매수 타이밍만 반환
  private BigDecimal targetReturnPercent;
}
