package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.enums.type.StrategyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Schema(description = "투자 전략 백테스트 요청")
@Data
@Builder
public class StrategyRequest {

  @Schema(description = "종목 코드", example = "AAPL", required = true)
  @NotBlank(message = "종목 코드는 필수입니다")
  private String symbol;

  @Schema(description = "전략 시작일", example = "2024-01-01", required = true)
  @NotNull(message = "시작일은 필수입니다")
  private LocalDate startDate;

  @Schema(description = "전략 종료일", example = "2024-09-18", required = true)
  @NotNull(message = "종료일은 필수입니다")
  private LocalDate endDate;

  @Schema(description = "전략 유형", example = "DCA", allowableValues = {"DCA", "CONDITIONAL"})
  private StrategyType strategyType; // URL 경로에 따라 자동 설정

  @Schema(description = "사용자 아이디", example = "user123")
  private String userId;

  // 적립식 투자 전략용 필드들
  @Schema(description = "월 투자금액 (DCA 전략용)", example = "100000.00")
  @Positive(message = "월 투자금액은 0보다 커야 합니다")
  private BigDecimal monthlyAmount;        // 월 투자금액
  @Schema(description = "매월 투자일 (1-28)", example = "15")
  private Integer investmentDay;           // 매월 몇일에 투자할지

  // 조건부 매수 전략용 필드들
  @Schema(description = "총 투자예산 (조건부 매수 전략용)", example = "5000000.00")
  @Positive(message = "총 투자금액은 0보다 커야 합니다")
  private BigDecimal totalInvestment;     // 총 투자예산
  @Schema(description = "하락률 조건 (예: 5% = 0.05)", example = "0.05")
  private BigDecimal dropPercentage;      // 하락률 조건 (예: 5% 하락시 매수)
  @Schema(description = "최대 매수 횟수", example = "10")
  private Integer maxPurchases;           // 최대 매수 횟수

}