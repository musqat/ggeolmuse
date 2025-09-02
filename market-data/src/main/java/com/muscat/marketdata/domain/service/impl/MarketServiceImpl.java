package com.muscat.marketdata.domain.service.impl;


import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.service.MarketService;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketServiceImpl implements MarketService {

    private final CandleRepository candleRepository;

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
        StockPriceDto result = StockPriceDto.builder()
            .symbol(c.getSymbol())
            .date(c.getDate())
            .currentPrice(c.getClose())
            .previousClose(c.getClose()) // TODO: 실제 전일 종가 조회 로직 추가 필요
            .changePercent(null) // TODO: 변화율 계산 로직 추가 필요
            .volume(c.getVolume())
            .currency(c.getCurrency())
            .available(true)
            .build();
        
        log.debug("현재가 조회 성공: symbol={}, price={}", symbol, c.getClose());
        return result;
    }
}