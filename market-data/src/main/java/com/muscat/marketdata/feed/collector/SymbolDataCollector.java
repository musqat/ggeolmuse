package com.muscat.marketdata.feed.collector;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.feed.service.CandleBatchService;
import com.muscat.marketdata.provider.config.SymbolDataCollectProps;
import com.muscat.marketdata.provider.stooq.StooqNasdaq100JsoupSource;
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

/**
 * 시장 데이터 수집 작업
 * 앱 시작 시: 종목 정보만 수집 (CommandLineRunner)
 * 스케줄: 전체 캔들/배당 데이터 수집 실행
 */
@Slf4j
@Component
@Order(1) // 환율 수집보다 먼저 실행
@RequiredArgsConstructor
public class SymbolDataCollector implements CommandLineRunner {

  private final StooqNasdaq100JsoupSource nasdaqDataSource;
  private final AssetRepository assetRepository;
  private final CandleBatchService batchService;
  private final SymbolDataCollectProps props;

  /**
   * 앱 시작 시: 종목 정보만 수집
   */
  @Override
  public void run(String... args) {
    if (props.getSymbol().isEnabled()) {
      log.info("[종목수집] 시작");
      collectSymbolsOnly();
    } else {
      log.info("[종목수집] 비활성화됨 (symbol.enabled=false)");
    }
  }

  /**
   * 종목 정보만 수집 (내부 메서드)
   */
  @Transactional
  public void collectSymbolsOnly() {
    List<Asset> fetchedAssets = nasdaqDataSource.fetchNasdaq100();
    List<Asset> newAssetsToSave = fetchedAssets.stream()
        .filter(asset -> !assetRepository.existsById(asset.getSymbol()))
        .toList();

    if (!newAssetsToSave.isEmpty()) {
      assetRepository.saveAllAndFlush(newAssetsToSave);
    }

    log.info("[종목수집] 완료: 신규={}, 전체={}", newAssetsToSave.size(), assetRepository.count());
  }

  /**
   * 매일 새벽 전체 데이터 수집 배치 실행
   */
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

    // 종목 정보가 비어있으면 우선 수집
    if (assetRepository.count() == 0) {
      log.info("[일일배치] Asset 테이블 비어있음, 종목 수집 먼저 실행");
      collectSymbolsOnly();
    }

    // 캔들 및 배당 데이터 전체 수집
    batchService.collectAll(startDate, endDate, true);
    log.info("[일일배치] 완료");
  }
}