package com.muscat.trade.domain.repository.impl;

import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.QHoldings;
import com.muscat.trade.domain.entity.QTrade;
import com.muscat.trade.domain.repository.HoldingsRepositoryCustom;
import com.muscat.trade.domain.repository.PortfolioSummaryProjection;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * HoldingsRepositoryCustom 구현체
 */
@Repository
@RequiredArgsConstructor
public class HoldingsRepositoryCustomImpl implements HoldingsRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private static final QHoldings holdings = QHoldings.holdings;
    private static final QTrade trade = QTrade.trade;

    @Override
    public PortfolioSummaryProjection calculatePortfolioSummary(String userId) {
        return queryFactory
                .select(Projections.constructor(PortfolioSummaryProjection.class,
                        holdings.totalInvestedAmount.sum(),
                        holdings.count().intValue()
                ))
                .from(holdings)
                .where(holdings.userId.eq(userId))
                .fetchOne();
    }

    @Override
    public List<Holdings> findHoldingsWithTradeHistory(String userId, String symbol, Integer minQuantity) {
        return queryFactory
                .selectFrom(holdings)
                .join(trade).on(
                        trade.userId.eq(holdings.userId)
                                .and(trade.symbol.eq(holdings.symbol))
                )
                .where(
                        holdings.userId.eq(userId),
                        symbol != null ? holdings.symbol.eq(symbol) : null,
                        minQuantity != null ? holdings.totalQuantity.goe(minQuantity) : null
                )
                .distinct()
                .fetch();
    }

    @Override
    public List<Holdings> findTopHoldingsByInvestment(String userId, int limit) {
        return queryFactory
                .selectFrom(holdings)
                .where(holdings.userId.eq(userId)
                        .and(holdings.totalInvestedAmount.gt(BigDecimal.ZERO)))
                .orderBy(holdings.totalInvestedAmount.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public List<Holdings> findHoldingsByMinInvestment(String userId, BigDecimal minAmount) {
        return queryFactory
                .selectFrom(holdings)
                .where(
                        holdings.userId.eq(userId),
                        holdings.totalInvestedAmount.goe(minAmount)
                )
                .orderBy(holdings.totalInvestedAmount.desc())
                .fetch();
    }

    @Override
    public Optional<Holdings> findByUserIdAndAccountIdAndSymbol(String userId, Long accountId, String symbol) {
        Holdings result = queryFactory
                .selectFrom(holdings)
                .where(
                        holdings.userId.eq(userId),
                        holdings.accountId.eq(accountId),
                        holdings.symbol.eq(symbol)
                )
                .fetchOne();
        return Optional.ofNullable(result);
    }
}
