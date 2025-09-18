package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.FxRate;
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

  Optional<FxRate> findByDate(LocalDate date);

  @Query("SELECT f FROM FxRate f WHERE f.date = (SELECT MAX(f2.date) FROM FxRate f2)")
  Optional<FxRate> findLatestRate();

  boolean existsByDate(LocalDate date);

  @Modifying
  void deleteByDate(LocalDate date);

  @Query("SELECT f FROM FxRate f WHERE f.date BETWEEN :from AND :to ORDER BY f.date")
  List<FxRate> findByDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query("SELECT f FROM FxRate f WHERE f.date >= :fromDate ORDER BY f.date DESC")
  List<FxRate> findRecentRates(@Param("fromDate") LocalDate fromDate);
}