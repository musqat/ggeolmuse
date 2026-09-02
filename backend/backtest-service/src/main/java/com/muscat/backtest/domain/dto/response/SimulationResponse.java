package com.muscat.backtest.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "투자 시뮬레이션 결과")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationResponse {

  @Schema(description = "종목 코드", example = "AAPL")
  private String symbol;
  @Schema(description = "매수일", example = "2024-01-15")
  private LocalDate purchaseDate;
  @Schema(description = "현재 기준일", example = "2024-09-18")
  private LocalDate currentDate;

  // 투자 정보
  @Schema(description = "투자 금액", example = "1000000.00")
  private BigDecimal investmentAmount;
  @Schema(description = "매수 단가 (USD)", example = "180.25")
  private BigDecimal purchasePrice;
  @Schema(description = "매수 주수", example = "41.7")
  private BigDecimal shares;

  // 현재 가치
  @Schema(description = "현재 주가 (USD)", example = "238.15")
  private BigDecimal currentPrice;
  @Schema(description = "현재 자산 가치 (USD)", example = "9930.86")
  private BigDecimal currentValue;

  // 수익률 분석
  @Schema(description = "주식 수익 (USD)", example = "2412.35")
  private BigDecimal stockReturn;
  @Schema(description = "주식 수익률 (%)", example = "32.08")
  private BigDecimal stockReturnPercent;

  // 환율 분석
  @Schema(description = "매수 시 환율", example = "1280.50")
  private BigDecimal purchaseFxRate;
  @Schema(description = "현재 환율", example = "1320.75")
  private BigDecimal currentFxRate;
  @Schema(description = "환차익 (KRW)", example = "320,842")
  private BigDecimal fxReturn;
  @Schema(description = "환차익 수익률 (%)", example = "3.14")
  private BigDecimal fxReturnPercent;

  // 배당금
  @Schema(description = "총 배당금 (USD)", example = "25.50")
  private BigDecimal totalDividends;
  @Schema(description = "배당 수익률 (%)", example = "0.34")
  private BigDecimal dividendYield;

  // 수수료 정보
  @Schema(description = "매매 수수료 (USD)", example = "18.82")
  private BigDecimal tradingFee; // 매매수수룼 (USD)
  @Schema(description = "매수 후 잔액 (USD)", example = "498.17")
  private BigDecimal remainingCash; // 매수 후 잔액 (USD)

  // 총 수익
  @Schema(description = "총 수익 (USD)", example = "2437.85")
  private BigDecimal totalReturn;
  @Schema(description = "총 수익률 (%)", example = "32.42")
  private BigDecimal totalReturnPercent;

  // 현재 KRW 환산 가치
  @Schema(description = "현재 주식 가치 (KRW)", example = "13,118,442")
  private BigDecimal currentValueKrw;
  @Schema(description = "잔액 (KRW)", example = "657,902")
  private BigDecimal remainingCashKrw; // 잔액 KRW 환산
  @Schema(description = "총 자산 (주식+잔액+배당, KRW)", example = "13,776,344")
  private BigDecimal totalAssetKrw; // 총 자산 = 주식가치 + 잔액 + 배당금
  @Schema(description = "총 수익 (KRW)", example = "3,218,442")
  private BigDecimal totalReturnKrw;

  // 성과 요약
  @Schema(description = "성과 요약 메시지", example = "8개월 간 32.42% 수익 달성")
  private String performanceSummary;

  // 최적 타이밍 정보
  @Schema(description = "최적 매수일", example = "2024-01-10")
  private LocalDate optimalBuyDate;
  @Schema(description = "최적 매수 가격 (USD)", example = "175.20")
  private BigDecimal optimalBuyPrice;
  @Schema(description = "최적 매도일", example = "2024-09-15")
  private LocalDate optimalSellDate;
  @Schema(description = "최적 매도 가격 (USD)", example = "245.80")
  private BigDecimal optimalSellPrice;
  @Schema(description = "최적 수익률 (%)", example = "40.31")
  private BigDecimal optimalReturnPercent;

  // 재투자 정보
  @Schema(description = "재투자된 배당금 (USD)", example = "12.50")
  private BigDecimal dividendsReinvested;
  @Schema(description = "배당 재투자 날짜 목록", example = "[\"2024-05-15\", \"2024-08-15\", \"2024-11-15\"]")
  private List<LocalDate> dividendReinvestDates;
}