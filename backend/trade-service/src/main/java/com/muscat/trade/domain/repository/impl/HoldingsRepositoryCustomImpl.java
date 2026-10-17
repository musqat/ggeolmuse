package com.muscat.trade.domain.repository.impl;

import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.QHoldings;
import com.muscat.trade.domain.repository.HoldingsRepositoryCustom;
import com.muscat.trade.domain.repository.PortfolioSummaryProjection;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * HoldingsRepositoryCustom 구현체
 */
@Repository
@RequiredArgsConstructor
public class HoldingsRepositoryCustomImpl implements HoldingsRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private static final QHoldings holdings = QHoldings.holdings;

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
