package com.muscat.backtest.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

// 파는 전략의 사이클 승률 · 평균 익절 · 손절 · 손익비 · 평균 보유일 · 1년 사이클 수
@Schema(description = "매매 통계(사이클 = 보유 0 에서 산 날 ~ 전부 판 날, 기간 끝 매도 포함)")
public record TradeStats(
  @Schema(description = "매수 거래 수 (배당 재투자 빼고)", example = "8")
  int buyCount,
  @Schema(description = "매도 거래 수 (양도세 납부 빼고)", example = "7")
  int sellCount,
  @Schema(description = "끝난 사이클 수", example = "7")
  int cycleCount,
  @Schema(description = "평가일에 들고 있음(진행 중인 사이클)", example = "true")
  boolean openCycle,
  @Schema(description = "이긴 사이클 수 (사이클 실현 손익 합이 0 보다 큼)", example = "4")
  int winCount,
  @Schema(description = "진 사이클 수 (사이클 실현 손익 합이 0 이하)", example = "3")
  int lossCount,
  @Schema(description = "승률 (이긴 수 ÷ 끝난 사이클 수 × 100, 끝난 사이클이 없으면 null)", example = "57.14")
  BigDecimal winRatePercent,
  @Schema(description = "이긴 사이클 수익률 평균 (%, 사이클 실현 손익 합 ÷ 사이클 매수 원가, 이긴 사이클이 없으면 null)", example = "12.35")
  BigDecimal averageWinPercent,
  @Schema(description = "진 사이클 수익률 절댓값 평균 (%, 진 사이클이 없으면 null)", example = "4.10")
  BigDecimal averageLossPercent,
  @Schema(description = "손익비 (평균 익절 ÷ 평균 손절, 어느 쪽이 없거나 평균 손절이 0 이면 null)", example = "3.01")
  BigDecimal profitLossRatio,
  @Schema(description = "끝난 사이클 평균 보유 일수 (산 날 ~ 전부 판 날 달력 일수, 끝난 사이클이 없으면 null)", example = "45.3")
  BigDecimal averageHoldingDays,
  @Schema(description = "1년에 시작한 사이클 수 (끝난 것 + 진행 중 ÷ 시작일 ~ 평가일 햇수, 같은 날이면 null)", example = "1.85")
  BigDecimal cyclesPerYear,
  @Schema(description = "트레일링 스탑으로 판 거래 수", example = "0")
  int trailingStopCount,
  @Schema(description = "부분 익절로 판 거래 수", example = "0")
  int partialTakeProfitCount
) {}
