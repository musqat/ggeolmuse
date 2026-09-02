package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.enums.type.InvestmentMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "조건부 매수 투자 전략 요청")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
  @Schema(description = "투자 모드: TOTAL_BUDGET(총 예산 분할) 또는 PER_PURCHASE(회당 고정 금액)",
          example = "TOTAL_BUDGET",
          required = true,
          defaultValue = "TOTAL_BUDGET")
  @Builder.Default
  private InvestmentMode investmentMode = InvestmentMode.TOTAL_BUDGET;  // 투자 모드 (기본값: 총 예산 분할)

  @Schema(description = "[TOTAL_BUDGET 모드] 총 투자 예산", example = "4000000.00")
  private BigDecimal totalInvestment;     // 총 투자예산 (TOTAL_BUDGET 모드에서 사용)

  @Schema(description = "[PER_PURCHASE 모드] 회당 투자 금액", example = "500000.00")
  private BigDecimal amountPerPurchase;   // 회당 투자 금액 (PER_PURCHASE 모드에서 사용)

  @Schema(description = "하락률 조건 (예: 5% = 0.05)", example = "0.05", required = true)
  @NotNull(message = "하락률 조건은 필수입니다")
  @Positive(message = "하락률은 0보다 커야 합니다")
  private BigDecimal dropPercentage;      // 하락률 조건 (예: 5% 하락시 매수)

  @Schema(description = "최대 매수 횟수 (TOTAL_BUDGET 모드에서는 자동 계산, PER_PURCHASE 모드에서는 필수)", example = "10")
  @Positive(message = "최대 매수 횟수는 0보다 커야 합니다")
  private Integer maxPurchases;           // 최대 매수 횟수

  @Schema(description = "매수 시 환율 (수동 설정 시, null이면 자동)", example = "1300.00")
  private BigDecimal purchaseFxRate;  // 매수 시 환율 (수동)

  @Schema(description = "현재 환율 (수동 설정 시, null이면 자동)", example = "1350.00")
  private BigDecimal currentFxRate;  // 현재 환율 (수동)

  @Schema(description = "배당금 재투자 여부", example = "false")
  @Builder.Default
  private Boolean reinvestDividends = false;  // 배당금 자동 재투자 여부

  @Schema(description = "배당 원천징수세율 (15.4% = 0.154, 미적용 시 0)", example = "0.154")
  @Builder.Default
  private BigDecimal dividendTaxRate = BigDecimal.ZERO;  // 배당 원천징수 세율
}