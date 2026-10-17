package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.DividendPayout;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DividendPayoutRepository extends JpaRepository<DividendPayout, Long> {

  List<DividendPayout> findByAccountIdAndSymbol(Long accountId, String symbol);

  List<DividendPayout> findByUserIdAndNetAmountGreaterThanOrderByExDateDesc(String userId, BigDecimal netAmount);

  void deleteByAccountId(Long accountId);
}
