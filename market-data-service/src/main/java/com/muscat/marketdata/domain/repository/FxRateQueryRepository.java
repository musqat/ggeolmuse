package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.entity.QFxRate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FxRateQueryRepository {

    private final JPAQueryFactory queryFactory;
    private static final QFxRate fxRate = QFxRate.fxRate;

    // 특정 기간의 환율 데이터 조회
    public List<FxRate> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return queryFactory
            .selectFrom(fxRate)
            .where(fxRate.date.between(startDate, endDate))
            .orderBy(fxRate.date.asc())
            .fetch();
    }

    // 최신 환율 조회
    public Optional<FxRate> findLatestRate() {
        FxRate result = queryFactory
            .selectFrom(fxRate)
            .orderBy(fxRate.date.desc())
            .limit(1)
            .fetchOne();
        return Optional.ofNullable(result);
    }

    // 특정 날짜 이후 환율 조회
    public List<FxRate> findRecentRates(LocalDate fromDate) {
        return queryFactory
            .selectFrom(fxRate)
            .where(fxRate.date.goe(fromDate))
            .orderBy(fxRate.date.desc())
            .fetch();
    }

    // 특정 날짜의 환율 조회
    public Optional<FxRate> findByDate(LocalDate date) {
        FxRate result = queryFactory
            .selectFrom(fxRate)
            .where(fxRate.date.eq(date))
            .fetchOne();
        return Optional.ofNullable(result);
    }

    // 환율 존재 여부 확인
    public boolean existsByDate(LocalDate date) {
        return queryFactory
            .selectOne()
            .from(fxRate)
            .where(fxRate.date.eq(date))
            .fetchFirst() != null;
    }
}