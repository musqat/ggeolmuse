package com.muscat.backtest.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.muscat.backtest.common.enums.type.TradeReason;
import lombok.Builder;
import lombok.Data;

// 투자 전략 거래 내역
@Data
@Builder
public class StrategyTransaction {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String symbol;                // 거래 종목 (포트폴리오만)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private TradeReason reason;           // 거래 이유 (포트폴리오만)
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
  private BigDecimal taxPaid;           // 판 돈에서 낸 양도세 (USD, 양도세 납부가 아니면 null)

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

  // 양도세 납부 줄(수량 0, 사고팔기 아님)
  @JsonIgnore
  public boolean isTaxPayment() {
    return taxPaid != null;
  }

  // 그 날짜 계좌 현금 변화(USD, 매도로 받은 돈 - 매수에 쓴 판 돈 - 낸 양도세)
  @JsonIgnore
  public BigDecimal cashChange() {
    BigDecimal in = proceeds != null ? proceeds : BigDecimal.ZERO;
    BigDecimal out = cashUsed != null ? cashUsed : BigDecimal.ZERO;
    return in.subtract(out).subtract(taxPaid != null ? taxPaid : BigDecimal.ZERO);
  }

  // 매수 원가(USD, 넣은 원화를 그 날짜 환율로 바꾼 값 + 쓴 판 돈, 수수료 포함, 배당 재투자는 0)
  @JsonIgnore
  public BigDecimal costUsd() {
    BigDecimal deposit = amount.signum() > 0 ? amount.divide(fxRate, 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    return cashUsed != null ? deposit.add(cashUsed) : deposit;
  }
}