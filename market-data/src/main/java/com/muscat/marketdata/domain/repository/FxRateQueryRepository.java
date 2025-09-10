package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.entity.QFxRate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

// 환율 데이터 복잡 조회용 Repository (필요시 메서드 추가)
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
}