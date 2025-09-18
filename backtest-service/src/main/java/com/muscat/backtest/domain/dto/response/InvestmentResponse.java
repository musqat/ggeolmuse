package com.muscat.backtest.domain.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvestmentResponse {

  // 시뮬레이션 결과
  private SimulationResponse simulation;

  // 실거래 정보
  private String holdingId;
  private String tradeId;
  private String symbol;
  private LocalDate purchaseDate;
  private BigDecimal investmentAmount;

  // 매수 정보
  private BigDecimal purchasePrice;
  private BigDecimal shares;
  private BigDecimal totalCost;

  // 투자 상태
  private String status;          // SUCCESS, FAILED
  private String message;

  // 포트폴리오 연동
  private boolean portfolioCreated;
  private String portfolioStatus;
}