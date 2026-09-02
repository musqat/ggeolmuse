package com.muscat.trade.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "거래 집계 정보")
public record TradeAggregationDto(
  @Schema(description = "총 거래금액", example = "125500.00")
  BigDecimal totalAmount,

  @Schema(description = "총 수수료", example = "62.75")
  BigDecimal totalFee,

  @Schema(description = "총 거래 건수", example = "15")
  Long totalCount
) {

}
