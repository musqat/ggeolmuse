package com.muscat.marketdata.datasource.yf.scheduler;

import com.muscat.marketdata.datasource.yf.collector.FxDataCollector;
import com.muscat.marketdata.datasource.yf.service.YahooMarketCapService;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Yahoo Finance 데이터 스케줄러
 * - 캔들 업데이트: 매일 오전 7시 KST (평일)
 * - 시가총액 업데이트: 매일 오전 10시 KST (평일)
 * - 환율 업데이트: 매일 오전 11:10 KST (평일)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "marketdata.provider", havingValue = "yahoo")
@RequiredArgsConstructor
public class YahooScheduler {

    private final AssetRepository assetRepository;
    private final CandleRepository candleRepository;
    private final FxRateRepository fxRateRepository;
    private final AssetEventProducer assetEventProducer;
    private final YahooMarketCapService marketCapService;
    private final FxDataCollector fxDataCollector;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final double INITIAL_COLLECTION_THRESHOLD = 0.95;

    /**
     * 캔들 데이터 업데이트 - 매일 오전 7시 KST (평일)
     */
    @Scheduled(cron = "${yahoo.scheduler.candle.cron:0 0 7 * * MON-FRI}",
        zone = "${yahoo.scheduler.zone:Asia/Seoul}")
    @SchedulerLock(name = "yahooUpdateCandles", lockAtMostFor = "9m", lockAtLeastFor = "1m")
    public void updateCandles() {
        if (isInitialCollectionInProgress()) {
            log.info("=== [YF 스케줄러] 초기 수집 진행 중 - 캔들 업데이트 스킵 ===");
            return;
        }

        log.info("=== [YF 스케줄러] 캔들 업데이트 시작 ===");

        List<Asset> assets = assetRepository.findByActiveTrue();
        log.info("[YF 스케줄러] 업데이트 대상 종목: {}개", assets.size());

        LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));
        LocalDate from = today.minusYears(1);

        int published = 0;
        for (Asset asset : assets) {
            try {
                assetEventProducer.publishAssetCreated(asset, true, from, today, false);
                published++;
            } catch (Exception e) {
                log.warn("[YF 스케줄러] 캔들 이벤트 발행 실패: symbol={}", asset.getSymbol(), e);
            }
        }

        log.info("=== [YF 스케줄러] 캔들 업데이트 완료: {}개 이벤트 발행 ===", published);
    }

    /**
     * 시가총액 업데이트 - 매일 오전 10시 KST (평일)
     */
    @Scheduled(cron = "${yahoo.scheduler.marketcap.cron:0 0 10 * * MON-FRI}",
        zone = "${yahoo.scheduler.zone:Asia/Seoul}")
    @SchedulerLock(name = "yahooUpdateMarketCaps", lockAtMostFor = "30m", lockAtLeastFor = "5m")
    public void updateMarketCaps() {
        if (isInitialCollectionInProgress()) {
            log.info("=== [YF 스케줄러] 초기 수집 진행 중 - 시가총액 업데이트 스킵 ===");
            return;
        }

        log.info("=== [YF 스케줄러] 시가총액 업데이트 시작 ===");

        List<Asset> assets = assetRepository.findByActiveTrue();
        int updated = marketCapService.updateAllMarketCaps(assets);

        log.info("=== [YF 스케줄러] 시가총액 업데이트 완료: {}개 ===", updated);
    }

    /**
     * 환율 업데이트 - 매일 오전 11:10 KST (평일)
     */
    @Scheduled(cron = "${yahoo.scheduler.fx.cron:0 10 11 * * MON-FRI}",
        zone = "${yahoo.scheduler.zone:Asia/Seoul}")
    @SchedulerLock(name = "yahooUpdateFxRates", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    public void updateFxRates() {
        log.info("=== [YF 스케줄러] 환율 업데이트 시작 ===");

        LocalDate today = LocalDate.now(KST);
        fxDataCollector.collectSingleDate(today, true).ifPresentOrElse(
            fxRate -> {
                fxRateRepository.save(fxRate);
                log.info("[YF 스케줄러] 환율 업데이트 완료: {} -> {}", fxRate.getDate(), fxRate.getRate());
            },
            () -> log.warn("[YF 스케줄러] 환율 데이터를 가져올 수 없음: date={}", today)
        );
    }

    private boolean isInitialCollectionInProgress() {
        try {
            long totalAssets = assetRepository.count();
            long assetsWithCandles = candleRepository.countDistinctSymbols();

            if (totalAssets == 0) {
                return false;
            }

            double completionRate = (double) assetsWithCandles / totalAssets;
            boolean inProgress = completionRate < INITIAL_COLLECTION_THRESHOLD;

            if (inProgress) {
                log.info("[스케줄러 체크] 초기 수집 진행 중: {}/{}개 ({}%), 스케줄러 스킵",
                    assetsWithCandles, totalAssets, String.format("%.1f", completionRate * 100));
            }

            return inProgress;
        } catch (Exception e) {
            log.warn("[스케줄러 체크] 확인 실패, 안전하게 스케줄러 실행", e);
            return false;
        }
    }
}
