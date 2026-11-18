package com.muscat.marketdata.datasource.yf.service;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.repository.DividendRepository;
import com.muscat.marketdata.domain.service.CandleService;
import com.muscat.marketdata.domain.service.CollectionStats;
import com.muscat.marketdata.domain.service.DividendMerge;
import com.muscat.marketdata.domain.service.SplitScale;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import com.muscat.marketdata.infra.kafka.DividendEventProducer;
import com.muscat.marketdata.infra.kafka.SplitEventProducer;

import java.util.Comparator;
import com.muscat.marketdata.datasource.common.MarketDataProvider.CandleSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.DividendSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.KafkaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClientException;

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
    private final CandleService candleService;
    private final SplitEventProducer splitEventProducer;

    // 관리자 재수집 기본값과 같음(이 날부터 받을 때는 과거 종가 비교 안 함)
    private static final LocalDate FULL_HISTORY_FROM = LocalDate.of(1970, 1, 1);

    // candle 가격 · 배당 · 분할 계수 컬럼의 소수 자릿수
    private static final int DB_SCALE = 8;

    // 서버 차이(84종목 상대 차이 최대 1.4e-6)와 배당 변화(가장 작아도 2.7e-4) 사이의 adjclose 허용치
    private static final BigDecimal ADJUSTED_CLOSE_TOLERANCE = new BigDecimal("0.00001");

    // 배당 금액 칸의 마지막 자리 한 단위(분할 비율을 곱한 뒤 비교 허용치)
    private static final BigDecimal DIVIDEND_UNIT = new BigDecimal("0.00000001");

    // self-injection: saveBoth에서 proxy 통해 호출해야 REQUIRES_NEW가 실제로 적용됨
    @Lazy
    @Autowired
    private YahooCandleUpdateService self;

    // 봉(분할이 있으면 분할 기준 배당까지)을 트랜잭션 밖에서 받은 뒤 종목 하나의 저장 트랜잭션으로 넘김
    public int saveCandles(String symbol, LocalDate from, LocalDate to) {
        List<Candle> candles;
        List<DividendDto> dividends;
        try {
            candles = candleSource.fetchDailyAdjusted(symbol, from, to);
            if (candles == null || candles.isEmpty()) {
                collectionStats.recordSymbolEmpty();
                log.debug("[YF-캔들저장] 데이터 없음: symbol={}", symbol);
                return 0;
            }
            // 받은 봉에 처음 보는 분할이 있을 때만 분할 기준 배당도 받음
            boolean newSplit = candles.stream().anyMatch(candle -> isSplit(candle.getSplitCoefficient()))
                && !newSplits(loadExisting(symbol, from, to), candles).isEmpty();
            dividends = newSplit ? dividendSource.fetchDividends(symbol, from, to) : List.of();
        } catch (RestClientException | DataAccessException e) {
            collectionStats.recordSymbolFailed();
            log.warn("[YF-캔들저장] 실패: symbol={}, error={}", symbol, e.getMessage());
            return 0;
        }
        return self.storeCandles(symbol, from, to, candles, dividends);
    }

    // 받은 봉 · 분할 기준 배당을 종목 하나의 트랜잭션으로 저장(실패하면 그 종목만 롤백)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int storeCandles(String symbol, LocalDate from, LocalDate to, List<Candle> candles,
        List<DividendDto> dividends) {
        try {
            Map<String, Candle> existing = loadExisting(symbol, from, to);
            List<Candle> newSplits = newSplits(existing, candles);
            if ((scaleChanged(existing, candles) || !newSplits.isEmpty()) && isPartialWindow(symbol, from)) {
                resyncFullHistory(symbol, to);
                return 0;
            }

            Merged merged = mergeIntoExisting(existing, candles);
            // 새로 넣은 날은 시세 없던 날이라 캐시에 없음, 고친 날만 비움
            if (!merged.updatedDates().isEmpty()) {
                afterCommit(() -> candleService.evictOhlc(symbol, merged.updatedDates()));
            }

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

            if (!newSplits.isEmpty()) {
                saveDividendsWithSplits(dividends, newSplits);
            }
            newSplits.forEach(split ->
                splitEventProducer.publishSplitDetected(symbol, split.getDate(), split.getSplitCoefficient()));

            collectionStats.recordSymbol(merged.inserted(), merged.updatedDates().size());
            log.debug("[YF-캔들저장] 완료: symbol={}, 받음={}, 신규={}, 갱신={}",
                symbol, candles.size(), merged.inserted(), merged.updatedDates().size());
            return candles.size();
        } catch (DataAccessException | KafkaException e) {
            rollbackThisSymbol();
            collectionStats.recordSymbolFailed();
            log.warn("[YF-캔들저장] 실패: symbol={}, error={}", symbol, e.getMessage());
            return 0;
        }
    }

    // 배당을 트랜잭션 밖에서 받은 뒤 종목 하나의 저장 트랜잭션으로 넘김
    public int saveDividends(String symbol, LocalDate from, LocalDate to) {
        List<DividendDto> dtos;
        try {
            dtos = dividendSource.fetchDividends(symbol, from, to);
        } catch (RestClientException e) {
            log.warn("[YF-배당저장] 실패: symbol={}, error={}", symbol, e.getMessage());
            return 0;
        }
        if (dtos == null || dtos.isEmpty()) {
            log.debug("[YF-배당저장] 데이터 없음: symbol={}", symbol);
            return 0;
        }
        return self.storeDividends(symbol, dtos);
    }

    // 받은 배당 가운데 바뀐 것만 종목 하나의 트랜잭션으로 저장(실패하면 그 종목만 롤백)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int storeDividends(String symbol, List<DividendDto> dtos) {
        try {
            var rows = DividendMerge.changedRows(dividendRepository, dtos);
            if (rows.isEmpty()) {
                log.debug("[YF-배당저장] 바뀐 배당 없음, 저장 스킵: symbol={}", symbol);
                return 0;
            }

            dividendRepository.saveAll(rows);
            afterCommit(candleService::evictDividendYieldOrder);

            // Kafka 이벤트 발행 (배치로 처리)
            dividendEventProducer.publishBatch(rows);
            collectionStats.recordDividends(rows.size());
            log.debug("[YF-배당저장] 완료: symbol={}, count={}", symbol, rows.size());

            return rows.size();
        } catch (DataAccessException | KafkaException e) {
            rollbackThisSymbol();
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
        List<LocalDate> updatedDates = new ArrayList<>();

        for (Candle fresh : candles) {
            Candle prev = existing.get(key(fresh));
            if (prev == null) {
                inserts.add(fresh);
            } else if (changed(prev, fresh)) {
                copyValues(prev, fresh);
                updatedDates.add(fresh.getDate());
            }
        }

        if (!inserts.isEmpty()) {
            candleRepository.saveAll(inserts);
        }

        return new Merged(inserts.size(), updatedDates);
    }

    // 기존 데이터와 겹치는 날짜 중 가장 이른 날의 종가 비교(분할이 나면 과거 종가가 전부 분할 기준으로 바뀜)
    private static boolean scaleChanged(Map<String, Candle> existing, List<Candle> candles) {
        return candles.stream()
            .filter(fresh -> existing.containsKey(key(fresh)))
            .min(Comparator.comparing(Candle::getDate))
            .map(fresh -> SplitScale.isScaleChange(existing.get(key(fresh)).getClose(), fresh.getClose()))
            .orElse(false);
    }

    // 처음 보는 분할 봉(저장된 봉이 없거나 계수 1 이던 날에 1 이 아닌 계수)
    private static List<Candle> newSplits(Map<String, Candle> existing, List<Candle> candles) {
        return candles.stream()
            .filter(fresh -> isSplit(fresh.getSplitCoefficient()))
            .filter(fresh -> {
                Candle prev = existing.get(key(fresh));
                return prev == null || !isSplit(prev.getSplitCoefficient());
            })
            .toList();
    }

    private static boolean isSplit(BigDecimal coefficient) {
        return coefficient != null && coefficient.compareTo(BigDecimal.ONE) != 0;
    }

    // 분할 기준으로 바뀐 주당 배당을 캔들과 같은 트랜잭션에 저장
    private void saveDividendsWithSplits(List<DividendDto> dividends, List<Candle> splits) {
        List<DividendMerge.Change> changes = DividendMerge.changes(dividendRepository, dividends);
        if (changes.isEmpty()) {
            return;
        }
        List<Dividend> rows = changes.stream().map(DividendMerge.Change::row).toList();
        dividendRepository.saveAll(rows);
        afterCommit(candleService::evictDividendYieldOrder);
        // 분할로만 바뀐 지난 배당은 배당 이벤트에서 제외
        List<Dividend> announced = changes.stream()
            .filter(change -> !splitOnly(change, splits))
            .map(DividendMerge.Change::row)
            .toList();
        if (!announced.isEmpty()) {
            afterCommit(() -> dividendEventProducer.publishBatch(announced));
        }
        collectionStats.recordDividends(rows.size());
    }

    // 지난 배당의 주당 금액이 배당락일 뒤 새 분할 비율의 곱만큼만 바뀌었는지
    private static boolean splitOnly(DividendMerge.Change change, List<Candle> splits) {
        if (change.before() == null) {
            return false;
        }
        BigDecimal factor = splits.stream()
            .filter(split -> split.getDate().isAfter(change.row().getExDate()))
            .map(Candle::getSplitCoefficient)
            .reduce(BigDecimal.ONE, BigDecimal::multiply);
        if (factor.compareTo(BigDecimal.ONE) == 0) {
            return false;
        }
        BigDecimal gap = change.before().subtract(change.row().getAmount().multiply(factor)).abs();
        return gap.compareTo(factor.add(BigDecimal.ONE).multiply(DIVIDEND_UNIT)) <= 0;
    }

    // 실패한 종목 트랜잭션만 조용히 롤백
    private static void rollbackThisSymbol() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
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
        log.info("[YF-캔들저장] 분할이 보여 배당까지 전 기간을 다시 받는다: symbol={}", symbol);
    }

    private record Merged(int inserted, List<LocalDate> updatedDates) {}

    // 트랜잭션 안이면 커밋 뒤에, 밖이면 바로(커밋 전에 비우면 옛 값이 다시 캐시됨)
    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
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
