package com.muscat.marketdata.datasource.alphavantage.service;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.mapper.MarketDataMapper;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.repository.DividendRepository;
import com.muscat.marketdata.infra.kafka.DividendEventProducer;
import com.muscat.marketdata.infra.kafka.PriceEventProducer;
import com.muscat.marketdata.datasource.common.MarketDataProvider.CandleSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.DividendSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AlphaVantage 기반 캔들 및 배당 데이터 업데이트 서비스
 */
@Slf4j
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "marketdata.provider",
    havingValue = "alphavantage"
)
@RequiredArgsConstructor
public class AlphaVantageCandleUpdateService implements com.muscat.marketdata.domain.service.CandleUpdateService {

    private final CandleSource candleSource;
    private final DividendSource dividendSource;

    private final CandleRepository candleRepository;
    private final DividendRepository dividendRepository;

    private final PriceEventProducer priceEventProducer;
    private final DividendEventProducer dividendEventProducer;

    @Transactional
    public int saveCandles(String symbol, LocalDate from, LocalDate to) {
        // 증분 수집: 마지막 캔들 날짜 확인
        Optional<Candle> lastCandle = candleRepository.findFirstBySymbolOrderByDateDesc(symbol);

        if (lastCandle.isPresent()) {
            LocalDate lastDate = lastCandle.get().getDate();
            LocalDate expectedLatestDate = getExpectedLatestTradingDate(to);

            // 이미 최신 데이터가 있으면 스킵
            if (!lastDate.isBefore(expectedLatestDate)) {
                log.info("[데이터수집 스킵] {} - 이미 최신 데이터 존재 (마지막={}, 기대={}, 요청={} ~ {})",
                        symbol, lastDate, expectedLatestDate, from, to);
                return 0;
            }

            // 마지막 날짜 다음날부터 수집
            from = lastDate.plusDays(1);
            log.info("[증분 수집] {} - 마지막 날짜 이후 데이터 수집 ({} ~ {})", symbol, from, to);
        } else {
            log.info("[전체 수집] {} - 최초 데이터 수집 ({} ~ {})", symbol, from, to);
        }

        List<Candle> candles = candleSource.fetchDailyAdjusted(symbol, from, to);
        if (candles == null || candles.isEmpty()) {
            log.debug("[수집 결과 없음] {} - 기간 내 데이터 없음", symbol);
            return 0;
        }

        // 중복 캔들 필터링 (Batch Query 방식: 1회 쿼리로 모든 날짜 조회)
        Set<LocalDate> existingDates = candleRepository.findBySymbol(symbol)
            .stream()
            .map(Candle::getDate)
            .collect(Collectors.toSet());

        // 메모리에서 필터링 (DB 쿼리 없음)
        List<Candle> newCandles = candles.stream()
            .filter(candle -> !existingDates.contains(candle.getDate()))
            .toList();

        if (newCandles.isEmpty()) {
            log.debug("[캔들] {} - 모두 기존 데이터, 저장 스킵 (기존={}개)", symbol, existingDates.size());
            return 0;
        }

        // DB에 저장
        candleRepository.saveAll(newCandles);

        // Kafka 이벤트 발행 (배치로 처리)
        priceEventProducer.publishBatch(newCandles);
        log.debug("[주가 이벤트 발행] symbol={}, count={}", symbol, newCandles.size());

        return newCandles.size();
    }

    /**
     * 기대되는 최신 거래일 계산 (주말 제외)
     *
     * @param requestedDate 요청된 종료 날짜
     * @return 실제 기대되는 최신 거래일
     */
    private LocalDate getExpectedLatestTradingDate(LocalDate requestedDate) {
        LocalDate date = requestedDate;

        // 주말이면 직전 금요일로 조정
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.minusDays(1);
        }

        return date;
    }

    @Transactional
    public int saveDividends(String symbol, LocalDate from, LocalDate to) {
        List<DividendDto> dtos = dividendSource.fetchDividends(symbol, from, to);
        if (dtos == null || dtos.isEmpty()) {
            return 0;
        }
        var entities = MarketDataMapper.toDividends(dtos);

        // 중복 배당 필터링 (이미 존재하는 배당은 제외)
        List<Dividend> newDividends = entities.stream()
            .filter(dividend -> !dividendRepository.existsBySymbolAndExDate(dividend.getSymbol(), dividend.getExDate()))
            .toList();

        if (newDividends.isEmpty()) {
            log.debug("[배당금] {} - 모두 기존 데이터, 저장 스킵", symbol);
            return 0;
        }

        dividendRepository.saveAll(newDividends);

        //Kafka 이벤트 발행 (배치로 처리)
        dividendEventProducer.publishBatch(newDividends);
        log.debug("[배당금 이벤트 발행] symbol={}, count={}", symbol, newDividends.size());

        return newDividends.size();
    }

    @Transactional
    public int saveBoth(String symbol, LocalDate from, LocalDate to) {
        int c = saveCandles(symbol, from, to);
        int d = saveDividends(symbol, from, to);
        log.info("[데이터저장] {} 캔들={}, 배당={}", symbol, c, d);
        return c + d;
    }
}
