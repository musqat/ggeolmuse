package com.muscat.backtest.domain.repository;

import com.muscat.backtest.domain.entity.BacktestHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BacktestHistoryRepository extends JpaRepository<BacktestHistory, Long> {

  // 사용자별 백테스트 히스토리 조회 (페이징)
  Page<BacktestHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

  // 이메일로 남은 옛 행의 주인을 토큰 사용자 식별자로 바꿈
  @Modifying(clearAutomatically = true)
  @Query("update BacktestHistory h set h.userId = :to where h.userId = :from")
  int reassignOwner(@Param("from") String from, @Param("to") String to);
}
