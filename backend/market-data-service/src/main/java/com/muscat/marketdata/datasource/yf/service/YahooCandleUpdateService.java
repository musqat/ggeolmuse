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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
            List<Candle> candles = candleSource.fetchDailyAdjusted(symbol, from, to);
            if (candles == null || candles.isEmpty()) {
                log.debug("[YF-캔들저장] 데이터 없음: symbol={}", symbol);
                return 0;
            }

            mergeIntoExisting(symbol, from, to, candles);

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

    /**
     * 받아온 캔들을 기존 행과 대조해 없는 것만 넣고 값이 달라진 것만 고친다.
     * adjusted_close 는 배당이 나올 때마다 과거 날짜까지 바뀌므로 기존 행도 다시 본다.
     */
    private void mergeIntoExisting(String symbol, LocalDate from, LocalDate to,
        List<Candle> candles) {

        Map<String, Candle> existing = candleRepository
            .findBySymbolAndDateBetweenOrderByDateAsc(symbol, from, to)
            .stream()
            .collect(Collectors.toMap(YahooCandleUpdateService::key, c -> c, (a, b) -> a));

        List<Candle> inserts = new ArrayList<>();
        int updated = 0;

        for (Candle fresh : candles) {
            Candle prev = existing.get(key(fresh));
            if (prev == null) {
                inserts.add(fresh);
            } else if (changed(prev, fresh)) {
                copyValues(prev, fresh);
                updated++;
            }
        }

        if (!inserts.isEmpty()) {
            candleRepository.saveAll(inserts);
        }

        log.debug("[YF-캔들저장] symbol={}, 신규={}, 갱신={}", symbol, inserts.size(), updated);
    }

    // UNIQUE(symbol, date, currency) 와 같은 기준
    private static String key(Candle c) {
        return c.getDate() + "|" + c.getCurrency();
    }

    private static boolean changed(Candle prev, Candle fresh) {
        return differs(prev.getOpen(), fresh.getOpen())
            || differs(prev.getHigh(), fresh.getHigh())
            || differs(prev.getLow(), fresh.getLow())
            || differs(prev.getClose(), fresh.getClose())
            || differs(prev.getAdjustedClose(), fresh.getAdjustedClose())
            || differs(prev.getDividendAmount(), fresh.getDividendAmount())
            || differs(prev.getSplitCoefficient(), fresh.getSplitCoefficient())
            || !Objects.equals(prev.getVolume(), fresh.getVolume());
    }

    // BigDecimal 은 자릿수가 달라도 같은 값일 수 있어 equals 를 쓰지 않는다
    private static boolean differs(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a != b;
        }
        return a.compareTo(b) != 0;
    }

    private static void copyValues(Candle target, Candle source) {
        target.setOpen(source.getOpen());
        target.setHigh(source.getHigh());
        target.setLow(source.getLow());
        target.setClose(source.getClose());
        target.setVolume(source.getVolume());
        target.setAdjustedClose(source.getAdjustedClose());
        target.setDividendAmount(source.getDividendAmount());
        target.setSplitCoefficient(source.getSplitCoefficient());
    }
}
