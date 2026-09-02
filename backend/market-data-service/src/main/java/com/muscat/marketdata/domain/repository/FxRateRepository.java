package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.FxRate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FxRateRepository extends JpaRepository<FxRate, LocalDate>, FxRateRepositoryCustom {

    // 특정 날짜의 환율 조회
    Optional<FxRate> findByDate(LocalDate date);

    // Bulk 조회: IN 쿼리로 여러 날짜의 환율 한 번에 조회
    List<FxRate> findByDateIn(List<LocalDate> dates);

    // 특정 날짜 이전의 가장 가까운 환율 조회 (주말/공휴일 대비)
    Optional<FxRate> findFirstByDateLessThanEqualOrderByDateDesc(LocalDate date);
}
