package com.muscat.backtest.domain.repository;

import com.muscat.backtest.domain.entity.BacktestHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BacktestHistoryRepository extends JpaRepository<BacktestHistory, Long> {

  // 사용자별 백테스트 히스토리 조회 (페이징)
  Page<BacktestHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}