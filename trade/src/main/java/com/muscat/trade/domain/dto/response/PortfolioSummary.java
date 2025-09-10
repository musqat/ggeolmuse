package com.muscat.trade.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioSummary {
    private BigDecimal totalInvestedAmount;     // 총 투자금액
    private BigDecimal totalCurrentValue;       // 총 평가금액
    private BigDecimal totalUnrealizedPnL;      // 총 평가손익
    private BigDecimal totalReturnRate;         // 총 수익률
    private int holdingCount;                   // 보유 종목 수
    private List<HoldingResponseDto> holdings;  // 보유 종목 상세 정보
    private Map<String, BigDecimal> symbolReturnRates; // 종목별 수익률
    private Map<String, BigDecimal> symbolUnrealizedPnL; // 종목별 평가손익
    
    // 백테스트 관련 정보
    private boolean backtestAvailable;              // 백테스트 결과 사용 가능 여부
    private String backtestResult;                  // 백테스트 결과 (JSON)
    private LocalDateTime backtestCalculatedAt;     // 백테스트 계산 시간
    private String backtestStatus;                  // 백테스트 상태
}