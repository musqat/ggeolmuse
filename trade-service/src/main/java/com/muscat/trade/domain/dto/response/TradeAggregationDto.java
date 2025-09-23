package com.muscat.trade.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Schema(description = "거래 집계 정보")
@Builder
@Data
public class TradeAggregationDto {

    @Schema(description = "총 거래금액", example = "125500.00")
    private BigDecimal totalAmount;

    @Schema(description = "총 수수료", example = "62.75")
    private BigDecimal totalFee;

    @Schema(description = "총 거래 건수", example = "15")
    private Long totalCount;
}