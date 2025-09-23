package com.muscat.backtest.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Schema(description = "DCA (적립식) 투자 전략 요청")
@Data
@Builder
public class DcaStrategyRequest {

  @Schema(description = "종목 코드", example = "AAPL", required = true)
  @NotBlank(message = "종목 코드는 필수입니다")
  private String symbol;

  @Schema(description = "DCA 전략 시작일", example = "2024-01-01", required = true)
  @NotNull(message = "시작일은 필수입니다")
  private LocalDate startDate;

  @Schema(description = "DCA 전략 종료일", example = "2024-09-18", required = true)
  @NotNull(message = "종료일은 필수입니다")
  private LocalDate endDate;

  @Schema(description = "사용자 아이디", example = "user123")
  private String userId;

  // DCA 전략 전용 필드들
  @Schema(description = "매월 투자할 금액", example = "100000.00", required = true)
  @NotNull(message = "월 투자금액은 필수입니다")
  @Positive(message = "월 투자금액은 0보다 커야 합니다")
  private BigDecimal monthlyAmount;        // 월 투자금액

  @Schema(description = "매월 투자할 날짜 (1-28)", example = "15", required = true)
  @NotNull(message = "투자일은 필수입니다")
  @Positive(message = "투자일은 1 이상이어야 합니다")
  private Integer purchaseDay;             // 매월 몇일에 투자할지
}