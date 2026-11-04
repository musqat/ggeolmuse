package com.muscat.trade.domain.repository.impl;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.dto.TradePosition;
import com.muscat.trade.domain.entity.QTrade;
import com.muscat.trade.domain.repository.TradeRepositoryCustom;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * TradeRepositoryCustom
 */
@Repository
@RequiredArgsConstructor
public class TradeRepositoryCustomImpl implements TradeRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private static final QTrade trade = QTrade.trade;

    @Override
    public List<TradePosition> findDistinctPositions(TradeStatus status) {
        return queryFactory
                .select(Projections.constructor(TradePosition.class, trade.userId, trade.accountId, trade.symbol))
                .distinct()
                .from(trade)
                .where(trade.status.eq(status))
                .fetch();
    }

    @Override
    public List<TradePosition> findPositionsTradedBefore(String symbol, TradeStatus status, LocalDate before) {
        return queryFactory
                .select(Projections.constructor(TradePosition.class, trade.userId, trade.accountId, trade.symbol))
                .distinct()
                .from(trade)
                .where(trade.symbol.eq(symbol), trade.status.eq(status), trade.tradeDate.lt(before))
                .orderBy(trade.accountId.asc())
                .fetch();
    }

    @Override
    public BigDecimal calculateSellableQuantity(
            String userId,
            Long accountId,
            String symbol,
            LocalDate sellDate
    ) {
        // 매수는 더하고 매도는 빼서 한 번에 합산
        NumberExpression<BigDecimal> signedQuantity = new CaseBuilder()
                .when(trade.tradeType.eq(TradeType.BUY)).then(trade.quantity)
                .otherwise(trade.quantity.negate());
        BigDecimal net = queryFactory
                .select(signedQuantity.sum())
                .from(trade)
                .where(
                        trade.userId.eq(userId),
                        trade.accountId.eq(accountId),
                        trade.symbol.eq(symbol),
                        trade.status.eq(TradeStatus.COMPLETED),
                        trade.tradeDate.loe(sellDate)
                )
                .fetchOne();

        return (net != null ? net : BigDecimal.ZERO).max(BigDecimal.ZERO);
    }
}
