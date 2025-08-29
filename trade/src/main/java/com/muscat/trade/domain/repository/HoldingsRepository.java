package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.Holdings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingsRepository extends JpaRepository<Holdings, String> {

  // 사용자별 보유종목 전체 조회
  List<Holdings> findByUserId(String userId);

  // 사용자 + 계좌별 보유종목 조회
  List<Holdings> findByUserIdAndAccountId(String userId, String accountId);

  // 특정 종목 보유현황 조회 (unique constraint 기준)
  Optional<Holdings> findByUserIdAndAccountIdAndSymbol(String userId, String accountId, String symbol);

  // 배당 계산이 필요한 종목들 조회 (마지막 배당 계산일이 특정 날짜 이전)
  @Query("SELECT h FROM Holdings h WHERE h.lastDividendCalculated < :date AND h.totalQuantity > 0")
  List<Holdings> findHoldingsNeedingDividendCalculation(@Param("date") LocalDate date);

}