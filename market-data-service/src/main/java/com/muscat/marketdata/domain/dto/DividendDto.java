package com.muscat.marketdata.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "배당 정보 데이터")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DividendDto {

  @Schema(description = "종목 코드", example = "AAPL")
  private String symbol;      // 티커 예: AAPL
  @Schema(description = "권리락일 (배당 받을 권리가 없어지는 날)", example = "2024-09-15")
  private LocalDate exDate;   // 권리락일 (가장 중요)
  @Schema(description = "배당 지급일", example = "2024-09-30")
  private LocalDate paymentDate;  // 지급일 (없을 수 있음)
  @Schema(description = "기준일 (배당 기준 주주 확정일)", example = "2024-09-16")
  private LocalDate recordDate;// 기준일 (없을 수 있음)
  @Schema(description = "주당 배당금", example = "0.25")
  private BigDecimal amount;  // 주당 배당금
  @Schema(description = "배당금 통화", example = "USD")
  private String currency;    // 예: USD
  @Schema(description = "데이터 출처", example = "AlphaVantage")
  private String source;      // 데이터 출처 태그(예: "AlphaVantage")
}
