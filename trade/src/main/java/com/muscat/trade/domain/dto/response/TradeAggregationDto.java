package com.muscat.trade.domain.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

// 거래 집계 결과 DTO
@Builder
@Data
public class TradeAggregationDto {

    private BigDecimal totalAmount;
    private BigDecimal totalFee;
    private Long totalCount;
}