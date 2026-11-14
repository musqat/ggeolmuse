package com.muscat.trade.domain.repository.impl;

import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.QHoldings;
import com.muscat.trade.domain.repository.HoldingsRepositoryCustom;
import com.muscat.trade.domain.repository.PortfolioSummaryProjection;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * HoldingsRepositoryCustom 구현체
 */
@Repository
@RequiredArgsConstructor
public class HoldingsRepositoryCustomImpl implements HoldingsRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private static final QHoldings holdings = QHoldings.holdings;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public PortfolioSummaryProjection calculatePortfolioSummary(String userId) {
        return queryFactory
                .select(Projections.constructor(PortfolioSummaryProjection.class,
                        holdings.totalInvestedAmount.sum(),
                        holdings.count().intValue()
                ))
                .from(holdings)
                .where(holdings.userId.eq(userId))
                .fetchOne();
    }

    @Override
    public Optional<Holdings> findByUserIdAndAccountIdAndSymbolWithLock(String userId, Long accountId, String symbol) {
        // id 만 읽는 행 잠금(컨텍스트의 낡은 보유와 버전을 견주지 않음) 뒤 엔티티를 DB 값으로 채움
        Long id = queryFactory
                .select(holdings.id)
                .from(holdings)
                .where(holdings.userId.eq(userId), holdings.accountId.eq(accountId), holdings.symbol.eq(symbol))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        if (id == null) {
            return Optional.empty();
        }
        Holdings locked = entityManager.find(Holdings.class, id);
        entityManager.refresh(locked);
        return Optional.of(locked);
    }
}
