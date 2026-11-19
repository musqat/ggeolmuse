package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.DividendPayout;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DividendPayoutRepository extends JpaRepository<DividendPayout, Long>, DividendPayoutRepositoryCustom {

  List<DividendPayout> findByAccountIdAndSymbol(Long accountId, String symbol);

  List<DividendPayout> findByUserId(String userId);

  List<DividendPayout> findByUserIdAndNetAmountGreaterThanOrderByExDateDesc(String userId, BigDecimal netAmount);

  void deleteByAccountId(Long accountId);
}
