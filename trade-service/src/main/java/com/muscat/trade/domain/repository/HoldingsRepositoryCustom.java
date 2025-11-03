package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.Holdings;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Holdings Repository Custom Interface
 */
public interface HoldingsRepositoryCustom {

    /**
     * 포트폴리오 집계 정보 계산
     */
    PortfolioSummaryProjection calculatePortfolioSummary(String userId);

    /**
     * 거래 이력과 연관된 보유 종목 조회
     */
    List<Holdings> findHoldingsWithTradeHistory(String userId, String symbol, Integer minQuantity);

    /**
     * 투자금액 기준 상위 N개 종목 조회
     */
    List<Holdings> findTopHoldingsByInvestment(String userId, int limit);

    /**
     * 특정 금액 이상 투자한 종목들 조회
     */
    List<Holdings> findHoldingsByMinInvestment(String userId, BigDecimal minAmount);

    /**
     * 사용자 ID, 계좌 ID, 심볼로 보유 종목 조회
     */
    Optional<Holdings> findByUserIdAndAccountIdAndSymbol(String userId, Long accountId, String symbol);
}
