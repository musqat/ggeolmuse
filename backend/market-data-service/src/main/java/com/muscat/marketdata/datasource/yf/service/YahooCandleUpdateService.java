package com.muscat.marketdata.datasource.yf.service;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.repository.DividendRepository;
import com.muscat.marketdata.domain.service.CollectionStats;
import com.muscat.marketdata.domain.service.DividendMerge;
import com.muscat.marketdata.domain.service.SplitScale;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

// Yahoo 캔들 · 배당을 저장하고 이벤트를 발행
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
    private final CollectionStats collectionStats;
    private final AssetEventProducer assetEventProducer;

    // 관리자 재수집 기본값과 같음(이 날부터 받을 때는 과거 종가 비교 안 함)
    private static final LocalDate FULL_HISTORY_FROM = LocalDate.of(1970, 1, 1);

    // candle 가격 · 배당 · 분할 계수 컬럼의 소수 자릿수
    private static final int DB_SCALE = 8;

    // 서버 차이(84종목 상대 차이 최대 1.4e-6)와 배당 변화(가장 작아도 2.7e-4) 사이의 adjclose 허용치
    private static final BigDecimal ADJUSTED_CLOSE_TOLERANCE = new BigDecimal("0.00001");

    // self-injection: saveBoth에서 proxy 통해 호출해야 REQUIRES_NEW가 실제로 적용됨
    @Lazy
    @Autowired
    private YahooCandleUpdateService self;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int saveCandles(String symbol, LocalDate from, LocalDate to) {
        try {
            List<Candle> candles = candleSource.fetchDailyAdjusted(symbol, from, to);
            if (candles == null || candles.isEmpty()) {
                collectionStats.recordSymbolEmpty();
                log.debug("[YF-캔들저장] 데이터 없음: symbol={}", symbol);
                return 0;
            }

            Map<String, Candle> existing = loadExisting(symbol, from, to);
            if (scaleChanged(existing, candles) && isPartialWindow(symbol, from)) {
                resyncFullHistory(symbol, to);
                return 0;
            }

            Merged merged = mergeIntoExisting(existing, candles);

            // 최신 캔들을 asset에 비정규화 (summary 조회 성능용)
            candles.stream()
                .max(Comparator.comparing(Candle::getDate))
                .ifPresent(latest -> assetRepository.findById(symbol).ifPresent(asset -> {
                    boolean touched = false;
                    if (asset.getLatestDate() == null || !latest.getDate().isBefore(asset.getLatestDate())) {
                        asset.setLatestClose(latest.getClose());
                        asset.setLatestDate(latest.getDate());
                        touched = true;
                    }
                    // 전 기간을 받았으면 받은 최신 봉 날짜 기록(분할 찾기가 이 날짜 뒤에 난 분할만 봄)
                    if (from != null && !from.isAfter(FULL_HISTORY_FROM)) {
                        asset.setFullHistoryThrough(latest.getDate());
                        touched = true;
                    }
                    if (touched) {
                        assetRepository.save(asset);
                    }
                }));

            collectionStats.recordSymbol(merged.inserted(), merged.updated());
            log.debug("[YF-캔들저장] 완료: symbol={}, 받음={}, 신규={}, 갱신={}",
                symbol, candles.size(), merged.inserted(), merged.updated());
            return candles.size();
        } catch (Exception e) {
            collectionStats.recordSymbolFailed();
            log.warn("[YF-캔들저장] 실패: symbol={}, error={}", symbol, e.getMessage());
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

            var rows = DividendMerge.changedRows(dividendRepository, dtos);
            if (rows.isEmpty()) {
                log.debug("[YF-배당저장] 바뀐 배당 없음, 저장 스킵: symbol={}", symbol);
                return 0;
            }

            dividendRepository.saveAll(rows);

            // Kafka 이벤트 발행 (배치로 처리)
            dividendEventProducer.publishBatch(rows);
            collectionStats.recordDividends(rows.size());
            log.debug("[YF-배당저장] 완료: symbol={}, count={}", symbol, rows.size());

            return rows.size();
        } catch (Exception e) {
            log.warn("[YF-배당저장] 실패: symbol={}, error={}", symbol, e.getMessage());
            return 0;
        }
    }

    public int saveBoth(String symbol, LocalDate from, LocalDate to) {
        int c = self.saveCandles(symbol, from, to);
        int d = self.saveDividends(symbol, from, to);
        log.debug("[YF-데이터저장] {} 캔들={}, 배당={}", symbol, c, d);
        return c + d;
    }

    private Map<String, Candle> loadExisting(String symbol, LocalDate from, LocalDate to) {
        return candleRepository
            .findBySymbolAndDateBetweenOrderByDateAsc(symbol, from, to)
            .stream()
            .collect(Collectors.toMap(YahooCandleUpdateService::key, c -> c, (a, b) -> a));
    }

    // 없는 행만 넣고 값이 달라진 행만 고침(adjusted_close 는 배당 때마다 과거까지 바뀌어 기존 행도 봄)
    private Merged mergeIntoExisting(Map<String, Candle> existing, List<Candle> candles) {
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

        return new Merged(inserts.size(), updated);
    }

    // 기존 데이터와 겹치는 날짜 중 가장 이른 날의 종가 비교(분할이 나면 과거 종가가 전부 분할 기준으로 바뀜)
    private static boolean scaleChanged(Map<String, Candle> existing, List<Candle> candles) {
        return candles.stream()
            .filter(fresh -> existing.containsKey(key(fresh)))
            .min(Comparator.comparing(Candle::getDate))
            .map(fresh -> SplitScale.isScaleChange(existing.get(key(fresh)).getClose(), fresh.getClose()))
            .orElse(false);
    }

    // 1970년부터 받을 때는 비교 안 함(비교하면 1970년 이전 데이터가 있는 종목에서 재수집이 되풀이됨)
    private boolean isPartialWindow(String symbol, LocalDate from) {
        return from != null && from.isAfter(FULL_HISTORY_FROM)
            && candleRepository.existsBySymbolAndDateBefore(symbol, from);
    }

    // 덮어쓰지 않아 재수집 요청이 실패해도 다음 수집에서 같은 차이가 다시 보임(주당 배당도 분할 기준으로 바뀌어 같이 받음)
    private void resyncFullHistory(String symbol, LocalDate to) {
        assetRepository.findById(symbol).ifPresent(asset ->
            assetEventProducer.publishAssetCreated(asset, true, FULL_HISTORY_FROM, to, true));
        collectionStats.recordSplitResync();
        log.info("[YF-캔들저장] 분할로 과거 종가가 바뀌어 배당까지 전 기간을 다시 받는다: symbol={}", symbol);
    }

    private record Merged(int inserted, int updated) {}

    // UNIQUE(symbol, date, currency) 와 같은 기준
    private static String key(Candle c) {
        return c.getDate() + "|" + c.getCurrency();
    }

    private static boolean changed(Candle prev, Candle fresh) {
        return differs(prev.getOpen(), fresh.getOpen())
            || differs(prev.getHigh(), fresh.getHigh())
            || differs(prev.getLow(), fresh.getLow())
            || differs(prev.getClose(), fresh.getClose())
            || adjustedCloseDiffers(prev.getAdjustedClose(), fresh.getAdjustedClose())
            || differs(prev.getDividendAmount(), fresh.getDividendAmount())
            || differs(prev.getSplitCoefficient(), fresh.getSplitCoefficient())
            || !Objects.equals(prev.getVolume(), fresh.getVolume());
    }

    // 소수 8자리로 맞춘 뒤 비교(야후는 124.80750274658203 처럼 주고 DB 는 8자리로 반올림 저장)
    private static boolean differs(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a != b;
        }
        return a.setScale(DB_SCALE, RoundingMode.HALF_UP).compareTo(b.setScale(DB_SCALE, RoundingMode.HALF_UP)) != 0;
    }

    // 8자리 비교 먼저(1e-4 크기 값은 반올림만으로도 상대 차이가 허용치를 넘음)
    private static boolean adjustedCloseDiffers(BigDecimal stored, BigDecimal fetched) {
        if (!differs(stored, fetched)) {
            return false;
        }
        if (stored == null || fetched == null || stored.signum() == 0) {
            return true;
        }
        BigDecimal gap = stored.subtract(fetched).abs();
        return gap.compareTo(stored.abs().multiply(ADJUSTED_CLOSE_TOLERANCE)) > 0;
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
