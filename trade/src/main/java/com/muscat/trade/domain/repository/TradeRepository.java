package com.muscat.trade.domain.repository;

import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

  // 특정 종목의 마지막 매수 거래 조회 (시간여행 방지용)
  @Query("SELECT t FROM Trade t WHERE t.userId = :userId AND t.accountId = :accountId AND t.symbol = :symbol AND t.tradeType = :tradeType ORDER BY t.tradeDate DESC, t.executedAt DESC")
  Optional<Trade> findFirstByUserIdAndAccountIdAndSymbolAndTradeTypeOrderByTradeDateDescExecutedAtDesc(
      @Param("userId") String userId, 
      @Param("accountId") String accountId, 
      @Param("symbol") String symbol, 
      @Param("tradeType") TradeType tradeType);

  // FIFO 검증용: 특정일 이전의 모든 거래 조회 (매수/매도 포함)
  @Query("SELECT t FROM Trade t WHERE t.userId = :userId AND t.accountId = :accountId AND t.symbol = :symbol AND t.tradeDate < :beforeDate ORDER BY t.tradeDate ASC, t.executedAt ASC")
  List<Trade> findTradesByUserAccountSymbolBeforeDate(
      @Param("userId") String userId, 
      @Param("accountId") String accountId, 
      @Param("symbol") String symbol, 
      @Param("beforeDate") LocalDate beforeDate);
}