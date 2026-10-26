package com.muscat.backtest.domain.dto.response;

import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.domain.model.SkippedRound;
import com.muscat.backtest.domain.model.StrategyTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Schema(description = "투자 전략 백테스트 결과")
@Data
@Builder
public class StrategyResponse {

  @Schema(description = "종목 코드", example = "AAPL")
  private String symbol;
  @Schema(description = "전략 시작일", example = "2024-01-01")
  private LocalDate startDate;
  @Schema(description = "전략 종료일", example = "2024-09-18")
  private LocalDate endDate;
  @Schema(description = "전략 유형", example = "DCA")
  private StrategyType strategyType;

  // 투자 실행 내역
  @Schema(description = "전략 거래 내역 목록")
  private List<StrategyTransaction> transactions;
  @Schema(description = "총 거래 횟수", example = "9")
  private Integer totalTransactions;

  // 투자 금액 요약
  @Schema(description = "총 투자금액 (USD)", example = "7518.50")
  private BigDecimal totalInvested;        // 총 투자금액
  @Schema(description = "총 보유 주수", example = "375.2")
  private BigDecimal totalShares;          // 총 보유주식수
  @Schema(description = "평균 매수 단가 (USD)", example = "200.35")
  private BigDecimal averagePrice;         // 평균단가

  // 현재 가치
  @Schema(description = "현재 주가 (USD)", example = "238.15")
  private BigDecimal currentPrice;
  @Schema(description = "현재 자산 가치 (USD)", example = "89,352.08")
  private BigDecimal currentValue;
  @Schema(description = "현재 주식 가치 (KRW)", example = "118,025,128")
  private BigDecimal currentValueKrw;
  @Schema(description = "총 자산 (주식+잔액+배당, KRW)", example = "118,150,558")
  private BigDecimal totalAssetKrw;           // 총 자산 = 주식가치 + 잔액 + 배당금

  // 수익률 분석
  @Schema(description = "총 수익률 (%)", example = "25.05")
  private BigDecimal totalReturnPercent;   // 총 수익률
  @Schema(description = "총 수익 (KRW)", example = "24,861,238")
  private BigDecimal totalReturnKrw;       // 총 수익 (KRW)

  // 환율 분석
  @Schema(description = "평균 환율", example = "1285.75")
  private BigDecimal averageFxRate;        // 평균 환율
  @Schema(description = "현재 환율", example = "1320.75")
  private BigDecimal currentFxRate;        // 현재 환율
  @Schema(description = "환차익 (KRW)", example = "2,625,128")
  private BigDecimal fxReturn;             // 환차익
  @Schema(description = "환차익 수익률 (%)", example = "2.72")
  private BigDecimal fxReturnPercent;      // 환차익 수익률

  // 배당금 (추후 확장)
  @Schema(description = "총 배당금 (USD)", example = "125.50")
  private BigDecimal totalDividends;
  @Schema(description = "배당 수익률 (%)", example = "1.67")
  private BigDecimal dividendYield;

  // 미매수 회차
  @Schema(description = "미매수 회차(계획된 매수일 · 이유)")
  private List<SkippedRound> skippedRounds;

}