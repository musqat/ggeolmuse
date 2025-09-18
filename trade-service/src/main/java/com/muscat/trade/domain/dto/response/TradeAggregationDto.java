package com.muscat.trade.domain.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Builder
@Data
public class TradeAggregationDto {

    private BigDecimal totalAmount;
    private BigDecimal totalFee;
    private Long totalCount;
}