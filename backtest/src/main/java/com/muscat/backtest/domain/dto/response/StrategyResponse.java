package com.muscat.backtest.domain.dto.response;

import com.muscat.backtest.common.enums.StrategyType;
import com.muscat.backtest.domain.model.StrategyTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StrategyResponse {

  private String symbol;
  private LocalDate startDate;
  private LocalDate endDate;
  private StrategyType strategyType;

  // 투자 실행 내역
  private List<StrategyTransaction> transactions;
  private Integer totalTransactions;

  // 투자 금액 요약
  private BigDecimal totalInvested;        // 총 투자금액
  private BigDecimal totalShares;          // 총 보유주식수
  private BigDecimal averagePrice;         // 평균단가

  // 현재 가치
  private BigDecimal currentPrice;
  private BigDecimal currentValue;
  private BigDecimal currentValueKrw;

  // 수익률 분석
  private BigDecimal totalReturn;          // 총 수익 (USD)
  private BigDecimal totalReturnPercent;   // 총 수익률
  private BigDecimal totalReturnKrw;       // 총 수익 (KRW)

  // 환율 분석
  private BigDecimal averageFxRate;        // 평균 환율
  private BigDecimal currentFxRate;        // 현재 환율
  private BigDecimal fxReturn;             // 환차익
  private BigDecimal fxReturnPercent;      // 환차익 수익률

  // 배당금 (추후 확장)
  private BigDecimal totalDividends;
  private BigDecimal dividendYield;

  // 전략별 특화 정보
  private String strategyDetails;          // 전략 상세 정보
  private String performanceSummary;       // 성과 요약

}