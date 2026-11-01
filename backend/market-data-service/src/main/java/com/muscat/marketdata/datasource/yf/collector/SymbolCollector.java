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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 종목 자동 수집(이벤트 기반): 목록은 SymbolCatalog, 캔들 · 배당은 Kafka 이벤트로 Yahoo 가 채움
@Slf4j
@Component
@ConditionalOnProperty(
    name = "marketdata.provider",
    havingValue = "yahoo"
)
@RequiredArgsConstructor
public class SymbolCollector {

    // 종목 목록은 공통 카탈로그에서
    private final SymbolCatalog symbolCatalog;
    private final AssetRepository assetRepository;
    private final AssetEventProducer assetEventProducer;

    @Value("${marketdata.symbol-collection.enabled:false}")
    private boolean enabled;

    @Value("${marketdata.symbol-collection.lookback-days:365}")
    private int lookbackDays;

    // 수집 종목 수 상한(0 = 무제한, 로컬에서 전체 약 6700 수집이 무거울 때 상위 N개만)
    @Value("${marketdata.symbol-collection.max-symbols:0}")
    private int maxCollectSymbols;


    // 트랜잭션 없음(걸면 저장이 커밋 때 한꺼번에 나가 한 종목 실패가 전부를 되돌리고 이벤트는 커밋 전에 나감)
    @EventListener(ApplicationReadyEvent.class)
    @Async
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

        // 기존 종목이 있으면 기동 때 발행 없음(전 종목 발행은 재기동마다 3천만 행 갱신, 갱신은 캔들 스케줄러 몫)
        if (existingCount > 0) {
            log.info("[YF-종목수집] 기존 {}개 종목 발견 - 기동 시 수집은 건너뛴다", existingCount);
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

    // 신규 상장 확인(캔들 07:30 뒤 · 시가총액 10:00 앞)
    // 기동 때만 돌면 재시작 전까지 목록이 굳음
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

    // DB 에 없는 심볼만 추가(목록 조회가 실패해도 예외를 밖으로 내지 않음, 기존 종목 갱신은 끝난 뒤)
    private void collectNewlyListed(LocalDate from, LocalDate to) {
        try {
            // 카탈로그가 중복 제거 · 보통주 필터까지 마친 목록
            List<Asset> fetched = symbolCatalog.fetchAll();


            if (fetched.isEmpty()) {
                log.warn("[YF-신규종목] 목록이 비어 있어 건너뛴다");
                return;
            }

            Map<String, Asset> known = assetRepository.findAll().stream()
                .collect(Collectors.toMap(Asset::getSymbol, a -> a, (first, dup) -> first));

            refreshListingNames(fetched, known);

            // 경계에서 한 번 더 거르는 중복
            Set<String> seen = new java.util.HashSet<>();
            List<Asset> newcomers = fetched.stream()
                .filter(a -> !known.containsKey(a.getSymbol()))
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

    // 상장 목록 이름이 바뀐 기존 종목 따라 고침(손으로 고친 이름은 두고 목록 이름만 갱신)
    private void refreshListingNames(List<Asset> fetched, Map<String, Asset> known) {
        List<Asset> changed = new ArrayList<>();
        int renamed = 0;
        for (Asset listed : fetched) {
            Asset asset = known.get(listed.getSymbol());
            if (asset == null || listed.getName().equals(asset.getSourceName())) {
                continue;
            }
            if (asset.getSourceName() != null && asset.getSourceName().equals(asset.getName())) {
                asset.setName(listed.getName());
                asset.setAssetType(listed.getAssetType());
                renamed++;
            }
            asset.setSourceName(listed.getName());
            changed.add(asset);
        }
        if (!changed.isEmpty()) {
            assetRepository.saveAll(changed);
            log.info("[YF-신규종목] 목록 이름이 바뀐 종목 {}개(이름 · 유형 갱신 {}개)", changed.size(), renamed);
        }
    }
}
