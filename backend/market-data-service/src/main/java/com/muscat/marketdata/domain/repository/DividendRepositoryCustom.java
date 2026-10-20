package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Dividend;
import java.time.LocalDate;
import java.util.List;

public interface DividendRepositoryCustom {

    // 여러 심볼의 배당 이력을 한 번에 조회
    List<Dividend> findBySymbolsAndDateRange(List<String> symbols, LocalDate startDate, LocalDate endDate);
}
