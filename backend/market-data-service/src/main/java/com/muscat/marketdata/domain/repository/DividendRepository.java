package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Repository;

// Dividend Repository
@Repository
public interface DividendRepository extends JpaRepository<Dividend, Long>, DividendRepositoryCustom {

    Optional<Dividend> findBySymbolAndExDate(String symbol, LocalDate exDate);
}
