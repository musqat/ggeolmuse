package com.muscat.backtest.domain.dto.response;

import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.domain.model.SkippedRound;
import com.muscat.backtest.domain.model.SkippedSpan;
import com.muscat.backtest.domain.model.StrategyTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Schema(description = "투자 전략 백테스트 결과")
@Data
@Builder(toBuilder = true)
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
  @Schema(description = "누적 매수액 (넣은 원화의 합, KRW)", example = "9,800,000")
  private BigDecimal totalInvested;        // 누적 매수액
  @Schema(description = "총 보유 주수", example = "375.2")
  private BigDecimal totalShares;          // 총 보유주식수
  @Schema(description = "보유분 평균 매수 단가 (USD, 전량 매도로 끝나면 null)", example = "200.35")
  private BigDecimal averagePrice;         // 평균단가

  // 현재 가치
  @Schema(description = "현재 주가 (USD)", example = "238.15")
  private BigDecimal currentPrice;
  @Schema(description = "현재 주식 가치 (USD, 보유가 없으면 0)", example = "89,352.08")
  private BigDecimal currentValue;
  @Schema(description = "현재 주식 가치 (KRW)", example = "118,025,128")
  private BigDecimal currentValueKrw;
  @Schema(description = "총 자산 (주식+잔액+배당, KRW)", example = "118,150,558")
  private BigDecimal totalAssetKrw;           // 총 자산 = 주식가치 + 잔액 + 배당금
  @Schema(description = "매도로 남은 현금 (USD, 매도가 없으면 0)", example = "1100.00")
  private BigDecimal cashBalance;
  @Schema(description = "매도 실현 손익 합 (USD, 매도가 없으면 0)", example = "100.00")
  private BigDecimal realizedProfit;

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

  // 배당금
  @Schema(description = "총 배당금 (USD)", example = "125.50")
  private BigDecimal totalDividends;
  @Schema(description = "배당 수익률 (%)", example = "1.67")
  private BigDecimal dividendYield;

  @Schema(description = "예상 양도소득세 · 세후 평가 (요청에서 끄면 null)")
  private CapitalGainsTax capitalGainsTax;

  // 미매수 회차
  @Schema(description = "미매수 회차(계획된 매수일 · 이유)")
  private List<SkippedRound> skippedRounds;
  @Schema(description = "예산 부족으로 사지 못한 구간(처음 날짜 · 회차 수 · 이유, 없으면 null)")
  private SkippedSpan skippedSpan;
  @Schema(description = "환율이 없어 기본 환율로 계산한 날(산 날 · 배당 재투자일 · 평가일)", example = "[\"2013-06-03\"]")
  @Builder.Default
  private List<LocalDate> defaultFxDates = List.of();

  // ===== 수익 · 위험 지표 =====
  @Schema(description = "원화 기준 수익 · 위험 지표(입금을 뺀 시간가중 수익률)")
  private PerformanceMetrics metricsKrw;
  @Schema(description = "달러 기준 수익 · 위험 지표(입금을 뺀 시간가중 수익률)")
  private PerformanceMetrics metricsUsd;
  @Schema(description = "첫 매수일 ~ 평가일 일별 평가 가치(마지막 날은 총자산)")
  @Builder.Default
  private List<DailyValue> dailyValues = List.of();

}