package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// Dividend Repository
@Repository
public interface DividendRepository extends JpaRepository<Dividend, Long>, DividendRepositoryCustom {

    Optional<Dividend> findBySymbolAndExDate(String symbol, LocalDate exDate);

    // 종목별 from 이후 배당 합
    interface SymbolAmount {
        String getSymbol();

        BigDecimal getAmount();
    }

    @Query("select d.symbol as symbol, sum(d.amount) as amount from Dividend d"
        + " where d.symbol in :symbols and d.exDate >= :from group by d.symbol")
    List<SymbolAmount> sumAmountBySymbolsSince(@Param("symbols") Collection<String> symbols, @Param("from") LocalDate from);
}
