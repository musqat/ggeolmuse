package com.muscat.trade.domain.repository;

// Holdings Repository Custom Interface
public interface HoldingsRepositoryCustom {

    // 포트폴리오 집계 정보 계산
    PortfolioSummaryProjection calculatePortfolioSummary(String userId);
}
