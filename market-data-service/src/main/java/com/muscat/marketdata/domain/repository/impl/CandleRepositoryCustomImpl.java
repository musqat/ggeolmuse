package com.muscat.marketdata.domain.repository.impl;

import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.QCandle;
import com.muscat.marketdata.domain.repository.CandleRepositoryCustom;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CandleRepositoryCustomImpl implements CandleRepositoryCustom {

  private final JPAQueryFactory queryFactory;
  private static final QCandle candle = QCandle.candle;

  @Override
  public List<Candle> findBySymbolsAndDateRange(List<String> symbols, LocalDate startDate,
    LocalDate endDate) {
    return queryFactory
      .selectFrom(candle)
      .where(candle.symbol.in(symbols)
        .and(candle.date.between(startDate, endDate)))
      .orderBy(candle.symbol.asc(), candle.date.asc())
      .fetch();
  }

  @Override
  public List<Candle> findCandlesWithDividends(String symbol, LocalDate startDate,
    LocalDate endDate) {
    return queryFactory
      .selectFrom(candle)
      .where(candle.symbol.eq(symbol)
        .and(candle.date.between(startDate, endDate))
        .and(candle.dividendAmount.gt(BigDecimal.ZERO)))
      .orderBy(candle.date.asc())
      .fetch();
  }

  @Override
  public Optional<Candle> findLatestBySymbol(String symbol) {
    Candle result = queryFactory
      .selectFrom(candle)
      .where(candle.symbol.eq(symbol))
      .orderBy(candle.date.desc())
      .limit(1)
      .fetchOne();
    return Optional.ofNullable(result);
  }

  @Override
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

  @Override
  public List<Candle> findRecentBySymbols(List<String> symbols, int daysBack) {
    LocalDate fromDate = LocalDate.now().minusDays(daysBack * 2 + 3);  // 주말 포함 여유있게
    return queryFactory
      .selectFrom(candle)
      .where(candle.symbol.in(symbols)
        .and(candle.date.goe(fromDate)))
      .orderBy(candle.symbol.asc(), candle.date.desc())
      .fetch();
  }

  @Override
  public long countDistinctSymbols() {
    Long count = queryFactory
      .select(candle.symbol.countDistinct())
      .from(candle)
      .fetchOne();
    return count != null ? count : 0L;
  }
}
