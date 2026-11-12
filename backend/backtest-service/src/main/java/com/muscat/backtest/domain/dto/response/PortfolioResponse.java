package com.muscat.backtest.domain.dto.response;

import com.muscat.backtest.common.enums.type.PeriodEnd;
import com.muscat.backtest.common.enums.type.PortfolioMethod;
import com.muscat.backtest.common.enums.type.PortfolioRebalance;
import com.muscat.backtest.common.enums.type.PortfolioStrategyType;
import com.muscat.backtest.domain.model.StrategyTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

// 여러 종목을 현금 하나로 사고 평가한 포트폴리오 백테스트 결과
@Schema(description = "포트폴리오 백테스트 결과")
@Builder
public record PortfolioResponse(
  @Schema(description = "기간 안 첫 거래일 (줄들의 일봉 날짜 가운데 가장 이른 날)", example = "2020-01-02")
  LocalDate startDate,
  @Schema(description = "평가일 (종료일, 오늘 뒤면 오늘)", example = "2024-12-31")
  LocalDate endDate,
  @Schema(description = "매수 방식 (줄 전략이 없는 줄의 방식)", example = "LUMP_SUM")
  PortfolioMethod method,
  @Schema(description = "리밸런싱 주기", example = "NONE")
  PortfolioRebalance rebalance,
  @Schema(description = "기간 끝 처리", example = "HOLD")
  PeriodEnd periodEnd,
  @Schema(description = "넣은 돈 (새로 넣은 원화 합, KRW)", example = "10000000")
  BigDecimal totalInvestedKrw,
  @Schema(description = "총 자산 (주식 평가 + 현금, 평가일 환율, KRW)", example = "14250000")
  BigDecimal totalAssetKrw,
  @Schema(description = "주식 평가 (종목 줄 지금 가치 합, KRW)", example = "14100000")
  BigDecimal stockValueKrw,
  @Schema(description = "현금 (판 돈 · 현금 배당에서 쓰고 남은 돈, 낸 양도세 뺌, KRW)", example = "150000")
  BigDecimal cashKrw,
  @Schema(description = "현금 (USD)", example = "108.30")
  BigDecimal cashUsd,
  @Schema(description = "총 수익 (총 자산 - 넣은 돈, KRW)", example = "4250000")
  BigDecimal totalReturnKrw,
  @Schema(description = "총 수익률 (%, 넣은 돈이 0 이면 0)", example = "42.50")
  BigDecimal totalReturnPercent,
  @Schema(description = "실현 손익 (판 거래의 원화 실현 이익 합, 양도세 계산과 같은 값, KRW)", example = "0")
  BigDecimal realizedProfitKrw,
  @Schema(description = "종목 이익 밖의 이익 (총 수익 - 종목 이익 합, 현금의 환율 차 · 낸 양도세, KRW)", example = "32000")
  BigDecimal otherProfitKrw,
  @Schema(description = "평가일 환율", example = "1385.20")
  BigDecimal currentFxRate,
  @Schema(description = "매수 거래 수 (배당 재투자 · 양도세 납부 빼고)", example = "2")
  int buyCount,
  @Schema(description = "매도 거래 수", example = "0")
  int sellCount,
  @Schema(description = "원화 기준 수익 · 위험 지표 (넣은 돈을 뺀 시간가중 수익률, 산 것이 없으면 null)")
  PerformanceMetrics metricsKrw,
  @Schema(description = "달러 기준 수익 · 위험 지표 (넣은 돈을 뺀 시간가중 수익률, 산 것이 없으면 null)")
  PerformanceMetrics metricsUsd,
  @Schema(description = "첫 매수일 ~ 평가일 합친 일별 평가 가치 (마지막 날은 총 자산, 산 것이 없으면 빈 목록)")
  List<DailyValue> dailyValues,
  @Schema(description = "종목 줄 (요청 순서)")
  List<Asset> assets,
  @Schema(description = "거래 내역 (거래일 순서, 종목 · 이유 포함, 양도세 납부 줄은 종목 없음)")
  List<StrategyTransaction> transactions,
  @Schema(description = "매매 통계 (사이클은 종목마다, 판 거래가 없으면 null)")
  TradeStats tradeStats,
  @Schema(description = "포트폴리오 전체 양도소득세 · 기간 안 납부와 세후 평가 (요청에서 끄면 null)")
  CapitalGainsTax capitalGainsTax,
  @Schema(description = "환율이 없어 기본 환율로 계산한 날 (산 날 · 배당 재투자일 · 평가일, 양도세를 켜면 판 날 · 낸 날)", example = "[]")
  List<LocalDate> defaultFxDates
) {

  // 종목 줄 하나의 넣은 돈 · 지금 가치 · 판 돈 · 배당 · 이익 · 기여 · 비중
  @Schema(description = "포트폴리오 종목 줄")
  @Builder
  public record Asset(
    @Schema(description = "종목 코드", example = "SPY")
    String symbol,
    @Schema(description = "줄 전략 (줄에 없으면 요청의 매수 방식)", example = "LUMP_SUM")
    PortfolioStrategyType strategyType,
    @Schema(description = "넣은 돈 (그 종목 매수 원가 합: 새 돈 원화 + 쓴 현금 × 그 날짜 환율, KRW)", example = "6000000")
    BigDecimal investedKrw,
    @Schema(description = "지금 가치 (평가일 보유 × 종가 × 평가일 환율, KRW)", example = "8650000")
    BigDecimal valueKrw,
    @Schema(description = "판 돈 (수수료 뺀 판 돈 × 판 날 환율 합, KRW)", example = "0")
    BigDecimal soldKrw,
    @Schema(description = "배당 (세후 배당 × 배당락일 환율 합, 재투자 포함, KRW)", example = "410000")
    BigDecimal dividendKrw,
    @Schema(description = "이익 (지금 가치 + 판 돈 + 배당 - 넣은 돈, KRW)", example = "3060000")
    BigDecimal profitKrw,
    @Schema(description = "수익률 (이익 ÷ 넣은 돈 × 100, 넣은 돈이 0 이면 null)", example = "51.00")
    BigDecimal returnPercent,
    @Schema(description = "기여 (이익 ÷ 총 수익 × 100, 총 수익이 0 이면 null)", example = "72.00")
    BigDecimal contributionPercent,
    @Schema(description = "처음 비중 (줄 금액 ÷ 줄 금액 합 × 100)", example = "60.00")
    BigDecimal startWeightPercent,
    @Schema(description = "지금 비중 (지금 가치 ÷ 총 자산 × 100, 총 자산이 0 이면 0)", example = "60.70")
    BigDecimal endWeightPercent,
    @Schema(description = "평가일 보유 주식 수", example = "20.512345")
    BigDecimal shares,
    @Schema(description = "실현 손익 (그 종목 판 거래의 원화 실현 이익 합, KRW)", example = "0")
    BigDecimal realizedProfitKrw,
    @Schema(description = "매수 거래 수 (배당 재투자 빼고)", example = "1")
    int buyCount,
    @Schema(description = "매도 거래 수", example = "0")
    int sellCount
  ) {}
}
