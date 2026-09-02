package com.muscat.marketdata.datasource.yf.collector;

import com.muscat.marketdata.datasource.common.SymbolCatalog;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 종목 자동 수집 (이벤트 기반)
 *
 * 목록은 {@link SymbolCatalog} 에서 받고, 캔들·배당은 Kafka 이벤트로 Yahoo 가 채운다.
 * 목록 출처와 시세 출처를 분리해 둔 것이라 프로바이더를 바꿔도 목록은 그대로다.
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "marketdata.provider",
    havingValue = "yahoo"
)
@RequiredArgsConstructor
public class SymbolCollector {

    // 목록은 프로바이더가 아니라 공통 카탈로그에서 받는다.
    private final SymbolCatalog symbolCatalog;
    private final AssetRepository assetRepository;
    private final AssetEventProducer assetEventProducer;

    @Value("${marketdata.symbol-collection.enabled:false}")
    private boolean enabled;

    @Value("${marketdata.symbol-collection.lookback-days:365}")
    private int lookbackDays;

    // 수집 종목 수 상한 (0 = 무제한). 로컬에서 전체(~6700) 수집은 무거워 상위 N개만 수집할 때 사용.
    @Value("${marketdata.symbol-collection.max-symbols:0}")
    private int maxCollectSymbols;


    @EventListener(ApplicationReadyEvent.class)
    @Async
    @Transactional
    @SchedulerLock(
        name = "YF_SymbolCollector_collectSymbols",
        lockAtMostFor = "30m",
        lockAtLeastFor = "10s"
    )
    public void collectSymbols() {
        if (!enabled) {
            log.info("[YF-종목수집] 비활성화됨 (marketdata.symbol-collection.enabled=false)");
            return;
        }

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(lookbackDays);

        // 기존 심볼이 있는지 확인
        long existingCount = assetRepository.count();

        if (existingCount > 0) {
            // 기존 심볼들에 대해서만 데이터 수집 이벤트 발행 (심볼 추가 안 함)
            log.info("[YF-종목수집] 기존 {}개 종목 발견 - 데이터 업데이트만 수행 (종목 추가 안 함)", existingCount);

            List<Asset> existingAssets = assetRepository.findAll();
            int eventCount = 0;

            for (Asset asset : existingAssets) {
                try {
                    // Kafka 이벤트 발행 (비동기 캔들 수집)
                    assetEventProducer.publishAssetCreated(
                        asset,
                        true,           // collectData
                        from,
                        to,
                        true            // includeDividends
                    );
                    eventCount++;

                    if (eventCount % 100 == 0) {
                        log.info("[YF-종목수집] 진행 중: {}/{} 이벤트 발행", eventCount, existingAssets.size());
                    }
                } catch (Exception e) {
                    log.error("[YF-종목수집] 이벤트 발행 실패: symbol={}", asset.getSymbol(), e);
                }
            }

            log.info("[YF-종목수집] 완료: {}개 기존 종목에 대해 데이터 수집 이벤트 발행", eventCount);

            // 기존 종목 갱신과 별개로 신규 상장을 받아온다.
            collectNewlyListed(from, to);
            return;
        }

        // 기존 심볼이 없으면 새로 로드
        log.info("[YF-종목수집] 신규 종목 로드 시작");

        try {
            List<Asset> symbols = symbolCatalog.fetchAll();

            if (symbols.isEmpty()) {
                log.warn("[YF-종목수집] 실패: 종목을 찾을 수 없음");
                return;
            }

            // 수집 상한 적용: 시가총액 desc 정렬 후 상위 N개 (거래소 무관, AAPL/MSFT 등 대형주 포함)
            if (maxCollectSymbols > 0 && symbols.size() > maxCollectSymbols) {
                symbols = symbols.stream()
                    .sorted(java.util.Comparator.comparingLong(
                        (Asset a) -> a.getMarketCap() == null ? 0L : a.getMarketCap()).reversed())
                    .limit(maxCollectSymbols)
                    .toList();
                log.info("[YF-종목수집] 수집 상한 적용: 시총 상위 {}개만 수집", maxCollectSymbols);
            }

            log.info("[YF-종목수집] 종목 로드 완료: {}개", symbols.size());

            int savedCount = 0;
            int eventCount = 0;

            for (Asset asset : symbols) {
                try {
                    // Asset 저장
                    Asset saved = assetRepository.save(asset);
                    savedCount++;

                    // Kafka 이벤트 발행 (비동기 캔들 수집)
                    assetEventProducer.publishAssetCreated(
                        saved,
                        true,           // collectData
                        from,
                        to,
                        true            // includeDividends
                    );
                    eventCount++;

                    if (savedCount % 100 == 0) {
                        log.info("[YF-종목수집] 진행 중: {}/{} 저장 완료", savedCount, symbols.size());
                    }

                } catch (Exception e) {
                    log.error("[YF-종목수집] 실패: symbol={}, error={}", asset.getSymbol(), e.getMessage());
                }
            }

            log.info("[YF-종목수집] 완료: Asset {}개 저장, Kafka 이벤트 {}개 발행", savedCount, eventCount);

        } catch (Exception e) {
            log.error("[YF-종목수집] 전체 실패", e);
        }
    }

