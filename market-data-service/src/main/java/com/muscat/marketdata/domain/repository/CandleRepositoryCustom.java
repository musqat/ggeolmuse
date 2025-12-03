package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Candle;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CandleRepositoryCustom {

    // 여러 심볼의 캔들 데이터를 한 번에 조회
    List<Candle> findBySymbolsAndDateRange(List<String> symbols, LocalDate startDate, LocalDate endDate);

    // 배당이 지급된 날짜의 캔들만 조회
    List<Candle> findCandlesWithDividends(String symbol, LocalDate startDate, LocalDate endDate);

    // 최신 캔들 조회
    Optional<Candle> findLatestBySymbol(String symbol);

    // 특정 날짜 이전의 최신 캔들 조회
    Optional<Candle> findLatestBySymbolBeforeDate(String symbol, LocalDate date);

    // 여러 심볼의 최근 N일 캔들 데이터를 일괄 조회
    List<Candle> findRecentBySymbols(List<String> symbols, int daysBack);

    // 캔들 데이터를 가진 고유 종목 개수 조회
    long countDistinctSymbols();
}
