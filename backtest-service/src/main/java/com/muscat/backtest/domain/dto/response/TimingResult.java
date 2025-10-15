package com.muscat.backtest.domain.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 개별 타이밍 분석 결과
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimingResult {

  // 매수일
  private LocalDate purchaseDate;

  // 매수 시 주가 (USD)
  private BigDecimal purchasePrice;

  // 매수 시 환율
  private BigDecimal purchaseFxRate;

  // 매수 주식 수
  private BigDecimal shares;

  // 현재 가치 (USD)
  private BigDecimal currentValue;

  // 현재 가치 (KRW)
  private BigDecimal currentValueKrw;

  // 총 수익 (KRW)
  private BigDecimal totalReturn;

  // 총 수익률 (%)
  private BigDecimal totalReturnPercent;

  // 주식 수익 (USD)
  private BigDecimal stockReturn;

  // 주식 수익률 (%)
  private BigDecimal stockReturnPercent;

  // 환차익 (USD)
  private BigDecimal fxReturn;

  // 환차익률 (%)
  private BigDecimal fxReturnPercent;

  // 순위
  private Integer rank;
}
