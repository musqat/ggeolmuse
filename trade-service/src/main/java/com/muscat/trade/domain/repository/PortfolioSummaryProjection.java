package com.muscat.trade.domain.repository;

import java.math.BigDecimal;

/**
 * 포트폴리오 요약 정보를 위한 Projection record
 */
public record PortfolioSummaryProjection(BigDecimal totalInvestedAmount, Integer holdingCount) {
    public PortfolioSummaryProjection {
        totalInvestedAmount = totalInvestedAmount != null ? totalInvestedAmount : BigDecimal.ZERO;
        holdingCount = holdingCount != null ? holdingCount : 0;
    }
}
