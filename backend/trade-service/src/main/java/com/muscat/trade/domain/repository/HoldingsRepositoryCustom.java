package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.Holdings;

import java.util.Optional;

// Holdings Repository Custom Interface
public interface HoldingsRepositoryCustom {

    // 포트폴리오 집계 정보 계산
    PortfolioSummaryProjection calculatePortfolioSummary(String userId);

    // 사용자 ID, 계좌 ID, 심볼로 보유 종목 조회
    Optional<Holdings> findByUserIdAndAccountIdAndSymbol(String userId, Long accountId, String symbol);
}
