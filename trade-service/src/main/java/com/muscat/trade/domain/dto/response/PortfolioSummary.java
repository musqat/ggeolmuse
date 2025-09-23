package com.muscat.trade.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "포트폴리오 요약 정보")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioSummary {
    @Schema(description = "총 투자금액", example = "125000.00")
    private BigDecimal totalInvestedAmount;     // 총 투자금액

    @Schema(description = "총 평가금액", example = "138500.00")
    private BigDecimal totalCurrentValue;       // 총 평가금액

    @Schema(description = "총 평가손익", example = "13500.00")
    private BigDecimal totalUnrealizedPnL;      // 총 평가손익

    @Schema(description = "총 수익률 (%)", example = "10.80")
    private BigDecimal totalReturnRate;         // 총 수익률

    @Schema(description = "보유 종목 수", example = "5")
    private int holdingCount;                   // 보유 종목 수

    @Schema(description = "보유 종목 상세 정보")
    private List<HoldingResponseDto> holdings;  // 보유 종목 상세 정보

    @Schema(description = "종목별 수익률 (%)", example = "{\"AAPL\": 12.5, \"GOOGL\": 8.3}")
    private Map<String, BigDecimal> symbolReturnRates; // 종목별 수익률

    @Schema(description = "종목별 평가손익", example = "{\"AAPL\": 2500.00, \"GOOGL\": 1200.00}")
    private Map<String, BigDecimal> symbolUnrealizedPnL; // 종목별 평가손익

    // 백테스트 관련 정보
    @Schema(description = "백테스트 결과 사용 가능 여부", example = "true")
    private boolean backtestAvailable;              // 백테스트 결과 사용 가능 여부

    @Schema(description = "백테스트 결과 (JSON)", example = "{\"totalReturn\": 15.2, \"sharpeRatio\": 1.3}")
    private String backtestResult;                  // 백테스트 결과 (JSON)

    @Schema(description = "백테스트 계산 시간", example = "2024-09-18T16:45:00")
    private LocalDateTime backtestCalculatedAt;     // 백테스트 계산 시간

    @Schema(description = "백테스트 상태", example = "COMPLETED", allowableValues = {"PENDING", "RUNNING", "COMPLETED", "FAILED"})
    private String backtestStatus;                  // 백테스트 상태
}