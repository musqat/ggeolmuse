package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Dividend;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface DividendRepositoryCustom {

    // 여러 심볼의 배당 이력을 한 번에 조회
    List<Dividend> findBySymbolsAndDateRange(List<String> symbols, LocalDate startDate, LocalDate endDate);

    // 종목별 from 이후 배당 합. 한 번에 종가의 specialRatio 를 넘는 배당(특별 · 청산)은 뺌
    Map<String, BigDecimal> sumRegularAmountBySymbolsSince(Collection<String> symbols, LocalDate from, BigDecimal specialRatio);
}
