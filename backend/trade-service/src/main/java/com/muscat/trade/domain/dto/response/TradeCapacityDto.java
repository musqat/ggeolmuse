package com.muscat.trade.domain.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 거래 가능 수량 응답 DTO
 */
public record TradeCapacityDto(
  // 매수 관련
  BigDecimal maxBuyQuantity,        // 최대 매수 가능 수량
  BigDecimal availableBalance,      // 사용 가능 잔액 (USD)
  BigDecimal currentPrice,          // 현재 가격
  BigDecimal estimatedFee,          // 예상 수수료

  // 매도 관련
  BigDecimal maxSellQuantity,       // 최대 매도 가능 수량
  BigDecimal totalHoldings,         // 전체 보유 수량
  BigDecimal sellableQuantity,      // 매도 가능 수량 (날짜 기준)
  BigDecimal avgPurchasePrice,      // 평균 매수가

  // 평가 정보
  BigDecimal currentValue,          // 현재 평가액
  BigDecimal unrealizedPnL,         // 평가손익
  BigDecimal returnRate,            // 수익률 (%)

  // 날짜 정보
  LocalDate tradeDate,              // 거래 날짜
  String symbol,                    // 종목 심볼

  // 계좌 정보
  String accountId,
  BigDecimal commissionRate         // 수수료율
) {

}
