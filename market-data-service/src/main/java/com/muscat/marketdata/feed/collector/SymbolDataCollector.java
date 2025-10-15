package com.muscat.marketdata.feed.collector;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.feed.service.AssetUpdateService;
import com.muscat.marketdata.feed.service.CandleBatchService;
import com.muscat.marketdata.provider.MarketDataProvider.SymbolSource;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SymbolDataCollector {

  private final SymbolSource nasdaqDataSource;
  private final AssetRepository assetRepository;
  private final CandleBatchService batchService;
  private final AssetUpdateService assetUpdateService;

  @Value("${marketdata.feed.symbol.enabled:true}")
  private boolean symbolCollectionEnabled;

  @Value("${marketdata.feed.schedule.enabled:true}")
  private boolean scheduleEnabled;

  @Value("${marketdata.feed.schedule.timezone:America/New_York}")
  private String scheduleTimezone;

  @Value("${marketdata.feed.schedule.lookback-days:365}")
  private int scheduleLookbackDays;

  @EventListener(ApplicationReadyEvent.class)
  @Async
  public void onApplicationReady() {
    if (symbolCollectionEnabled) {
      // 기본 종목 5개를 DB에 초기화
      initializeHardcodedSymbols();

      // 초기화된 종목들의 캔들 데이터 수집
      if (assetRepository.count() > 0) {
        log.info("[자동 캔들수집] 시작");
        collectCandleData();
      }
    } else {
      log.info("[종목수집] 비활성화됨 (symbol.enabled=false)");
    }
  }

  @Transactional
  public void collectSymbolsOnly() {
    List<Asset> fetchedAssets = nasdaqDataSource.fetchSymbols();
    List<Asset> newAssetsToSave = fetchedAssets.stream()
        .filter(asset -> !assetRepository.existsById(asset.getSymbol()))
        .toList();

    if (!newAssetsToSave.isEmpty()) {
      assetRepository.saveAllAndFlush(newAssetsToSave);
    }

    log.info("[종목수집] 완료: 신규={}, 전체={}", newAssetsToSave.size(), assetRepository.count());
  }

  @Transactional
  protected void initializeHardcodedSymbols() {
    log.info("[하드코딩 심볼 초기화] 시작");

    List<Asset> hardcodedAssets = List.of(
        Asset.builder()
            .symbol("AAPL")
            .name("Apple Inc.")
            .country("US")
            .currency("USD")
            .assetType("EQUITY")
            .build(),
        Asset.builder()
            .symbol("MSFT")
            .name("Microsoft Corp.")
            .country("US")
            .currency("USD")
            .assetType("EQUITY")
            .build(),
        Asset.builder()
            .symbol("GOOGL")
            .name("Alphabet Inc.")
            .country("US")
            .currency("USD")
            .assetType("EQUITY")
            .build(),
        Asset.builder()
            .symbol("TSLA")
            .name("Tesla Inc.")
            .country("US")
            .currency("USD")
            .assetType("EQUITY")
            .build(),
        Asset.builder()
            .symbol("NVDA")
            .name("NVIDIA Corp.")
            .country("US")
            .currency("USD")
            .assetType("EQUITY")
            .build()
    );

    int savedCount = 0;
    for (Asset asset : hardcodedAssets) {
      if (!assetRepository.existsById(asset.getSymbol())) {
        assetRepository.save(asset);
        savedCount++;
      }
    }

    log.info("[하드코딩 심볼 초기화] 완료: 신규={}, 전체={}", savedCount, assetRepository.count());
  }

  private void collectCandleData() {
    ZoneId marketTimeZone = ZoneId.of(scheduleTimezone);
    LocalDate endDate = LocalDate.now(marketTimeZone);
    LocalDate startDate = endDate.minusDays(365);

    log.info("[자동 캔들수집] 기간: {} ~ {}", startDate, endDate);
    batchService.collectAll(startDate, endDate, true);
    log.info("[자동 캔들수집] 완료");
  }

  @Scheduled(cron = "${marketdata.feed.schedule.cron:0 0 3 * * *}")
  public void runDailyDataCollection() {
    if (!scheduleEnabled) {
      log.info("[일일배치] 스케줄러 비활성화됨");
      return;
    }

    ZoneId marketTimeZone = ZoneId.of(scheduleTimezone);
    LocalDate endDate = LocalDate.now(marketTimeZone);
    LocalDate startDate = endDate.minusDays(scheduleLookbackDays);

    log.info("[일일배치] 시작: {} ~ {}", startDate, endDate);

    if (assetRepository.count() == 0) {
      log.info("[일일배치] Asset 테이블 비어있음, 종목 수집 먼저 실행");
      collectSymbolsOnly();
    }

    batchService.collectAll(startDate, endDate, true);
    log.info("[일일배치] 완료");
  }
}
