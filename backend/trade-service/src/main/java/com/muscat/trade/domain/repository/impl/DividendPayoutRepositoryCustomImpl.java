package com.muscat.trade.domain.repository.impl;

import com.muscat.trade.domain.dto.TradePosition;
import com.muscat.trade.domain.entity.QDividendPayout;
import com.muscat.trade.domain.repository.DividendPayoutRepositoryCustom;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DividendPayoutRepositoryCustomImpl implements DividendPayoutRepositoryCustom {

  private static final QDividendPayout payout = QDividendPayout.dividendPayout;

  private final JPAQueryFactory queryFactory;

  @Override
  public List<TradePosition> findDistinctPositions() {
    return queryFactory
      .select(Projections.constructor(TradePosition.class, payout.userId, payout.accountId, payout.symbol))
      .distinct()
      .from(payout)
      .fetch();
  }
}
