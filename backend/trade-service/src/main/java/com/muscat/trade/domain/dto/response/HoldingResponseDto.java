package com.muscat.trade.domain.dto.response;

import com.muscat.trade.common.constants.TradeConstants;
import com.muscat.trade.domain.entity.Holdings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Schema(description = "보유 종목 정보")
public record HoldingResponseDto(
  @Schema(description = "보유 ID", example = "1")
  Long id, // 보유 ID

  @Schema(description = "계좌 ID", example = "12345678")
  Long accountId, // 계좌 ID

  @Schema(description = "종목 심볼", example = "AAPL")
  String symbol, // 종목 심볼

  @Schema(description = "보유 수량", example = "100.00")
  BigDecimal totalQuantity, // 보유 수량

  @Schema(description = "평균 매수가", example = "150.50")
  BigDecimal avgPurchasePrice, // 평균 매수가

  @Schema(description = "총 투자금액", example = "15050.00")
  BigDecimal totalInvestedAmount, // 총 투자금액

  @Schema(description = "생성일시", example = "2024-09-18T10:30:00")
  LocalDateTime createdAt, // 생성일시

  // 추가 계산 필드들 (현재가 정보가 있을 때)
  @Schema(description = "현재가", example = "165.75")
  BigDecimal currentPrice, // 현재가

  @Schema(description = "현재 평가금액", example = "16575.00")
  BigDecimal currentValue, // 현재 평가금액

  @Schema(description = "평가손익", example = "1525.00")
  BigDecimal unrealizedPnL, // 평가손익

  @Schema(description = "수익률 (%)", example = "10.15")
  BigDecimal returnRate, // 수익률 (%)

  @Schema(description = "지금 들고 있는 주식이 받은 세후 배당 (USD)", example = "2.12")
  BigDecimal totalDividends,

  @Schema(description = "보유에 반영한 분할 비율 누적(분할 뒤 주식 수 ÷ 처음 주식 수, 반영한 분할이 없으면 빈 값)", example = "4")
  BigDecimal splitRatio
) {

  // Entity to DTO 변환
  public static HoldingResponseDto from(Holdings holding) {
    return new HoldingResponseDto(
      holding.getId(),
      holding.getAccountId(),
      holding.getSymbol(),
      holding.getTotalQuantity(),
      holding.getAvgPurchasePrice(),
      holding.getTotalInvestedAmount(),
      holding.getCreatedAt(),
      null,  // currentPrice
      null,  // currentValue
      null,  // unrealizedPnL
      null,  // returnRate
      null,
      null
    );
  }

  // 현재가 정보 포함한 DTO 생성(손익 · 수익률은 총 투자금액 기준)
  public static HoldingResponseDto of(Holdings holding, BigDecimal currentPrice) {
    BigDecimal currentValue = holding.getTotalQuantity().multiply(currentPrice);
    BigDecimal unrealizedPnL = currentValue.subtract(holding.getTotalInvestedAmount());
    BigDecimal returnRate = returnRate(unrealizedPnL, holding.getTotalInvestedAmount());

    return new HoldingResponseDto(
      holding.getId(),
      holding.getAccountId(),
      holding.getSymbol(),
      holding.getTotalQuantity(),
      holding.getAvgPurchasePrice(),
      holding.getTotalInvestedAmount(),
      holding.getCreatedAt(),
      currentPrice,
      currentValue,
      unrealizedPnL,
      returnRate,
      null,
      null
    );
  }

  // 손익 ÷ 투자금액(%), 투자금액이 0 이하면 0
  public static BigDecimal returnRate(BigDecimal unrealizedPnL, BigDecimal invested) {
    if (invested.signum() <= 0) {
      return BigDecimal.ZERO;
    }
    return unrealizedPnL.divide(invested, 4, RoundingMode.HALF_UP).multiply(TradeConstants.PERCENTAGE_MULTIPLIER);
  }

  public HoldingResponseDto withTotalDividends(BigDecimal dividends) {
    return new HoldingResponseDto(id, accountId, symbol, totalQuantity, avgPurchasePrice, totalInvestedAmount,
      createdAt, currentPrice, currentValue, unrealizedPnL, returnRate, dividends, splitRatio);
  }

  public HoldingResponseDto withSplitRatio(BigDecimal ratio) {
    return new HoldingResponseDto(id, accountId, symbol, totalQuantity, avgPurchasePrice, totalInvestedAmount,
      createdAt, currentPrice, currentValue, unrealizedPnL, returnRate, totalDividends, ratio);
  }
}
