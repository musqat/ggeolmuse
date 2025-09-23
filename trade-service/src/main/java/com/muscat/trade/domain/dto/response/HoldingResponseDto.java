package com.muscat.trade.domain.dto.response;

import com.muscat.trade.common.constants.TradeConstants;
import com.muscat.trade.domain.entity.Holdings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "보유 종목 정보")
@Getter
@Builder
public class HoldingResponseDto {

  @Schema(description = "보유 ID", example = "HOLD_20240918_001")
  private String holdingId; // 보유 ID

  @Schema(description = "계좌 ID", example = "12345678")
  private String accountId; // 계좌 ID

  @Schema(description = "종목 심볼", example = "AAPL")
  private String symbol; // 종목 심볼

  @Schema(description = "보유 수량", example = "100.00")
  private BigDecimal totalQuantity; // 보유 수량

  @Schema(description = "평균 매수가", example = "150.50")
  private BigDecimal avgPurchasePrice; // 평균 매수가

  @Schema(description = "총 투자금액", example = "15050.00")
  private BigDecimal totalInvestedAmount; // 총 투자금액

  @Schema(description = "생성일시", example = "2024-09-18T10:30:00")
  private LocalDateTime createdAt; // 생성일시

  // 추가 계산 필드들 (현재가 정보가 있을 때)
  @Schema(description = "현재가", example = "165.75")
  private BigDecimal currentPrice; // 현재가

  @Schema(description = "현재 평가금액", example = "16575.00")
  private BigDecimal currentValue; // 현재 평가금액

  @Schema(description = "평가손익", example = "1525.00")
  private BigDecimal unrealizedPnL; // 평가손익

  @Schema(description = "수익률 (%)", example = "10.15")
  private BigDecimal returnRate; // 수익률 (%)

  // Entity to DTO 변환
  public static HoldingResponseDto from(Holdings holding) {
    return HoldingResponseDto.builder()
        .holdingId(holding.getHoldingId())
        .accountId(String.valueOf(holding.getAccountId()))
        .symbol(holding.getSymbol())
        .totalQuantity(holding.getTotalQuantity())
        .avgPurchasePrice(holding.getAvgPurchasePrice())
        .totalInvestedAmount(holding.getTotalInvestedAmount())
        .createdAt(holding.getCreatedAt())
        .build();
  }

  // 현재가 정보 포함한 DTO 생성
  public static HoldingResponseDto fromWithCurrentPrice(Holdings holding, BigDecimal currentPrice) {
    BigDecimal currentValue = holding.getTotalQuantity().multiply(currentPrice);
    BigDecimal bookValue = holding.getTotalQuantity().multiply(holding.getAvgPurchasePrice());
    BigDecimal unrealizedPnL = currentValue.subtract(bookValue);

    BigDecimal returnRate = BigDecimal.ZERO;
    if (holding.getAvgPurchasePrice().compareTo(BigDecimal.ZERO) > 0) {
      returnRate = currentPrice.subtract(holding.getAvgPurchasePrice())
          .divide(holding.getAvgPurchasePrice(), 4, RoundingMode.HALF_UP)
          .multiply(TradeConstants.PERCENTAGE_MULTIPLIER);
    }

    return HoldingResponseDto.builder()
        .holdingId(holding.getHoldingId())
        .accountId(String.valueOf(holding.getAccountId()))
        .symbol(holding.getSymbol())
        .totalQuantity(holding.getTotalQuantity())
        .avgPurchasePrice(holding.getAvgPurchasePrice())
        .totalInvestedAmount(holding.getTotalInvestedAmount())
        .createdAt(holding.getCreatedAt())
        .currentPrice(currentPrice)
        .currentValue(currentValue)
        .unrealizedPnL(unrealizedPnL)
        .returnRate(returnRate)
        .build();
  }
}