    /**
     * 신규 상장 확인 - 매일 오전 8시 KST (평일)
     *
     * 캔들 수집(07:30) 뒤, 시가총액 수집(10:00) 앞에 둔다
     *
     * 기동 시에도 한 번 돌지만(collectSymbols) 그것만으로는 재시작 전까지 목록이 굳는다.
     */
    @Scheduled(cron = "${yahoo.scheduler.symbol.cron:0 0 8 * * MON-FRI}",
        zone = "${yahoo.scheduler.zone:Asia/Seoul}")
    @SchedulerLock(name = "yfCollectNewlyListed", lockAtMostFor = "20m", lockAtLeastFor = "1m")
    public void collectNewlyListedDaily() {
        if (!enabled) {
            log.info("[YF-신규종목] 비활성화됨 (marketdata.symbol-collection.enabled=false)");
            return;
        }

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(lookbackDays);

        log.info("[YF-신규종목] 일일 확인 시작");
        collectNewlyListed(from, to);
    }

    /**
     * 목록을 다시 받아 DB 에 없는 심볼만 추가한다.
     *
     * 이미 있는 종목은 건드리지 않는다. 갱신은 호출부에서 이미 이벤트를 발행했다.
     * 목록 조회가 실패해도 기존 종목 갱신은 끝난 뒤이므로 여기서 예외를 밖으로 내보내지 않는다.
     */
    private void collectNewlyListed(LocalDate from, LocalDate to) {
        try {
            // 카탈로그가 중복 제거와 보통주 필터까지 마친 목록을 준다
            List<Asset> fetched = symbolCatalog.fetchAll();

            if (fetched.isEmpty()) {
                log.warn("[YF-신규종목] 목록이 비어 있어 건너뛴다");
                return;
            }

            Set<String> known = assetRepository.findAll().stream()
                .map(Asset::getSymbol)
                .collect(Collectors.toSet());

            // 카탈로그가 중복을 걸러 주지만 경계에서 한 번 더 본다.
            Set<String> seen = new java.util.HashSet<>();
            List<Asset> newcomers = fetched.stream()
                .filter(a -> !known.contains(a.getSymbol()))
                .filter(a -> seen.add(a.getSymbol()))
                .toList();

            if (newcomers.isEmpty()) {
                log.info("[YF-신규종목] 조회 {}개, 신규 없음", fetched.size());
                return;
            }

            int saved = 0;
            for (Asset asset : newcomers) {
                try {
                    Asset persisted = assetRepository.save(asset);
                    assetEventProducer.publishAssetCreated(persisted, true, from, to, true);
                    saved++;
                } catch (Exception e) {
                    log.error("[YF-신규종목] 저장 실패: symbol={}, error={}",
                        asset.getSymbol(), e.getMessage());
                }
            }

            log.info("[YF-신규종목] 조회 {}개 중 {}개 추가", fetched.size(), saved);

        } catch (Exception e) {
            log.error("[YF-신규종목] 실패", e);
        }
    }
}
