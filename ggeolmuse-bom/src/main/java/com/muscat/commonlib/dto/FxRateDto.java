package com.muscat.commonlib.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "환율 정보")
public record FxRateDto(
  @Schema(description = "환율 기준 날짜", example = "2024-09-18", required = true)
  @JsonFormat(pattern = "yyyy-MM-dd")
  LocalDate date,

  @Schema(description = "USD/KRW 환율", example = "1350.50", required = true)
  BigDecimal rate,

  @Schema(description = "데이터 출처", example = "KOREAEXIM", allowableValues = {"KOREAEXIM",
    "YAHOO_FINANCE", "ALPHA_VANTAGE"})
  String source
) {

}
