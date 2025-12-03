package com.muscat.marketdata.datasource.alphavantage.collector;

import com.muscat.marketdata.datasource.alphavantage.provider.SymbolSource;
import com.muscat.marketdata.datasource.alphavantage.service.MarketCapCollectionService;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AlphaVantage 종목 자동 수집 (이벤트 기반)
 * Phase 1: LISTING_STATUS API로 전체 종목 수집 (시가총액 없음)
 * Phase 2: MarketCapCollectionService로 시가총액 점진적 수집
 */
@Slf4j
@Component
@ConditionalOnProperty(
  name = "marketdata.provider",
  havingValue = "alphavantage"
)
@RequiredArgsConstructor
public class SymbolCollector {

  private final SymbolSource symbolSource;
  private final AssetRepository assetRepository;
  private final AssetEventProducer assetEventProducer;
  private final MarketCapCollectionService marketCapCollectionService;

  @Value("${marketdata.symbol-collection.enabled:false}")
  private boolean enabled;

  @Value("${marketdata.symbol-collection.collect-data:true}")
  private boolean collectData;

  @Value("${marketdata.symbol-collection.include-dividends:true}")
  private boolean includeDividends;

  @EventListener(ApplicationReadyEvent.class)
  @Async
  @Transactional
  @SchedulerLock(
    name = "AV_SymbolCollector_collectSymbols",
    lockAtMostFor = "3h",
    lockAtLeastFor = "10s"
  )
  public void collectSymbols() {
    if (!enabled) {
      log.info("[AV-심볼수집] 비활성화됨 (marketdata.symbol-collection.enabled=false)");
      return;
    }

    // 초기 수집: 아주 과거 날짜부터 (full outputsize로 전부 받음)
    LocalDate from = LocalDate.of(1900, 1, 1);
    LocalDate to = LocalDate.now();

    log.info("[AV-심볼수집] ========== 종목 동기화 시작 ==========");
    log.info("[AV-심볼수집] Phase 1: LISTING_STATUS API로 최신 상장 종목 조회");

    // 항상 LISTING_STATUS API로 최신 종목 리스트 조회
    List<Asset> latestSymbols = symbolSource.fetchSymbols();

    if (latestSymbols.isEmpty()) {
      log.warn("[AV-심볼수집] LISTING_STATUS API에서 종목을 가져오지 못했습니다");
      return;
    }

    log.info("[AV-심볼수집] LISTING_STATUS API: {}개 종목 수신", latestSymbols.size());

    // DB 기존 종목 조회
    List<Asset> existingAssets = assetRepository.findAll();
    long existingCount = existingAssets.size();

    log.info("[AV-심볼수집] DB 기존 종목: {}개", existingCount);

    // Safety Check: API 응답이 비정상적으로 적으면 중단 (API 장애 방지)
    // 기존 종목의 50% 미만이면 API 오류로 간주
    if (existingCount > 0 && latestSymbols.size() < existingCount * 0.5) {
      log.error("[AV-심볼수집] API 응답 이상 API: {}개, DB: {}개 (50% 미만). 상장폐지 처리 중단.",
        latestSymbols.size(), existingCount);
      log.error("[AV-심볼수집] AlphaVantage API 상태를 확인하거나, 수동으로 실행하세요.");
      return;
    }

    // 최신 종목 심볼 Set (빠른 검색용)
    Set<String> latestSymbolSet = latestSymbols.stream()
      .map(Asset::getSymbol)
      .collect(Collectors.toSet());

    // 기존 종목 심볼 Map (빠른 검색용)
    Map<String, Asset> existingSymbolMap = existingAssets.stream()
      .collect(Collectors.toMap(Asset::getSymbol, asset -> asset));

    int newCount = 0;
    int delistedCount = 0;
    int unchangedCount = 0;

    // 1. 신규 상장: CSV에 있지만 DB에 없음
    log.info("[AV-심볼수집] 신규 상장 종목 확인 중...");
    for (Asset latestAsset : latestSymbols) {
      if (!existingSymbolMap.containsKey(latestAsset.getSymbol())) {
        try {
          assetRepository.save(latestAsset);
          newCount++;

          if (newCount % 10 == 0) {
            log.info("[AV-심볼수집] 신규 상장 처리 중: {}/{}", newCount, latestSymbols.size());
          }
        } catch (Exception e) {
          log.error("[AV-심볼수집] 신규 종목 저장 실패: symbol={}", latestAsset.getSymbol(), e);
        }
      } else {
        unchangedCount++;
      }
    }

    // 2. 상장 폐지: DB에 있지만 CSV에 없음
    log.info("[AV-심볼수집] 상장 폐지 종목 확인 중...");
    for (Asset existingAsset : existingAssets) {
      if (!latestSymbolSet.contains(existingAsset.getSymbol()) && existingAsset.getActive()) {
        try {
          existingAsset.setActive(false);
          existingAsset.setDelistedDate(LocalDate.now());
          assetRepository.save(existingAsset);
          delistedCount++;
          log.info("[AV-심볼수집] 상장 폐지 처리: symbol={}", existingAsset.getSymbol());
        } catch (Exception e) {
          log.error("[AV-심볼수집] 상장 폐지 처리 실패: symbol={}", existingAsset.getSymbol(), e);
        }
      }
    }

    log.info("[AV-심볼수집] ========== Phase 1 완료 ==========");
    log.info("[AV-심볼수집] 신규 상장: {}개, 상장 폐지: {}개, 유지: {}개", newCount, delistedCount, unchangedCount);

    // Phase 2: 캔들 데이터 수집 (활성 종목만)
    if (collectData) {
      log.info("[AV-심볼수집] Phase 2: 캔들 데이터 수집 이벤트 발행 (활성 종목만)");
      List<Asset> activeAssets = assetRepository.findByActiveTrue();
      publishDataCollectionEvents(activeAssets, from, to);
    }

    // Phase 3: 시가총액 수집
    log.info("[AV-심볼수집] Phase 3: 시가총액 수집 시작");
    marketCapCollectionService.collectMarketCaps();

    log.info("[AV-심볼수집] ========== 종목 동기화 완료 ==========");
  }

  /**
   * 캔들/배당 데이터 수집을 위한 Kafka 이벤트 발행
   */
  private void publishDataCollectionEvents(List<Asset> assets, LocalDate from, LocalDate to) {
    int eventCount = 0;

    for (Asset asset : assets) {
      try {
        assetEventProducer.publishAssetCreated(
          asset,
          collectData,
          from,
          to,
          includeDividends
        );
        eventCount++;

        if (eventCount % 100 == 0) {
          log.info("[AV-심볼수집] 이벤트 발행 진행: {}/{}", eventCount, assets.size());
        }
      } catch (Exception e) {
        log.error("[AV-심볼수집] 이벤트 발행 실패: symbol={}", asset.getSymbol(), e);
      }
    }

    log.info("[AV-심볼수집] 이벤트 발행 완료: {}개", eventCount);
  }
}
