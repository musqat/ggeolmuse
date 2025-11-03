package com.muscat.marketdata.domain.repository.impl;

import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.entity.QDividend;
import com.muscat.marketdata.domain.repository.DividendRepositoryCustom;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DividendRepositoryCustom 구현체
 */
@Repository
@RequiredArgsConstructor
public class DividendRepositoryCustomImpl implements DividendRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private static final QDividend dividend = QDividend.dividend;

    @Override
    public List<Dividend> findBySymbolsAndDateRange(List<String> symbols, LocalDate startDate, LocalDate endDate) {
        return queryFactory
            .selectFrom(dividend)
            .where(dividend.symbol.in(symbols)
                .and(dividend.exDate.between(startDate, endDate)))
            .orderBy(dividend.symbol.asc(), dividend.exDate.desc())
            .fetch();
    }

    @Override
    public List<Dividend> findHighDividendStocks(BigDecimal minAmount, LocalDate fromDate) {
        return queryFactory
            .selectFrom(dividend)
            .where(dividend.amount.goe(minAmount)
                .and(dividend.exDate.goe(fromDate)))
            .orderBy(dividend.amount.desc(), dividend.exDate.desc())
            .fetch();
    }
}
