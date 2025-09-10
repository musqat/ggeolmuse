package com.muscat.trade.domain.repository;

import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.dto.response.TradeAggregationDto;
import com.muscat.trade.domain.entity.QTrade;
import com.muscat.trade.domain.entity.Trade;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TradeQueryRepository {

  private final JPAQueryFactory queryFactory;

  // 복잡한 거래 내역 검색 (다중 조건 + 페이지네이션)
  public Page<Trade> findTradesWithComplexFilters(String userId, String accountId,
      String symbol, TradeType tradeType, LocalDate startDate, LocalDate endDate,
      BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable) {

    QTrade trade = QTrade.trade;

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

    // 카운트 쿼리
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
  // FIFO 방식으로 매도 가능 수량 계산
  public BigDecimal calculateSellableQuantity(String userId, String accountId,
      String symbol, LocalDate sellDate) {
    QTrade trade = QTrade.trade;

    // 매도일 이전의 매수량 합계
    BigDecimal totalBuyQuantity = queryFactory
        .select(trade.quantity.sum())
        .from(trade)
        .where(
            trade.userId.eq(userId),
            trade.accountId.eq(accountId),
            trade.symbol.eq(symbol),
            trade.tradeType.eq(TradeType.BUY),
            trade.tradeDate.lt(sellDate)
        )
        .fetchOne();

    // 매도일 이전의 매도량 합계
    BigDecimal totalSellQuantity = queryFactory
        .select(trade.quantity.sum())
        .from(trade)
        .where(
            trade.userId.eq(userId),
            trade.accountId.eq(accountId),
            trade.symbol.eq(symbol),
            trade.tradeType.eq(TradeType.SELL),
            trade.tradeDate.lt(sellDate)
        )
        .fetchOne();

    BigDecimal buyAmount = totalBuyQuantity != null ? totalBuyQuantity : BigDecimal.ZERO;
    BigDecimal sellAmount = totalSellQuantity != null ? totalSellQuantity : BigDecimal.ZERO;

    return buyAmount.subtract(sellAmount).max(BigDecimal.ZERO);
  }

  // 커서 기반 페이지네이션
  public List<Trade> findTradesWithCursor(String userId, String lastTradeId,
      LocalDate fromDate, int limit) {
    QTrade trade = QTrade.trade;

    BooleanExpression whereClause = trade.userId.eq(userId);

    if (lastTradeId != null) {
      // lastTradeId보다 이전 거래들을 조회 (최신순)
      whereClause = whereClause.and(trade.tradeId.lt(lastTradeId));
    }

    if (fromDate != null) {
      whereClause = whereClause.and(trade.tradeDate.goe(fromDate));
    }

    return queryFactory
        .selectFrom(trade)
        .where(whereClause)
        .orderBy(trade.executedAt.desc())
        .limit(limit)
        .fetch();
  }

  // 집계 쿼리 최적화 (사용자별 거래 요약)
  public TradeAggregationDto getUserTradeAggregation(String userId, LocalDate startDate,
      LocalDate endDate) {
    QTrade trade = QTrade.trade;

    var result = queryFactory
        .select(
            trade.totalAmount.sum(), // 총 거래금액
            trade.fee.sum(),         // 총 수수료
            trade.count()            // 총 거래 건수
        )
        .from(trade)
        .where(
            trade.userId.eq(userId),
            betweenDates(startDate, endDate)
        )
        .fetchOne();

    return TradeAggregationDto.builder()
        .totalAmount(result.get(0, BigDecimal.class))
        .totalFee(result.get(1, BigDecimal.class))
        .totalCount(result.get(2, Long.class))
        .build();
  }


  // 동적 조건 메서드들
  private BooleanExpression eqAccountId(String accountId) {
    return accountId != null ? QTrade.trade.accountId.eq(accountId) : null;
  }

  private BooleanExpression eqSymbol(String symbol) {
    return symbol != null ? QTrade.trade.symbol.eq(symbol) : null;
  }

  private BooleanExpression eqTradeType(TradeType tradeType) {
    return tradeType != null ? QTrade.trade.tradeType.eq(tradeType) : null;
  }

  private BooleanExpression betweenDates(LocalDate startDate, LocalDate endDate) {
    if (startDate != null && endDate != null) {
      return QTrade.trade.tradeDate.between(startDate, endDate);
    }
    if (startDate != null) {
      return QTrade.trade.tradeDate.goe(startDate);
    }
    if (endDate != null) {
      return QTrade.trade.tradeDate.loe(endDate);
    }
    return null;
  }

  private BooleanExpression betweenAmounts(BigDecimal minAmount, BigDecimal maxAmount) {
    if (minAmount != null && maxAmount != null) {
      return QTrade.trade.totalAmount.between(minAmount, maxAmount);
    }
    if (minAmount != null) {
      return QTrade.trade.totalAmount.goe(minAmount);
    }
    if (maxAmount != null) {
      return QTrade.trade.totalAmount.loe(maxAmount);
    }
    return null;
  }


}