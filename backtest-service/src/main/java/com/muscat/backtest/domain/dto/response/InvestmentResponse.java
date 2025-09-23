package com.muscat.backtest.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Schema(description = "실제 투자 실행 결과")
@Data
@Builder
public class InvestmentResponse {

  // 시뮬레이션 결과
  @Schema(description = "시뮬레이션 결과 데이터")
  private SimulationResponse simulation;

  // 실거래 정보
  @Schema(description = "보유 자산 아이디", example = "HOLD-20240918-001")
  private String holdingId;
  @Schema(description = "거래 아이디", example = "TXN-20240918-001")
  private String tradeId;
  @Schema(description = "종목 코드", example = "AAPL")
  private String symbol;
  @Schema(description = "매수일", example = "2024-09-18")
  private LocalDate purchaseDate;
  @Schema(description = "투자 금액", example = "1000000.00")
  private BigDecimal investmentAmount;

  // 매수 정보
  @Schema(description = "매수 단가", example = "238.15")
  private BigDecimal purchasePrice;
  @Schema(description = "매수 주수", example = "31.5")
  private BigDecimal shares;
  @Schema(description = "총 매수 비용", example = "7501.73")
  private BigDecimal totalCost;

  // 투자 상태
  @Schema(description = "투자 상태", example = "SUCCESS", allowableValues = {"SUCCESS", "FAILED"})
  private String status;          // SUCCESS, FAILED
  @Schema(description = "처리 결과 메시지", example = "투자가 성공적으로 완료되었습니다")
  private String message;

  // 포트폴리오 연동
  @Schema(description = "포트폴리오 생성 여부", example = "true")
  private boolean portfolioCreated;
  @Schema(description = "포트폴리오 상태", example = "ACTIVE")
  private String portfolioStatus;
}