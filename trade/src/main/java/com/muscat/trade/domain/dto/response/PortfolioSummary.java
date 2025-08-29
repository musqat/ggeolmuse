package com.muscat.trade.domain.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class PortfolioSummary {
    private BigDecimal totalInvestedAmount;     // 총 투자금액
    private BigDecimal totalCurrentValue;       // 총 평가금액
    private BigDecimal totalUnrealizedPnL;      // 총 평가손익
    private BigDecimal totalReturnRate;         // 총 수익률
    private BigDecimal totalDividends;          // 총 배당금
    private int holdingCount;                   // 보유 종목 수
    private List<HoldingResponseDto> holdings;  // 보유 종목 상세 정보
    private Map<String, BigDecimal> symbolReturnRates; // 종목별 수익률
    private Map<String, BigDecimal> symbolUnrealizedPnL; // 종목별 평가손익
}