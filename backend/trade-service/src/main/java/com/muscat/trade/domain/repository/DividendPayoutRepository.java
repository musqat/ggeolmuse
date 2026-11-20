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

  // 탈퇴한 사용자의 행 전부 삭제(Keycloak ID)
  void deleteByUserId(String userId);
}
