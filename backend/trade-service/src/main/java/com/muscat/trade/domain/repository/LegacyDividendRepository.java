package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.LegacyDividend;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegacyDividendRepository extends JpaRepository<LegacyDividend, Long> {

  void deleteByAccountId(Long accountId);
}
