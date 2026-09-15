package com.muscat.marketdata.datasource.yf.service;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.mapper.MarketDataMapper;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.repository.DividendRepository;
import com.muscat.marketdata.domain.service.CollectionStats;
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
    private final CollectionStats collectionStats;
    private final AssetEventProducer assetEventProducer;

    // 관리자 재수집 기본값과 같다. 이 날부터 받을 때는 과거 종가를 비교하지 않는다
    private static final LocalDate FULL_HISTORY_FROM = LocalDate.of(1970, 1, 1);

    // candle 가격 · 배당 · 분할 계수 컬럼의 소수 자릿수
    private static final int DB_SCALE = 8;

    // 야후는 응답 서버마다 adjclose 를 조금씩 다르게 준다. 84종목을 두 서버에서 받아 비교했을 때 상대 차이는 최대 1.4e-6,
    // 배당 한 번이 과거 adjclose 를 바꾸는 비율은 가장 작아도 2.7e-4 였다. 그 사이 값으로 둘을 가른다
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
                    // 전 기간을 받았으면 받은 최신 봉 날짜를 남긴다. 분할 찾기가 이 날짜 뒤에 난 분할만 본다
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
            collectionStats.recordDividends(newDividends.size());
            log.debug("[YF-배당저장] 완료: symbol={}, count={}", symbol, newDividends.size());

            return newDividends.size();
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

    /**
     * 받아온 캔들을 기존 행과 대조해 없는 것만 넣고 값이 달라진 것만 고친다.
     * adjusted_close 는 배당이 나올 때마다 과거 날짜까지 바뀌므로 기존 행도 다시 본다.
     */
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

    // 기존 데이터와 겹치는 날짜 중 가장 이른 날의 종가를 비교한다. 분할이 나면 과거 종가가 전부 분할 기준으로 바뀐다
    private static boolean scaleChanged(Map<String, Candle> existing, List<Candle> candles) {
        return candles.stream()
            .filter(fresh -> existing.containsKey(key(fresh)))
            .min(Comparator.comparing(Candle::getDate))
            .map(fresh -> SplitScale.isScaleChange(existing.get(key(fresh)).getClose(), fresh.getClose()))
            .orElse(false);
    }

    // 1970년 부터 받을 때는 비교하지 않는다. 비교하면 1970년 이전 데이터가 있는 종목에서 재수집이 되풀이된다
    private boolean isPartialWindow(String symbol, LocalDate from) {
        return from != null && from.isAfter(FULL_HISTORY_FROM)
            && candleRepository.existsBySymbolAndDateBefore(symbol, from);
    }

    // 덮어쓰지 않으므로 재수집 요청이 실패해도 다음 수집에서 같은 차이가 다시 보인다
    private void resyncFullHistory(String symbol, LocalDate to) {
        assetRepository.findById(symbol).ifPresent(asset ->
            assetEventProducer.publishAssetCreated(asset, true, FULL_HISTORY_FROM, to, false));
        collectionStats.recordSplitResync();
        log.info("[YF-캔들저장] 분할로 과거 종가가 바뀌어 전 기간을 다시 받는다: symbol={}", symbol);
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

    // 야후는 124.80750274658203 처럼 주고 DB 는 소수 8자리로 반올림해 저장한다. 같은 자리로 맞춘 뒤 비교한다
    private static boolean differs(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a != b;
        }
        return a.setScale(DB_SCALE, RoundingMode.HALF_UP).compareTo(b.setScale(DB_SCALE, RoundingMode.HALF_UP)) != 0;
    }

    // 8자리 비교를 먼저 한다. 1e-4 크기 값은 반올림만으로도 상대 차이가 허용치를 넘는다
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
