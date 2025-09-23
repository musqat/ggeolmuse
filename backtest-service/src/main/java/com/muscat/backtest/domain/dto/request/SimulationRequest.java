package com.muscat.backtest.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Schema(description = "투자 시뮬레이션 요청")
@Data
@Builder
public class SimulationRequest {

  @Schema(description = "종목 코드", example = "AAPL", required = true)
  @NotBlank(message = "종목 코드는 필수입니다")
  private String symbol;

  @Schema(description = "매수일", example = "2024-01-15", required = true)
  @NotNull(message = "매수일은 필수입니다")
  private LocalDate purchaseDate;

  @Schema(description = "투자 금액", example = "1000000.00", required = true)
  @NotNull(message = "투자 금액은 필수입니다")
  @Positive(message = "투자 금액은 0보다 커야 합니다")
  private BigDecimal investmentAmount;

  // 매매 수수료 설정 (선택적, 기본값 0%)
  @Schema(description = "매매 수수료율 (0.25% = 0.0025)", example = "0.0025")
  @Builder.Default
  private BigDecimal tradingFeeRate = BigDecimal.ZERO; // 매매수수료율 (0.25% = 0.0025)

  @Schema(description = "사용자 아이디", example = "user123")
  private String userId;
}