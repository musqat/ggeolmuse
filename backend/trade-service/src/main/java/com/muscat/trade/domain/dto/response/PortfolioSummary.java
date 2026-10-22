package com.muscat.trade.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}
