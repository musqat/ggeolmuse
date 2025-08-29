package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, String> {

  // 사용자별 거래 내역 조회 (페이지네이션)
  Page<Trade> findByUserIdOrderByExecutedAtDesc(String userId, Pageable pageable);

  // 특정 종목 거래 내역 조회
  List<Trade> findByUserIdAndSymbolOrderByExecutedAtDesc(String userId, String symbol);

  // 특정 기간 거래 내역 조회
  @Query("SELECT t FROM Trade t WHERE t.userId = :userId AND t.tradeDate BETWEEN :startDate AND :endDate ORDER BY t.executedAt DESC")
  List<Trade> findTradesByDateRange(@Param("userId") String userId, 
                                   @Param("startDate") LocalDate startDate, 
                                   @Param("endDate") LocalDate endDate);
}