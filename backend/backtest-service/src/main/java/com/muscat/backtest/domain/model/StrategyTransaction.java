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
  private String trigger;               // 매수 트리거 (적립식: "월정액" · "매주 정액" · "매달 2주", 조건부: "5%하락")
  private BigDecimal fee;               // 매매 수수료 (USD, 배당 재투자는 null)
  private BigDecimal proceeds;          // 매도로 받은 돈 (USD, 수수료 뺀 값, 매수는 null)
  private BigDecimal cashUsed;          // 매수에 쓴 판 돈 (USD, 새 돈만 넣었으면 null)
  private BigDecimal realizedProfit;    // 매도 실현 손익 (USD, 매수는 null)

  // 환율이 없어 기본 환율로 산 날(아니면 null)
  @JsonIgnore
  private LocalDate defaultFxDate;

  // 배당 대상 판정용 날짜(주말에 계획해 월요일에 샀으면 월요일)
  public LocalDate tradedOn() {
    return actualDate != null ? actualDate : date;
  }

  // 매도 거래(수량이 음수)
  @JsonIgnore
  public boolean isSell() {
    return shares != null && shares.signum() < 0;
  }

  // 그 날짜 계좌 현금 변화(USD, 매도로 받은 돈 - 매수에 쓴 판 돈)
  @JsonIgnore
  public BigDecimal cashChange() {
    BigDecimal in = proceeds != null ? proceeds : BigDecimal.ZERO;
    return cashUsed != null ? in.subtract(cashUsed) : in;
  }
}