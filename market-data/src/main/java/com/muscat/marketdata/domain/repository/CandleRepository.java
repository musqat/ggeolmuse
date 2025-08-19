package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.CandleId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandleRepository extends JpaRepository<Candle, CandleId> {

  // 심볼 + 날짜 범위 조회
  List<Candle> findByIdSymbolAndIdDateBetweenOrderByIdDateAsc(
      String symbol,
      LocalDate startDate,
      LocalDate endDate
  );

  // 특정 날짜 모든 종목 조회
  List<Candle> findByIdDate(LocalDate date);
}
