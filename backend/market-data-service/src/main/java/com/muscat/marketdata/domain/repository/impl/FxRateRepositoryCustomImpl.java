package com.muscat.marketdata.domain.repository.impl;

import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.entity.QFxRate;
import com.muscat.marketdata.domain.repository.FxRateRepositoryCustom;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

/**
 * FxRateRepositoryCustom 구현체
 */
@RequiredArgsConstructor
public class FxRateRepositoryCustomImpl implements FxRateRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private static final QFxRate fxRate = QFxRate.fxRate;

    @Override
    public Optional<FxRate> findLatestRate() {
        FxRate result = queryFactory
            .selectFrom(fxRate)
            .orderBy(fxRate.date.desc())
            .limit(1)
            .fetchOne();
        return Optional.ofNullable(result);
    }
}
