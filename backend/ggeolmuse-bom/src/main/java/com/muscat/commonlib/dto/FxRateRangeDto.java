package com.muscat.commonlib.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "저장된 환율 전체의 최저 · 최고")
public record FxRateRangeDto(
  @Schema(description = "최저 USD/KRW 환율", example = "1053.37")
  BigDecimal min,

  @Schema(description = "최저 환율 날짜", example = "2018-04-03")
  @JsonFormat(pattern = "yyyy-MM-dd")
  LocalDate minDate,

  @Schema(description = "최고 USD/KRW 환율", example = "1554.40")
  BigDecimal max,

  @Schema(description = "최고 환율 날짜", example = "2026-07-02")
  @JsonFormat(pattern = "yyyy-MM-dd")
  LocalDate maxDate
) {

}
