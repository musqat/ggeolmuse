package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.Holdings;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// Holdings Repository
@Repository
public interface HoldingsRepository extends JpaRepository<Holdings, Long>, HoldingsRepositoryCustom {

  // 사용자 ID로 보유 종목 전체 조회
  List<Holdings> findByUserId(String userId);

  // 사용자 ID와 계좌 ID로 보유 종목 조회
  List<Holdings> findByUserIdAndAccountId(String userId, Long accountId);

  // 사용자 · 계좌 · 종목으로 보유 단건 조회
  Optional<Holdings> findByUserIdAndAccountIdAndSymbol(String userId, Long accountId, String symbol);

  // 계좌 삭제 시 모든 보유 자산 삭제
  void deleteByAccountId(Long accountId);
}
