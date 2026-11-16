package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.StockSplit;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockSplitRepository extends JpaRepository<StockSplit, Long> {

  boolean existsBySymbolAndSplitDate(String symbol, LocalDate splitDate);

  Optional<StockSplit> findBySymbolAndSplitDate(String symbol, LocalDate splitDate);
}
