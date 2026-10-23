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

    // 기간 안 환율
    List<FxRate> findByDateBetween(LocalDate from, LocalDate to);

    // 특정 날짜 이전의 가장 가까운 환율 조회 (주말/공휴일 대비)
    Optional<FxRate> findFirstByDateLessThanEqualOrderByDateDesc(LocalDate date);

    // 저장된 환율 전체의 최저 · 최고 (환전 환율 허용 범위용)
    Optional<FxRate> findFirstByOrderByRateAsc();

    Optional<FxRate> findFirstByOrderByRateDesc();
}
