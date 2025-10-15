package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.QCandle;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.JPAExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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


    // 최신 캔들을 심볼별로 일괄 조회
    public List<Candle> findLatestBySymbols(List<String> symbols) {
        QCandle c = candle;
        QCandle c2 = new QCandle("c2");
        return queryFactory
            .selectFrom(c)
            .where(
                c.symbol.in(symbols)
                    .and(c.date.eq(
                        JPAExpressions.select(c2.date.max())
                            .from(c2)
                            .where(c2.symbol.eq(c.symbol))
                    ))
            )
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

    // 최신 캔들 조회
    public Optional<Candle> findLatestBySymbol(String symbol) {
        Candle result = queryFactory
            .selectFrom(candle)
            .where(candle.symbol.eq(symbol))
            .orderBy(candle.date.desc())
            .limit(1)
            .fetchOne();
        return Optional.ofNullable(result);
    }

    // 특정 날짜 이전의 최신 캔들 조회
    public Optional<Candle> findLatestBySymbolBeforeDate(String symbol, LocalDate date) {
        Candle result = queryFactory
            .selectFrom(candle)
            .where(candle.symbol.eq(symbol)
                .and(candle.date.lt(date)))
            .orderBy(candle.date.desc())
            .limit(1)
            .fetchOne();
        return Optional.ofNullable(result);
    }

}
