package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Dividend;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dividend Repository Custom Interface
 */
public interface DividendRepositoryCustom {

    /**
     * 여러 심볼의 배당 이력을 한 번에 조회
     */
    List<Dividend> findBySymbolsAndDateRange(List<String> symbols, LocalDate startDate, LocalDate endDate);

    /**
     * 특정 금액 이상의 배당을 지급하는 종목 검색
     */
    List<Dividend> findHighDividendStocks(BigDecimal minAmount, LocalDate fromDate);
}
