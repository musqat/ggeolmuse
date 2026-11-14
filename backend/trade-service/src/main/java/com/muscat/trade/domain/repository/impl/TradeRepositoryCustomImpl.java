package com.muscat.trade.domain.repository.impl;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.dto.TradePosition;
import com.muscat.trade.domain.entity.QTrade;
import com.muscat.trade.domain.repository.TradeRepositoryCustom;
import com.querydsl.core.Tuple;
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

    // 매도일까지 누적 보유와 매도일 뒤 체결마다의 누적 보유(거래일 → 체결 시각 순) 가운데 최솟값(팔아도 뒤 보유가 음수가 아닌 양)
    @Override
    public BigDecimal calculateSellableQuantity(
            String userId,
            Long accountId,
            String symbol,
            LocalDate sellDate
    ) {
        // 매수는 더하고 매도는 빼서 보유 원장과 같은 순서로 쌓음
        NumberExpression<BigDecimal> signedQuantity = new CaseBuilder()
                .when(trade.tradeType.eq(TradeType.BUY)).then(trade.quantity)
                .otherwise(trade.quantity.negate());
        List<Tuple> trades = queryFactory
                .select(trade.tradeDate, signedQuantity)
                .from(trade)
                .where(
                        trade.userId.eq(userId),
                        trade.accountId.eq(accountId),
                        trade.symbol.eq(symbol),
                        trade.status.eq(TradeStatus.COMPLETED)
                )
                .orderBy(trade.tradeDate.asc(), trade.executedAt.asc(), trade.id.asc())
                .fetch();

        BigDecimal held = BigDecimal.ZERO;
        BigDecimal lowest = null;
        for (Tuple row : trades) {
            if (lowest == null && row.get(trade.tradeDate).isAfter(sellDate)) {
                lowest = held;
            }
            held = held.add(row.get(signedQuantity));
            if (lowest != null) {
                lowest = lowest.min(held);
            }
        }
        return (lowest != null ? lowest : held).max(BigDecimal.ZERO);
    }
}
