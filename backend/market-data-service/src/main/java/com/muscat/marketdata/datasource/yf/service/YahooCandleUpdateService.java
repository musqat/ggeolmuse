package com.muscat.marketdata.datasource.yf.service;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.mapper.MarketDataMapper;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.repository.DividendRepository;
import com.muscat.marketdata.infra.kafka.DividendEventProducer;

import java.util.Comparator;
import com.muscat.marketdata.datasource.common.MarketDataProvider.CandleSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.DividendSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Yahoo Finance 캔들 및 배당 데이터 업데이트 서비스
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
    private final AssetRepository assetRepository;

    private final DividendEventProducer dividendEventProducer;

    // self-injection: saveBoth에서 proxy 통해 호출해야 REQUIRES_NEW가 실제로 적용됨
    @Lazy
    @Autowired
    private YahooCandleUpdateService self;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int saveCandles(String symbol, LocalDate from, LocalDate to) {
        try {
            // DB 최신 날짜 이후만 수집 (중복 방지)
            LocalDate effectiveFrom = candleRepository.findLatestBySymbol(symbol)
                .map(c -> c.getDate().plusDays(1))
                .orElse(from);

            if (!effectiveFrom.isBefore(to)) {
                log.debug("[YF-캔들저장] 이미 최신 데이터: symbol={}, latestDate={}", symbol, effectiveFrom.minusDays(1));
                return 0;
            }

            List<Candle> candles = candleSource.fetchDailyAdjusted(symbol, effectiveFrom, to);
            if (candles == null || candles.isEmpty()) {
                log.debug("[YF-캔들저장] 데이터 없음: symbol={}", symbol);
                return 0;
            }

            candleRepository.saveAll(candles);

            // 최신 캔들을 asset에 비정규화 (summary 조회 성능용)
            candles.stream()
                .max(Comparator.comparing(Candle::getDate))
                .ifPresent(latest -> assetRepository.findById(symbol).ifPresent(asset -> {
                    if (asset.getLatestDate() == null || !latest.getDate().isBefore(asset.getLatestDate())) {
                        asset.setLatestClose(latest.getClose());
                        asset.setLatestDate(latest.getDate());
                        assetRepository.save(asset);
                    }
                }));

            log.info("[YF-캔들저장] 완료: symbol={}, count={}", symbol, candles.size());
            return candles.size();
        } catch (Exception e) {
            log.error("[YF-캔들저장] 실패: symbol={}, error={}", symbol, e.getMessage());
            return 0;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int saveDividends(String symbol, LocalDate from, LocalDate to) {
        try {
            List<DividendDto> dtos = dividendSource.fetchDividends(symbol, from, to);
            if (dtos == null || dtos.isEmpty()) {
                log.debug("[YF-배당저장] 데이터 없음: symbol={}", symbol);
                return 0;
            }

            var entities = MarketDataMapper.toDividends(dtos);

            // 중복 배당 필터링 (이미 존재하는 배당은 제외)
            var newDividends = entities.stream()
                .filter(d -> !dividendRepository.existsBySymbolAndExDate(d.getSymbol(), d.getExDate()))
                .toList();

            if (newDividends.isEmpty()) {
                log.debug("[YF-배당저장] 모두 기존 데이터, 저장 스킵: symbol={}", symbol);
                return 0;
            }

            dividendRepository.saveAll(newDividends);

            // Kafka 이벤트 발행 (배치로 처리)
            dividendEventProducer.publishBatch(newDividends);
            log.debug("[YF-배당저장] 완료: symbol={}, count={}", symbol, newDividends.size());

            return newDividends.size();
        } catch (Exception e) {
            log.error("[YF-배당저장] 실패: symbol={}, error={}", symbol, e.getMessage());
            return 0;
        }
    }

    public int saveBoth(String symbol, LocalDate from, LocalDate to) {
        int c = self.saveCandles(symbol, from, to);
        int d = self.saveDividends(symbol, from, to);
        log.info("[YF-데이터저장] {} 캔들={}, 배당={}", symbol, c, d);
        return c + d;
    }
}
