package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface DividendRepository extends JpaRepository<Dividend, Long> {

    List<Dividend> findBySymbolOrderByExDateDesc(String symbol);

    List<Dividend> findByExDateBetween(LocalDate start, LocalDate end);
}
