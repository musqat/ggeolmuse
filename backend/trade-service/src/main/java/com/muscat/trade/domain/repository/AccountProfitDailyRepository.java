package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.AccountProfitDaily;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountProfitDailyRepository extends JpaRepository<AccountProfitDaily, Long> {

  List<AccountProfitDaily> findByUserIdAndAccountIdOrderByTradeDateAsc(String userId, Long accountId);

  void deleteByAccountIdAndTradeDateGreaterThanEqual(Long accountId, LocalDate from);

  void deleteByAccountId(Long accountId);
}
