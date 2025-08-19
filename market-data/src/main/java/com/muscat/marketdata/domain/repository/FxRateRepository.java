package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.FxRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FxRateRepository extends JpaRepository<FxRate, LocalDate> {

  // 특정일 환율 조회 (PK 직접 조회)
  Optional<FxRate> findByDate(LocalDate date);

  // 최신 환율 조회
  @Query("SELECT f FROM FxRate f WHERE f.date = (SELECT MAX(f2.date) FROM FxRate f2)")
  Optional<FxRate> findLatestRate();

  // 특정일 데이터 존재 여부
  boolean existsByDate(LocalDate date);

  // 특정일 데이터 삭제 (재수집용)
  @Modifying
  void deleteByDate(LocalDate date);

  // 구간 조회 (이미 FxRateService에 있지만 Repository에도 제공)
  @Query("SELECT f FROM FxRate f WHERE f.date BETWEEN :from AND :to ORDER BY f.date")
  List<FxRate> findByDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

  // 최근 N일 환율
  @Query("SELECT f FROM FxRate f WHERE f.date >= :fromDate ORDER BY f.date DESC")
  List<FxRate> findRecentRates(@Param("fromDate") LocalDate fromDate);
}