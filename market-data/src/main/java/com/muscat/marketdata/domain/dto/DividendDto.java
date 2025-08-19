package com.muscat.marketdata.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 배당 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DividendDto {
  private String symbol;      // 티커 예: AAPL
  private LocalDate exDate;   // 권리락일 (가장 중요)
  private LocalDate paymentDate;  // 지급일 (없을 수 있음)
  private LocalDate recordDate;// 기준일 (없을 수 있음)
  private BigDecimal amount;  // 주당 배당금
  private String currency;    // 예: USD
  private String source;      // 데이터 출처 태그(예: "AlphaVantage")
}
