package com.muscat.marketdata.domain.repository.impl;

import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.QAsset;
import com.muscat.marketdata.domain.entity.QCandle;
import com.muscat.marketdata.domain.repository.CandleRepositoryCustom;
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
  private static final QAsset asset = QAsset.asset;

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

  @Override
  public List<String> findSymbolsWithSplits(LocalDate from) {
    // 상장폐지 종목은 야후가 404 를 낸다. 다시 받을 수 없으니 목록에서 뺀다
    return queryFactory
      .select(candle.symbol).distinct()
      .from(candle)
      .join(asset).on(asset.symbol.eq(candle.symbol))
      .where(candle.date.goe(from)
        .and(candle.splitCoefficient.ne(BigDecimal.ONE))
        .and(asset.active.isTrue()))
      .orderBy(candle.symbol.asc())
      .fetch();
  }
}
