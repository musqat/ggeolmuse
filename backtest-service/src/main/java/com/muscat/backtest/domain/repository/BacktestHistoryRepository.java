package com.muscat.backtest.domain.repository;

import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.domain.entity.BacktestHistory;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BacktestHistoryRepository extends JpaRepository<BacktestHistory, String> {

  // 사용자별 백테스트 히스토리 조회 (페이징)
  Page<BacktestHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

  // 사용자별 특정 타입 백테스트 히스토리 조회
  List<BacktestHistory> findByUserIdAndBacktestTypeOrderByCreatedAtDesc(String userId,
      BacktestType backtestType);

  // 기간별 백테스트 히스토리 조회
  List<BacktestHistory> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(String userId,
      LocalDateTime startDate, LocalDateTime endDate);

  // 전체 백테스트 실행 통계 (관리자용)
  long countByBacktestType(BacktestType backtestType);
}