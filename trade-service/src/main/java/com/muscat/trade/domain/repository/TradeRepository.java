package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Trade Repository
 */
@Repository
public interface TradeRepository extends JpaRepository<Trade, String>, TradeRepositoryCustom {

  Page<Trade> findByUserIdOrderByExecutedAtDesc(String userId, Pageable pageable);

  List<Trade> findByUserIdAndSymbolOrderByExecutedAtDesc(String userId, String symbol);

  List<Trade> findByUserIdAndSymbolOrderByTradeDateAsc(String userId, String symbol);

  @Query("SELECT DISTINCT t.symbol FROM Trade t WHERE t.userId = :userId")
  List<String> findDistinctSymbolsByUserId(String userId);

  // 계좌 삭제 시 모든 거래 내역 삭제
  void deleteByAccountId(Long accountId);
}
