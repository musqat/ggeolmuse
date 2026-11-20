package com.muscat.backtest.domain.repository;

import com.muscat.backtest.domain.entity.BacktestHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface BacktestHistoryRepository extends JpaRepository<BacktestHistory, Long> {

  // 사용자별 백테스트 히스토리 조회 (페이징)
  Page<BacktestHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

  // 한 사용자의 이력 전부 지우기(지운 행 수)
  @Transactional
  long deleteByUserId(String userId);
}
