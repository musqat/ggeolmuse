package com.muscat.marketdata.domain.repository.impl;

import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.entity.QAsset;
import com.muscat.marketdata.domain.entity.QDividend;
import com.muscat.marketdata.domain.repository.DividendRepositoryCustom;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DividendRepositoryCustom 구현체
 */
@Repository
@RequiredArgsConstructor
public class DividendRepositoryCustomImpl implements DividendRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private static final QDividend dividend = QDividend.dividend;
    private static final QAsset asset = QAsset.asset;

    @Override
    public List<Dividend> findBySymbolsAndDateRange(List<String> symbols, LocalDate startDate, LocalDate endDate) {
        return queryFactory
            .selectFrom(dividend)
            .where(dividend.symbol.in(symbols), exDateWithin(startDate, endDate))
            .orderBy(dividend.symbol.asc(), dividend.exDate.desc())
            .fetch();
    }

    @Override
    public Map<String, BigDecimal> sumRegularAmountBySymbolsSince(Collection<String> symbols, LocalDate from, BigDecimal specialRatio) {
        NumberExpression<BigDecimal> total = dividend.amount.sum();
        return queryFactory
            .select(dividend.symbol, total)
            .from(dividend)
            .join(asset).on(asset.symbol.eq(dividend.symbol))
            .where(dividend.symbol.in(symbols),
                dividend.exDate.goe(from),
                dividend.amount.loe(asset.latestClose.multiply(specialRatio)))
            .groupBy(dividend.symbol)
            .fetch()
            .stream()
            .collect(Collectors.toMap(row -> row.get(dividend.symbol), row -> row.get(total)));
    }

    // 빈 쪽 경계는 걸지 않음. 둘 다 비면 조건 없음
    private static BooleanExpression exDateWithin(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return null;
        }
        return dividend.exDate.between(startDate, endDate);
    }
}
