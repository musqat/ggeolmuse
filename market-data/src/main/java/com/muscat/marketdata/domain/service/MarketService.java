package com.muscat.marketdata.domain.service;


import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface MarketService {

    /**
     * OHLC 가격 조회
     * @param symbol 종목 심볼 (예: AAPL)
     * @param date 조회 날짜 (yyyy-MM-dd)
     * @return OHLC 가격 정보
     */
    OHLCPriceDto getOHLCPrice(String symbol, LocalDate date);

    /**
     * 현재가 조회 (가장 최근 데이터)
     * @param symbol 종목 심볼 (예: AAPL)
     * @return 현재가 정보
     */
    StockPriceDto getCurrentPrice(String symbol);

    /**
     * 배당 이력 조회
     * @param symbol 종목 심볼 (예: AAPL)
     * @param startDate 시작일 (옵션)
     * @param endDate 종료일 (옵션)
     * @return 배당 이력 목록
     */
    List<DividendDto> getDividendHistory(String symbol, LocalDate startDate, LocalDate endDate);

    // 여러 심볼의 OHLC 데이터를 한 번에 조회
    List<OHLCPriceDto> getMultipleOHLCPrices(List<String> symbols, LocalDate startDate, LocalDate endDate);

    // 배당이 지급된 날짜의 캔들 데이터만 조회
    List<OHLCPriceDto> getCandlesWithDividends(String symbol, LocalDate startDate, LocalDate endDate);

    // 특정 금액 이상의 배당을 지급하는 종목 검색
    List<DividendDto> findHighDividendStocks(BigDecimal minAmount, LocalDate fromDate);
}