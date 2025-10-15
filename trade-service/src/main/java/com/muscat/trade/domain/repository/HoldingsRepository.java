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

@Repository
public interface HoldingsRepository extends JpaRepository<Holdings, String> {

  List<Holdings> findByUserId(String userId);

  List<Holdings> findByUserIdAndAccountId(String userId, Long accountId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT h FROM Holdings h WHERE h.userId = :userId AND h.accountId = :accountId AND h.symbol = :symbol")
  Optional<Holdings> findByUserIdAndAccountIdAndSymbolWithLock(@Param("userId") String userId,
      @Param("accountId") Long accountId,
      @Param("symbol") String symbol);

  Optional<Holdings> findByUserIdAndSymbol(String userId, String symbol);
}