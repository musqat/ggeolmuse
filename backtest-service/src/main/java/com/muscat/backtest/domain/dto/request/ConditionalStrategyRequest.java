package com.muscat.backtest.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Schema(description = "조건부 매수 투자 전략 요청")
@Data
@Builder
public class ConditionalStrategyRequest {

  @Schema(description = "종목 코드", example = "AAPL", required = true)
  @NotBlank(message = "종목 코드는 필수입니다")
  private String symbol;

  @Schema(description = "전략 시작일", example = "2024-01-01", required = true)
  @NotNull(message = "시작일은 필수입니다")
  private LocalDate startDate;

  @Schema(description = "전략 종료일", example = "2024-09-18", required = true)
  @NotNull(message = "종료일은 필수입니다")
  private LocalDate endDate;

  @Schema(description = "사용자 아이디", example = "user123")
  private String userId;

  // 조건부 매수 전략 전용 필드들
  @Schema(description = "총 투자 예산", example = "5000000.00", required = true)
  @NotNull(message = "총 투자금액은 필수입니다")
  @Positive(message = "총 투자금액은 0보다 커야 합니다")
  private BigDecimal totalInvestment;     // 총 투자예산

  @Schema(description = "하락률 조건 (예: 5% = 0.05)", example = "0.05", required = true)
  @NotNull(message = "하락률 조건은 필수입니다")
  @Positive(message = "하락률은 0보다 커야 합니다")
  private BigDecimal dropPercentage;      // 하락률 조건 (예: 5% 하락시 매수)

  @Schema(description = "최대 매수 횟수", example = "10")
  @Positive(message = "최대 매수 횟수는 0보다 커야 합니다")
  private Integer maxPurchases;           // 최대 매수 횟수
}