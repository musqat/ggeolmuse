package com.muscat.backtest.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

// 투자 전략 거래 내역
@Data
@Builder
public class StrategyTransaction {

  private LocalDate date;               // 계획된 매수일
  private LocalDate actualDate;         // 실제 거래일 (주말/휴장일 조정)
  private BigDecimal price;             // USD 주가
  private BigDecimal shares;            // 매수 주식수
  private BigDecimal amount;            // 투자금액 (KRW)
  private BigDecimal fxRate;            // 당시 환율
  private String trigger;               // 매수 트리거 (적립식: "월정액", 조건부: "5%하락")
}