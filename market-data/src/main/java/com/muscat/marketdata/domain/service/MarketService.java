package com.muscat.marketdata.domain.service;


import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import java.time.LocalDate;

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
}