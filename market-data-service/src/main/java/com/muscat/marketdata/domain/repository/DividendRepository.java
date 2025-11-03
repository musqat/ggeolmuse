package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import org.springframework.stereotype.Repository;

/**
 * Dividend Repository
 */
@Repository
public interface DividendRepository extends JpaRepository<Dividend, Long>, DividendRepositoryCustom {

    /**
     * 배당 데이터 존재 여부 확인
     */
    boolean existsBySymbolAndExDate(String symbol, LocalDate exDate);
}
