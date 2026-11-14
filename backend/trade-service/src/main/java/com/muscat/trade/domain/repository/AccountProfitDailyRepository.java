package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.AccountProfitDaily;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountProfitDailyRepository extends JpaRepository<AccountProfitDaily, Long> {

  List<AccountProfitDaily> findByUserIdAndAccountIdOrderByTradeDateAsc(String userId, Long accountId);

  void deleteByAccountIdAndTradeDateGreaterThanEqual(Long accountId, LocalDate from);

  void deleteByAccountId(Long accountId);

  // 계좌 ID 를 키로 트랜잭션 끝까지 잡는 Postgres 어드바이저리 잠금
  @Query(value = "select 1 from pg_advisory_xact_lock(:accountId)", nativeQuery = true)
  Integer lockAccount(@Param("accountId") Long accountId);
}
