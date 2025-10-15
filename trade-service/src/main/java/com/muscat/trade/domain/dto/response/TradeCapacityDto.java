package com.muscat.trade.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 거래 가능 수량 응답 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeCapacityDto {

    // 매수 관련
    private BigDecimal maxBuyQuantity;        // 최대 매수 가능 수량
    private BigDecimal availableBalance;      // 사용 가능 잔액 (USD)
    private BigDecimal currentPrice;          // 현재 가격
    private BigDecimal estimatedFee;          // 예상 수수료

    // 매도 관련
    private BigDecimal maxSellQuantity;       // 최대 매도 가능 수량
    private BigDecimal totalHoldings;         // 전체 보유 수량
    private BigDecimal sellableQuantity;      // 매도 가능 수량 (날짜 기준)
    private BigDecimal avgPurchasePrice;      // 평균 매수가

    // 평가 정보
    private BigDecimal currentValue;          // 현재 평가액
    private BigDecimal unrealizedPnL;         // 평가손익
    private BigDecimal returnRate;            // 수익률 (%)

    // 날짜 정보
    private LocalDate tradeDate;              // 거래 날짜
    private String symbol;                    // 종목 심볼

    // 계좌 정보
    private String accountId;
    private BigDecimal commissionRate;        // 수수료율
}
