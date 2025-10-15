package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Candle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface CandleRepository extends JpaRepository<Candle, Long> {

    List<Candle> findBySymbolAndDateBetweenOrderByDateAsc(
            String symbol, LocalDate startDate, LocalDate endDate);

    List<Candle> findByDate(LocalDate date);

    Optional<Candle> findBySymbolAndDate(String symbol, LocalDate date);

    boolean existsBySymbolAndDate(String symbol, LocalDate date);
}
