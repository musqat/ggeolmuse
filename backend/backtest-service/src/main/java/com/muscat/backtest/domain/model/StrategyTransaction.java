package com.muscat.backtest.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
  private BigDecimal fee;               // 매매 수수료 (USD, 배당 재투자는 null)

  // 환율이 없어 기본 환율로 산 날(아니면 null)
  @JsonIgnore
  private LocalDate defaultFxDate;

  // 배당 대상 판정용 날짜(주말에 계획해 월요일에 샀으면 월요일)
  public LocalDate tradedOn() {
    return actualDate != null ? actualDate : date;
  }
}