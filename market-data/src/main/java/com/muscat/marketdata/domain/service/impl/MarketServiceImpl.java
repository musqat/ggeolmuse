package com.muscat.marketdata.domain.service.impl;


import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.repository.CandleQueryRepository;
import com.muscat.marketdata.domain.repository.DividendRepository;
import com.muscat.marketdata.domain.repository.DividendQueryRepository;
import com.muscat.marketdata.domain.service.MarketService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketServiceImpl implements MarketService {

    private final CandleRepository candleRepository;
    private final CandleQueryRepository candleQueryRepository;
    private final DividendRepository dividendRepository;
    private final DividendQueryRepository dividendQueryRepository;
    private final MarketDataProperties properties;

    @Override
    public OHLCPriceDto getOHLCPrice(String symbol, LocalDate date) {
        log.debug("OHLC 조회 요청: symbol={}, date={}", symbol, date);
        
        String upperSymbol = symbol.toUpperCase();
        Optional<Candle> candle = candleRepository.findBySymbolAndDate(upperSymbol, date);
        
        if (candle.isEmpty()) {
            log.warn("OHLC 데이터 없음: symbol={}, date={}", symbol, date);
            return OHLCPriceDto.builder()
                .symbol(upperSymbol)
                .date(date)
                .available(false)
                .build();
        }
        
        Candle c = candle.get();
        OHLCPriceDto result = OHLCPriceDto.builder()
            .symbol(c.getSymbol())
            .date(c.getDate())
            .openPrice(c.getOpen())
            .highPrice(c.getHigh())
            .lowPrice(c.getLow())
            .closePrice(c.getClose())
            .adjustedClose(c.getAdjustedClose())
            .volume(c.getVolume())
            .currency(c.getCurrency())
            .available(true)
            .build();
        
        log.debug("OHLC 조회 성공: symbol={}, date={}, close={}", symbol, date, c.getClose());
        return result;
    }

    @Override
    public StockPriceDto getCurrentPrice(String symbol) {
        log.debug("현재가 조회 요청: symbol={}", symbol);
        
        String upperSymbol = symbol.toUpperCase();
        Optional<Candle> latestCandle = candleRepository.findFirstBySymbolOrderByDateDesc(upperSymbol);
        
        if (latestCandle.isEmpty()) {
            log.warn("현재가 데이터 없음: symbol={}", symbol);
            return StockPriceDto.builder()
                .symbol(upperSymbol)
                .available(false)
                .build();
        }
        
        Candle c = latestCandle.get();
        
        // 전일 캔들 조회
        Optional<Candle> previousCandle = candleRepository
            .findFirstBySymbolAndDateLessThanOrderByDateDesc(upperSymbol, c.getDate());
        
        BigDecimal previousClose = previousCandle.map(Candle::getClose).orElse(c.getClose());
        BigDecimal changePercent = calculateChangePercent(c.getClose(), previousClose);
        
        StockPriceDto result = StockPriceDto.builder()
            .symbol(c.getSymbol())
            .date(c.getDate())
            .currentPrice(c.getClose())
            .previousClose(previousClose) // 실제 전일 종가
            .changePercent(changePercent)  // 계산된 변화율
            .volume(c.getVolume())
            .currency(c.getCurrency())
            .available(true)
            .build();
        
        log.debug("현재가 조회 성공: symbol={}, price={}, previousClose={}, change={}%", 
                 symbol, c.getClose(), previousClose, changePercent);
        return result;
    }

    @Override
    public List<DividendDto> getDividendHistory(String symbol, LocalDate startDate, LocalDate endDate) {
        log.debug("배당 이력 조회 요청: symbol={}, startDate={}, endDate={}", symbol, startDate, endDate);
        
        String upperSymbol = symbol.toUpperCase();
        
        List<Dividend> dividends = dividendQueryRepository.findBySymbolsAndDateRange(
            List.of(upperSymbol), startDate, endDate);
        
        List<DividendDto> result = dividends.stream()
            .map(dividend -> DividendDto.builder()
                .symbol(dividend.getSymbol())
                .exDate(dividend.getExDate())
                .amount(dividend.getAmount())
                .currency(dividend.getCurrency())
                .source("MarketData")
                .build())
            .collect(Collectors.toList());
        
        log.debug("배당 이력 조회 성공: symbol={}, count={}", symbol, result.size());
        return result;
    }

    // 여러 심볼의 OHLC 데이터를 한 번에 조회 (성능 최적화)
    public List<OHLCPriceDto> getMultipleOHLCPrices(List<String> symbols, LocalDate startDate, LocalDate endDate) {
        log.debug("다중 OHLC 조회 요청: symbols={}, startDate={}, endDate={}", symbols, startDate, endDate);
        
        List<String> upperSymbols = symbols.stream().map(String::toUpperCase).toList();
        
        List<Candle> candles = candleQueryRepository.findBySymbolsAndDateRange(upperSymbols, startDate, endDate);
        
        List<OHLCPriceDto> result = candles.stream()
            .map(c -> OHLCPriceDto.builder()
                .symbol(c.getSymbol())
                .date(c.getDate())
                .openPrice(c.getOpen())
                .highPrice(c.getHigh())
                .lowPrice(c.getLow())
                .closePrice(c.getClose())
                .adjustedClose(c.getAdjustedClose())
                .volume(c.getVolume())
                .currency(c.getCurrency())
                .available(true)
                .build())
            .toList();
        
        log.debug("다중 OHLC 조회 성공: count={}", result.size());
        return result;
    }

    // 배당이 지급된 날짜의 캔들 데이터만 조회
    public List<OHLCPriceDto> getCandlesWithDividends(String symbol, LocalDate startDate, LocalDate endDate) {
        log.debug("배당 포함 캔들 조회 요청: symbol={}, startDate={}, endDate={}", symbol, startDate, endDate);
        
        String upperSymbol = symbol.toUpperCase();
        
        List<Candle> candlesWithDividends = candleQueryRepository.findCandlesWithDividends(upperSymbol, startDate, endDate);
        
        List<OHLCPriceDto> result = candlesWithDividends.stream()
            .map(c -> OHLCPriceDto.builder()
                .symbol(c.getSymbol())
                .date(c.getDate())
                .openPrice(c.getOpen())
                .highPrice(c.getHigh())
                .lowPrice(c.getLow())
                .closePrice(c.getClose())
                .adjustedClose(c.getAdjustedClose())
                .volume(c.getVolume())
                .currency(c.getCurrency())
                .available(true)
                .build())
            .toList();
        
        log.debug("배당 포함 캔들 조회 성공: count={}", result.size());
        return result;
    }

    // 특정 금액 이상의 배당을 지급하는 종목 검색
    public List<DividendDto> findHighDividendStocks(BigDecimal minAmount, LocalDate fromDate) {
        log.debug("고배당주 검색 요청: minAmount={}, fromDate={}", minAmount, fromDate);
        
        List<Dividend> highDividends = dividendQueryRepository.findHighDividendStocks(minAmount, fromDate);
        
        List<DividendDto> result = highDividends.stream()
            .map(dividend -> DividendDto.builder()
                .symbol(dividend.getSymbol())
                .exDate(dividend.getExDate())
                .amount(dividend.getAmount())
                .currency(dividend.getCurrency())
                .source("MarketData")
                .build())
            .toList();
        
        log.debug("고배당주 검색 성공: count={}", result.size());
        return result;
    }
    
    /**
     * 변화율 계산 헬퍼 메서드
     */
    private BigDecimal calculateChangePercent(BigDecimal currentPrice, BigDecimal previousClose) {
        if (currentPrice == null || previousClose == null || 
            previousClose.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        
        return currentPrice.subtract(previousClose)
            .divide(previousClose, properties.getCalculation().getPercentScale(), RoundingMode.HALF_UP)
            .multiply(properties.getCalculation().getPercentageMultiplier());
    }
}