package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.dto.TradePosition;
import com.muscat.trade.domain.entity.DividendPayout;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DividendPayoutRepository extends JpaRepository<DividendPayout, Long> {

  List<DividendPayout> findByAccountIdAndSymbol(Long accountId, String symbol);

  List<DividendPayout> findByUserIdAndNetAmountGreaterThanOrderByExDateDesc(String userId, BigDecimal netAmount);

  void deleteByAccountId(Long accountId);

  @Query("select distinct new com.muscat.trade.domain.dto.TradePosition(p.userId, p.accountId, p.symbol) "
    + "from DividendPayout p")
  List<TradePosition> findDistinctPositions();
}
