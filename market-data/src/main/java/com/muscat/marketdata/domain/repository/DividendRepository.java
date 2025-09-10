package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 배당 데이터 기본 CRUD Repository
 * 복잡한 쿼리는 DividendQueryRepository 사용
 */
@Repository
public interface DividendRepository extends JpaRepository<Dividend, Long> {

    List<Dividend> findBySymbolOrderByExDateDesc(String symbol);

    List<Dividend> findByExDateBetween(LocalDate start, LocalDate end);
    
    List<Dividend> findBySymbolAndExDateBetweenOrderByExDateDesc(String symbol, LocalDate start, LocalDate end);
    
    List<Dividend> findBySymbolAndExDateGreaterThanEqualOrderByExDateDesc(String symbol, LocalDate startDate);
    
    List<Dividend> findBySymbolAndExDateLessThanEqualOrderByExDateDesc(String symbol, LocalDate endDate);

    Optional<Dividend> findFirstBySymbolOrderByExDateDesc(String symbol);
    
    boolean existsBySymbolAndExDate(String symbol, LocalDate exDate);
}
