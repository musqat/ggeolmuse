package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.entity.DividendId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DividendRepository extends JpaRepository<Dividend, DividendId> {

  // 심볼별 배당 이력
  List<Dividend> findByIdSymbolOrderByIdExDateDesc(String symbol);

  // 배당락일 범위 조회
  List<Dividend> findByIdExDateBetween(LocalDate start, LocalDate end);
}
