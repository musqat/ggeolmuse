package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.FxRate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

@Repository
public interface FxRateRepository extends JpaRepository<FxRate, LocalDate> {

  Optional<FxRate> findByDate(LocalDate date);

  boolean existsByDate(LocalDate date);

  @Modifying
  void deleteByDate(LocalDate date);

  // Bulk 조회: IN 쿼리로 여러 날짜의 환율 한 번에 조회
  List<FxRate> findByDateIn(List<LocalDate> dates);
}