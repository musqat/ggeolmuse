package com.muscat.marketdata.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "주식 분할")
public record SplitDto(
  @Schema(description = "분할이 반영된 첫 거래일", example = "2024-06-10")
  LocalDate date,

  @Schema(description = "분할 전 1주가 분할 뒤 몇 주인지(1:10 병합은 0.1)", example = "10")
  BigDecimal ratio
) {
}
