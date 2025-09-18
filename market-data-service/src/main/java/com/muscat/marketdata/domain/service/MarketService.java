package com.muscat.marketdata.domain.service;


import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface MarketService {

    // 특정 날짜의 OHLC 가격 조회
    OHLCPriceDto getOHLCPrice(String symbol, LocalDate date);

    // 종목의 현재가 조회
    StockPriceDto getCurrentPrice(String symbol);

    // 종목의 배당 이력 조회 (기간 옵션)
    List<DividendDto> getDividendHistory(String symbol, LocalDate startDate, LocalDate endDate);

    // 여러 종목의 OHLC 데이터 일괄 조회
    List<OHLCPriceDto> getMultipleOHLCPrices(List<String> symbols, LocalDate startDate, LocalDate endDate);

    // 배당 지급일의 캠들 데이터 조회
    List<OHLCPriceDto> getCandlesWithDividends(String symbol, LocalDate startDate, LocalDate endDate);

    // 고배당 주식 검색 (최소 금액 이상)
    List<DividendDto> findHighDividendStocks(BigDecimal minAmount, LocalDate fromDate);
}