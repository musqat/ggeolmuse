package com.muscat.backtest.domain.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SimulationResponse {

  private String symbol;
  private LocalDate purchaseDate;
  private LocalDate currentDate;

  // 투자 정보
  private BigDecimal investmentAmount;
  private BigDecimal purchasePrice;
  private BigDecimal shares;

  // 현재 가치
  private BigDecimal currentPrice;
  private BigDecimal currentValue;

  // 수익률 분석
  private BigDecimal stockReturn;
  private BigDecimal stockReturnPercent;

  // 환율 분석
  private BigDecimal purchaseFxRate;
  private BigDecimal currentFxRate;
  private BigDecimal fxReturn;
  private BigDecimal fxReturnPercent;

  // 배당금
  private BigDecimal totalDividends;
  private BigDecimal dividendYield;

  // 수수료 정보
  private BigDecimal tradingFee; // 매매수수료 (USD)
  private BigDecimal remainingCash; // 매수 후 잔액 (USD)

  // 총 수익
  private BigDecimal totalReturn;
  private BigDecimal totalReturnPercent;

  // 현재 KRW 환산 가치
  private BigDecimal currentValueKrw;
  private BigDecimal totalReturnKrw;
  private BigDecimal remainingCashKrw; // 잔액 KRW 환산

  // 성과 요약
  private String performanceSummary;
}