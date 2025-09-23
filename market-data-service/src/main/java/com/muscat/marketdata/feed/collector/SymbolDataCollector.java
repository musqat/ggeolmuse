package com.muscat.marketdata.feed.collector;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.feed.service.CandleBatchService;
import com.muscat.marketdata.provider.MarketDataProvider.SymbolSource;
import com.muscat.marketdata.provider.config.SymbolDataCollectProps;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SymbolDataCollector implements CommandLineRunner {

  private final SymbolSource nasdaqDataSource;
  private final AssetRepository assetRepository;
  private final CandleBatchService batchService;
  private final SymbolDataCollectProps props;

  @Override
  public void run(String... args) {
    if (props.getSymbol().isEnabled()) {
      log.info("[종목수집] 시작");
      collectSymbolsOnly();

      // 심볼 수집 후 바로 캔들 데이터 수집 시작
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

  private void collectCandleData() {
    ZoneId marketTimeZone = ZoneId.of(props.getSchedule().getTimezone());
    LocalDate endDate = LocalDate.now(marketTimeZone);
    LocalDate startDate = endDate.minusDays(365); // 최근 30일 데이터

    log.info("[자동 캔들수집] 기간: {} ~ {}", startDate, endDate);
    batchService.collectAll(startDate, endDate, true);
    log.info("[자동 캔들수집] 완료");
  }

  @Scheduled(cron = "${marketdata.feed.schedule.cron:0 0 3 * * *}")
  public void runDailyDataCollection() {
    if (!props.getSchedule().isEnabled()) {
      log.info("[일일배치] 스케줄러 비활성화됨");
      return;
    }

    ZoneId marketTimeZone = ZoneId.of(props.getSchedule().getTimezone());
    LocalDate endDate = LocalDate.now(marketTimeZone);
    LocalDate startDate = endDate.minusDays(props.getSchedule().getLookbackDays());

    log.info("[일일배치] 시작: {} ~ {}", startDate, endDate);

    if (assetRepository.count() == 0) {
      log.info("[일일배치] Asset 테이블 비어있음, 종목 수집 먼저 실행");
      collectSymbolsOnly();
    }

    batchService.collectAll(startDate, endDate, true);
    log.info("[일일배치] 완료");
  }
}