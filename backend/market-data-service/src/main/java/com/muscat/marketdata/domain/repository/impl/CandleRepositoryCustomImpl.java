package com.muscat.marketdata.domain.repository.impl;

import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.QAsset;
import com.muscat.marketdata.domain.entity.QCandle;
import com.muscat.marketdata.domain.repository.CandleRepositoryCustom;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

  @PersistenceContext
  private EntityManager entityManager;
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

  // 종목마다 최근 N개 캔들을 한 쿼리로 조회(종목별 순위)
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
    // 상장폐지 종목 제외(야후 404 라 다시 못 받음)
    return queryFactory
      .select(candle.symbol).distinct()
      .from(candle)
      .join(asset).on(asset.symbol.eq(candle.symbol))
      .where(candle.date.goe(from)
        // 1 은 SQL 에 직접 박음. 파라미터로 넘기면 부분 인덱스 idx_candle_split_date 를 못 탐
        .and(Expressions.booleanTemplate("{0} <> 1", candle.splitCoefficient))
        .and(asset.active.isTrue())
        // 분할일 봉까지 전 기간을 받은 종목 제외(분할일 = 기록 날짜면 받은 것)
        .and(asset.fullHistoryThrough.isNull().or(candle.date.gt(asset.fullHistoryThrough))))
      .orderBy(candle.symbol.asc())
      .fetch();
  }
}
