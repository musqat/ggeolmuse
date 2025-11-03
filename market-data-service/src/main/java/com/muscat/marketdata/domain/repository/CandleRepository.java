package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Candle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Candle Repository
 */
@Repository
public interface CandleRepository extends JpaRepository<Candle, Long>, CandleRepositoryCustom {

    /**
     * 특정 종목의 모든 캔들 데이터 조회 (중복 필터링용)
     */
    List<Candle> findBySymbol(String symbol);

    /**
     * 날짜 범위로 캔들 데이터 조회
     */
    List<Candle> findBySymbolAndDateBetweenOrderByDateAsc(
            String symbol, LocalDate startDate, LocalDate endDate);

    /**
     * 특정 날짜의 캔들 데이터 조회
     */
    Optional<Candle> findBySymbolAndDate(String symbol, LocalDate date);

    /**
     * 캔들 데이터 존재 여부 확인
     */
    boolean existsBySymbolAndDate(String symbol, LocalDate date);

    /**
     * 특정 종목의 가장 최신 캔들 데이터 조회 (증분 수집용)
     */
    Optional<Candle> findFirstBySymbolOrderByDateDesc(String symbol);

    /**
     * 캔들 데이터를 가진 고유 종목 개수 조회 (초기 수집 완료 여부 판단용)
     */
    @Query("SELECT COUNT(DISTINCT c.symbol) FROM Candle c")
    long countDistinctSymbols();
}
