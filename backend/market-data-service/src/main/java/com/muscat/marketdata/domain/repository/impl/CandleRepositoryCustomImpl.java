package com.muscat.marketdata.domain.repository.impl;

import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.QCandle;
import com.muscat.marketdata.domain.repository.CandleRepositoryCustom;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CandleRepositoryCustomImpl implements CandleRepositoryCustom {

  private final JPAQueryFactory queryFactory;
  private static final QCandle candle = QCandle.candle;

  @Override
  public List<Candle> findBySymbolsAndDateRange(List<String> symbols, LocalDate startDate, LocalDate endDate) {
    return queryFactory
      .selectFrom(candle)
      .where(candle.symbol.in(symbols)
        .and(candle.date.between(startDate, endDate)))
      .orderBy(candle.symbol.asc(), candle.date.asc())
      .fetch();
  }

  @Override
  public List<Candle> findCandlesWithDividends(String symbol, LocalDate startDate, LocalDate endDate) {
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
    List<Candle> result = new java.util.ArrayList<>();
    for (String symbol : symbols) {
      List<Candle> candles = queryFactory
        .selectFrom(candle)
        .where(candle.symbol.eq(symbol))
        .orderBy(candle.date.desc())
        .limit(daysBack)
        .fetch();
      result.addAll(candles);
    }
    return result;
  }

  @Override
  public long countDistinctSymbols() {
    Long count = queryFactory
      .select(candle.symbol.countDistinct())
      .from(candle)
      .fetchOne();
    return count != null ? count : 0L;
  }

  // 분할이 close 에 반영되지 않으면 종목 안에서 adjusted_close/close 가 분할 배수만큼 갈린다.
  // 2:1 분할이 최소라 문턱은 1.9 로 둔다. 배당만으로 이만큼 벌어진 고배당 종목도 같이 잡히는데,
  // 그쪽도 다시 받아서 손해가 아니다.
  private static final BigDecimal SPLIT_RATIO_THRESHOLD = new BigDecimal("1.9");

  @Override
  public List<String> findSymbolsWithUnadjustedSplits(LocalDate from) {
    NumberExpression<BigDecimal> ratio = candle.adjustedClose.divide(candle.close);

    return queryFactory
      .select(candle.symbol)
      .from(candle)
      .where(candle.date.goe(from)
        .and(candle.close.gt(BigDecimal.ZERO))
        .and(candle.adjustedClose.gt(BigDecimal.ZERO)))
      .groupBy(candle.symbol)
      .having(ratio.max().divide(ratio.min()).gt(SPLIT_RATIO_THRESHOLD))
      .orderBy(candle.symbol.asc())
      .fetch();
  }
}
