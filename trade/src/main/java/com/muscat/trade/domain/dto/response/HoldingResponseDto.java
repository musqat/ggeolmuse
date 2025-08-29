package com.muscat.trade.domain.dto.response;

import com.muscat.trade.domain.entity.Holdings;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class HoldingResponseDto {

  private String holdingId; // 보유 ID
  private String accountId; // 계좌 ID
  private String symbol; // 종목 심볼
  private BigDecimal totalQuantity; // 보유 수량
  private BigDecimal avgPurchasePrice; // 평균 매수가
  private BigDecimal totalInvestedAmount; // 총 투자금액
  private BigDecimal totalDividends; // 누적 배당금
  private LocalDate lastDividendCalculated; // 마지막 배당 계산일
  private LocalDateTime createdAt; // 생성일시
  
  // 추가 계산 필드들 (현재가 정보가 있을 때)
  private BigDecimal currentPrice; // 현재가
  private BigDecimal currentValue; // 현재 평가금액
  private BigDecimal unrealizedPnL; // 평가손익
  private BigDecimal returnRate; // 수익률 (%)

  // Entity to DTO 변환
  public static HoldingResponseDto from(Holdings holding) {
    return HoldingResponseDto.builder()
        .holdingId(holding.getHoldingId())
        .accountId(holding.getAccountId())
        .symbol(holding.getSymbol())
        .totalQuantity(holding.getTotalQuantity())
        .avgPurchasePrice(holding.getAvgPurchasePrice())
        .totalInvestedAmount(holding.getTotalInvestedAmount())
        .totalDividends(holding.getTotalDividends())
        .lastDividendCalculated(holding.getLastDividendCalculated())
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
          .divide(holding.getAvgPurchasePrice(), 4, BigDecimal.ROUND_HALF_UP)
          .multiply(new BigDecimal("100"));
    }

    return HoldingResponseDto.builder()
        .holdingId(holding.getHoldingId())
        .accountId(holding.getAccountId())
        .symbol(holding.getSymbol())
        .totalQuantity(holding.getTotalQuantity())
        .avgPurchasePrice(holding.getAvgPurchasePrice())
        .totalInvestedAmount(holding.getTotalInvestedAmount())
        .totalDividends(holding.getTotalDividends())
        .lastDividendCalculated(holding.getLastDividendCalculated())
        .createdAt(holding.getCreatedAt())
        .currentPrice(currentPrice)
        .currentValue(currentValue)
        .unrealizedPnL(unrealizedPnL)
        .returnRate(returnRate)
        .build();
  }
}