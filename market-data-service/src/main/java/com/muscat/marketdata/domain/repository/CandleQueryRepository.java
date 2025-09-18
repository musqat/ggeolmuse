package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.QCandle;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// 캔들 데이터 복잡 조회용 Repository
@Repository
@RequiredArgsConstructor
public class CandleQueryRepository {

    private final JPAQueryFactory queryFactory;
    private static final QCandle candle = QCandle.candle;

    // 여러 심볼의 캔들 데이터를 한 번에 조회
    public List<Candle> findBySymbolsAndDateRange(List<String> symbols, LocalDate startDate, LocalDate endDate) {
        return queryFactory
            .selectFrom(candle)
            .where(candle.symbol.in(symbols)
                .and(candle.date.between(startDate, endDate)))
            .orderBy(candle.symbol.asc(), candle.date.asc())
            .fetch();
    }


    // 배당이 지급된 날짜의 캔들만 조회
    public List<Candle> findCandlesWithDividends(String symbol, LocalDate startDate, LocalDate endDate) {
        return queryFactory
            .selectFrom(candle)
            .where(candle.symbol.eq(symbol)
                .and(candle.date.between(startDate, endDate))
                .and(candle.dividendAmount.gt(BigDecimal.ZERO)))
            .orderBy(candle.date.asc())
            .fetch();
    }


}