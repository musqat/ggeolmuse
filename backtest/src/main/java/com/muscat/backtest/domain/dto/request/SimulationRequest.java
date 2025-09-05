package com.muscat.backtest.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SimulationRequest {

  @NotBlank(message = "종목 코드는 필수입니다")
  private String symbol;

  @NotNull(message = "매수일은 필수입니다")
  private LocalDate purchaseDate;

  @NotNull(message = "투자 금액은 필수입니다")
  @Positive(message = "투자 금액은 0보다 커야 합니다")
  private BigDecimal investmentAmount;

  // 매매 수수료 설정 (선택적, 기본값 0%)
  @Builder.Default
  private BigDecimal tradingFeeRate = BigDecimal.ZERO; // 매매수수료율 (0.25% = 0.0025)

  private String userId;
}