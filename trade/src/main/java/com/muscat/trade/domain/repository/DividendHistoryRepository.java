package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.DividendHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface DividendHistoryRepository extends JpaRepository<DividendHistory, String> {

  List<DividendHistory> findByUserIdOrderByDividendDateDesc(String userId);

  List<DividendHistory> findByUserIdAndSymbolOrderByDividendDateDesc(String userId, String symbol);

  @Query("SELECT dh FROM DividendHistory dh WHERE dh.userId = :userId AND YEAR(dh.dividendDate) = :year ORDER BY dh.dividendDate DESC")
  List<DividendHistory> findByUserIdAndYear(@Param("userId") String userId, @Param("year") int year);

  @Query("SELECT COALESCE(SUM(dh.totalDividend), 0) FROM DividendHistory dh WHERE dh.userId = :userId AND YEAR(dh.dividendDate) = :year")
  BigDecimal getTotalDividendByUserIdAndYear(@Param("userId") String userId, @Param("year") int year);
}