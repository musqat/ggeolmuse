package com.muscat.backtest.domain.repository.impl;

import com.muscat.backtest.domain.entity.QBacktestHistory;
import com.muscat.backtest.domain.repository.BacktestHistoryRepositoryCustom;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BacktestHistoryRepositoryCustomImpl implements BacktestHistoryRepositoryCustom {

  private static final QBacktestHistory history = QBacktestHistory.backtestHistory;

  private final JPAQueryFactory queryFactory;

  @PersistenceContext
  private EntityManager entityManager;

  @Override
  public long reassignOwner(String from, String to) {
    long moved = queryFactory
      .update(history)
      .set(history.userId, to)
      .where(history.userId.eq(from))
      .execute();
    entityManager.clear();
    return moved;
  }
}
