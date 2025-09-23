package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.QHoldings;
import com.muscat.trade.domain.entity.QTrade;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class HoldingsQueryRepository {

  private final JPAQueryFactory queryFactory;

  // 포트폴리오 집계 정보 계산
  public PortfolioSummaryProjection calculatePortfolioSummary(String userId) {
    QHoldings holdings = QHoldings.holdings;
    return queryFactory
        .select(Projections.constructor(PortfolioSummaryProjection.class,
            holdings.totalInvestedAmount.sum(),
            holdings.count().intValue()
        ))
        .from(holdings)
        .where(holdings.userId.eq(userId))
        .fetchOne();
  }

  // 거래 이력과 연관된 보유 종목 조회
  public List<Holdings> findHoldingsWithTradeHistory(String userId, String symbol,
      Integer minQuantity) {
    QHoldings holdings = QHoldings.holdings;
    QTrade trade = QTrade.trade;
    return queryFactory
        .selectFrom(holdings)
        .join(trade).on(
            trade.userId.eq(holdings.userId)
                .and(trade.symbol.eq(holdings.symbol))
        )
        .where(
            holdings.userId.eq(userId),
            symbol != null ? holdings.symbol.eq(symbol) : null,
            minQuantity != null ? holdings.totalQuantity.goe(minQuantity) : null
        )
        .distinct()
        .fetch();
  }

  // 투자금액 기준 상위 N개 종목 조회
  public List<Holdings> findTopHoldingsByInvestment(String userId, int limit) {
    QHoldings holdings = QHoldings.holdings;
    return queryFactory
        .selectFrom(holdings)
        .where(holdings.userId.eq(userId)
            .and(holdings.totalInvestedAmount.gt(BigDecimal.ZERO)))
        .orderBy(holdings.totalInvestedAmount.desc())
        .limit(limit)
        .fetch();
  }

  // 특정 금액 이상 투자한 종목들 조회
  public List<Holdings> findHoldingsByMinInvestment(String userId, BigDecimal minAmount) {
    QHoldings holdings = QHoldings.holdings;
    return queryFactory
        .selectFrom(holdings)
        .where(
            holdings.userId.eq(userId),
            holdings.totalInvestedAmount.goe(minAmount)
        )
        .orderBy(holdings.totalInvestedAmount.desc())
        .fetch();
  }


  public Optional<Holdings> findByUserIdAndAccountIdAndSymbol(String userId, Long accountId, String symbol) {
    QHoldings holdings = QHoldings.holdings;
    Holdings result = queryFactory
        .selectFrom(holdings)
        .where(
            holdings.userId.eq(userId),
            holdings.accountId.eq(accountId),
            holdings.symbol.eq(symbol)
        )
        .fetchOne();
    return Optional.ofNullable(result);
  }



  // 포트폴리오 요약 정보를 위한 Projection record
  public record PortfolioSummaryProjection(BigDecimal totalInvestedAmount, Integer holdingCount) {
    public PortfolioSummaryProjection {
      totalInvestedAmount = totalInvestedAmount != null ? totalInvestedAmount : BigDecimal.ZERO;
      holdingCount = holdingCount != null ? holdingCount : 0;
    }
  }

}