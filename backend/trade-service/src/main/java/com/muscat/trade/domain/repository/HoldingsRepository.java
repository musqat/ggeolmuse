package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.Holdings;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// Holdings Repository
@Repository
public interface HoldingsRepository extends JpaRepository<Holdings, Long>, HoldingsRepositoryCustom {

  // 사용자 ID로 보유 종목 전체 조회
  List<Holdings> findByUserId(String userId);

  // 사용자 ID와 계좌 ID로 보유 종목 조회
  List<Holdings> findByUserIdAndAccountId(String userId, Long accountId);

  // 심볼로 보유 종목 조회 (배당 처리용)
  List<Holdings> findBySymbol(String symbol);

  // 비관적 락으로 보유 종목 조회 (매수/매도 동시성 제어)
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT h FROM Holdings h WHERE h.userId = :userId AND h.accountId = :accountId AND h.symbol = :symbol")
  Optional<Holdings> findByUserIdAndAccountIdAndSymbolWithLock(@Param("userId") String userId,
      @Param("accountId") Long accountId,
      @Param("symbol") String symbol);

  // 사용자 ID와 심볼로 보유 종목 조회
  Optional<Holdings> findByUserIdAndSymbol(String userId, String symbol);

  // 계좌 삭제 시 모든 보유 자산 삭제
  void deleteByAccountId(Long accountId);
}
