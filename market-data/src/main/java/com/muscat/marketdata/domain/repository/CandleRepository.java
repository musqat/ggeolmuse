package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Candle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 캔들 데이터 기본 CRUD Repository
 * 복잡한 쿼리는 CandleQueryRepository 사용
 */
@Repository
public interface CandleRepository extends JpaRepository<Candle, Long> {

    List<Candle> findBySymbolAndDateBetweenOrderByDateAsc(
            String symbol, LocalDate startDate, LocalDate endDate);

    List<Candle> findByDate(LocalDate date);
    
    Optional<Candle> findBySymbolAndDate(String symbol, LocalDate date);
    
    Optional<Candle> findFirstBySymbolOrderByDateDesc(String symbol);
    
    // 전일 캔들 조회 (특정 날짜 이전 가장 최근 캔들)
    Optional<Candle> findFirstBySymbolAndDateLessThanOrderByDateDesc(String symbol, LocalDate date);
    
    boolean existsBySymbolAndDate(String symbol, LocalDate date);
}
