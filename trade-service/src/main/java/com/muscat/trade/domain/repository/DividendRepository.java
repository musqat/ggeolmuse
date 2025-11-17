package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.Dividend;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DividendRepository extends JpaRepository<Dividend, String> {

  // 특정 Trade의 특정 배당일에 배당이 이미 지급되었는지 확인 (Trade 단위 배당 추적)
  boolean existsByTradeIdAndDividendDate(String tradeId, LocalDate dividendDate);

  // 특정 사용자의 특정 종목 배당 내역 조회 (최신순)
  List<Dividend> findByUserIdAndSymbolOrderByDividendDateDesc(String userId, String symbol);
}
