package com.muscat.trade.domain.repository.impl;

import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.QTrade;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.TradeRepositoryCustom;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * TradeRepositoryCustom 구현체
 */
@Repository
@RequiredArgsConstructor
public class TradeRepositoryCustomImpl implements TradeRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private static final QTrade trade = QTrade.trade;

    @Override
    public Page<Trade> findTradesWithComplexFilters(
            String userId,
            Long accountId,
            String symbol,
            TradeType tradeType,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable
    ) {
        JPAQuery<Trade> query = queryFactory
                .selectFrom(trade)
                .where(
                        trade.userId.eq(userId),
                        eqAccountId(accountId),
                        eqSymbol(symbol),
                        eqTradeType(tradeType),
                        betweenDates(startDate, endDate),
                        betweenAmounts(minAmount, maxAmount)
                )
                .orderBy(trade.executedAt.desc());

        Long totalCount = queryFactory
                .select(trade.count())
                .from(trade)
                .where(
                        trade.userId.eq(userId),
                        eqAccountId(accountId),
                        eqSymbol(symbol),
                        eqTradeType(tradeType),
                        betweenDates(startDate, endDate),
                        betweenAmounts(minAmount, maxAmount)
                )
                .fetchOne();

        long total = totalCount != null ? totalCount : 0L;

        List<Trade> trades = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return new PageImpl<>(trades, pageable, total);
    }

    @Override
    public BigDecimal calculateSellableQuantity(
            String userId,
            Long accountId,
            String symbol,
            LocalDate sellDate
    ) {
        BigDecimal totalBuyQuantity = queryFactory
                .select(trade.quantity.sum())
                .from(trade)
                .where(
                        trade.userId.eq(userId),
                        trade.accountId.eq(accountId),
                        trade.symbol.eq(symbol),
                        trade.tradeType.eq(TradeType.BUY),
                        trade.tradeDate.loe(sellDate)
                )
                .fetchOne();

        BigDecimal totalSellQuantity = queryFactory
                .select(trade.quantity.sum())
                .from(trade)
                .where(
                        trade.userId.eq(userId),
                        trade.accountId.eq(accountId),
                        trade.symbol.eq(symbol),
                        trade.tradeType.eq(TradeType.SELL),
                        trade.tradeDate.loe(sellDate)
                )
                .fetchOne();

        BigDecimal buyAmount = totalBuyQuantity != null ? totalBuyQuantity : BigDecimal.ZERO;
        BigDecimal sellAmount = totalSellQuantity != null ? totalSellQuantity : BigDecimal.ZERO;

        return buyAmount.subtract(sellAmount).max(BigDecimal.ZERO);
    }

    // ========== 동적 조건 헬퍼 메서드들 ==========

    private BooleanExpression eqAccountId(Long accountId) {
        return accountId != null ? trade.accountId.eq(accountId) : null;
    }

    private BooleanExpression eqSymbol(String symbol) {
        return symbol != null ? trade.symbol.eq(symbol) : null;
    }

    private BooleanExpression eqTradeType(TradeType tradeType) {
        return tradeType != null ? trade.tradeType.eq(tradeType) : null;
    }

    private BooleanExpression betweenDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return trade.tradeDate.between(startDate, endDate);
        }
        if (startDate != null) {
            return trade.tradeDate.goe(startDate);
        }
        if (endDate != null) {
            return trade.tradeDate.loe(endDate);
        }
        return null;
    }

    private BooleanExpression betweenAmounts(BigDecimal minAmount, BigDecimal maxAmount) {
        if (minAmount != null && maxAmount != null) {
            return trade.totalAmount.between(minAmount, maxAmount);
        }
        if (minAmount != null) {
            return trade.totalAmount.goe(minAmount);
        }
        if (maxAmount != null) {
            return trade.totalAmount.loe(maxAmount);
        }
        return null;
    }
}
