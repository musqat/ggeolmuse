package com.muscat.user.domain.account.repository;

import com.muscat.user.domain.account.entity.DividendSettlement;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DividendSettlementRepository extends JpaRepository<DividendSettlement, Long> {

  boolean existsByAccountIdAndSymbolAndExDate(Long accountId, String symbol, LocalDate exDate);
}
