package com.muscat.backtest.domain.repository;

import com.muscat.backtest.domain.entity.InvestmentBacktestResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InvestmentBacktestResultRepository extends
    JpaRepository<InvestmentBacktestResult, String> {

  // 사용자의 최신 백테스트 결과 조회
  Optional<InvestmentBacktestResult> findByUserId(String userId);

  // 계산이 필요한 사용자들 조회 (스케줄러용)
  @Query("SELECT r FROM InvestmentBacktestResult r WHERE r.nextScheduledAt <= :now AND r.status != 'RUNNING'")
  List<InvestmentBacktestResult> findUsersNeedingCalculation(@Param("now") LocalDateTime now);

  // 실행 중인 계산 조회 (장시간 실행 중인 것 체크용)
  @Query("SELECT r FROM InvestmentBacktestResult r WHERE r.status = 'RUNNING' AND r.updatedAt < :threshold")
  List<InvestmentBacktestResult> findStuckCalculations(@Param("threshold") LocalDateTime threshold);

  // 계산 상태별 조회
  List<InvestmentBacktestResult> findByStatus(InvestmentBacktestResult.CalculationStatus status);
}