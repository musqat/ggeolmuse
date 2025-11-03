package com.muscat.marketdata.datasource.yf.service;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.mapper.MarketDataMapper;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.repository.DividendRepository;
import com.muscat.marketdata.infra.kafka.DividendEventProducer;
import com.muscat.marketdata.infra.kafka.PriceEventProducer;
import com.muscat.marketdata.datasource.common.MarketDataProvider.CandleSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.DividendSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Yahoo Finance 캔들 및 배당 데이터 업데이트 서비스
 *
 * 데이터 저장 및 Kafka 이벤트 발행을 처리합니다.
 */
@Slf4j
@Service
@ConditionalOnProperty(
    name = "marketdata.provider",
    havingValue = "yahoo"
)
@RequiredArgsConstructor
public class YahooCandleUpdateService implements com.muscat.marketdata.domain.service.CandleUpdateService {

    private final CandleSource candleSource;
    private final DividendSource dividendSource;

    private final CandleRepository candleRepository;
    private final DividendRepository dividendRepository;

    private final PriceEventProducer priceEventProducer;
    private final DividendEventProducer dividendEventProducer;

    @Transactional
    public int saveCandles(String symbol, LocalDate from, LocalDate to) {
        try {
            List<Candle> candles = candleSource.fetchDailyAdjusted(symbol, from, to);
            if (candles == null || candles.isEmpty()) {
                log.debug("[YF-캔들저장] 데이터 없음: symbol={}", symbol);
                return 0;
            }

            // DB에 저장
            candleRepository.saveAll(candles);

            // Kafka 이벤트 발행 (배치로 처리)
            priceEventProducer.publishBatch(candles);
            log.debug("[YF-캔들저장] 완료: symbol={}, count={}", symbol, candles.size());

            return candles.size();
        } catch (Exception e) {
            log.error("[YF-캔들저장] 실패: symbol={}, error={}", symbol, e.getMessage());
            return 0;
        }
    }

    @Transactional
    public int saveDividends(String symbol, LocalDate from, LocalDate to) {
        try {
            List<DividendDto> dtos = dividendSource.fetchDividends(symbol, from, to);
            if (dtos == null || dtos.isEmpty()) {
                log.debug("[YF-배당저장] 데이터 없음: symbol={}", symbol);
                return 0;
            }

            var entities = MarketDataMapper.toDividends(dtos);
            dividendRepository.saveAll(entities);

            // Kafka 이벤트 발행 (배치로 처리)
            dividendEventProducer.publishBatch(entities);
            log.debug("[YF-배당저장] 완료: symbol={}, count={}", symbol, entities.size());

            return entities.size();
        } catch (Exception e) {
            log.error("[YF-배당저장] 실패: symbol={}, error={}", symbol, e.getMessage());
            return 0;
        }
    }

    @Transactional
    public int saveBoth(String symbol, LocalDate from, LocalDate to) {
        int c = saveCandles(symbol, from, to);
        int d = saveDividends(symbol, from, to);
        log.info("[YF-데이터저장] {} 캔들={}, 배당={}", symbol, c, d);
        return c + d;
    }
}
