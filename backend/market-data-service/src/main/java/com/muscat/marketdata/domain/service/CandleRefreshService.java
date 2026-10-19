package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.domain.entity.AdminJobRun;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AdminJobRunRepository;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 관리자 캔들 재수집: 지정 종목은 바로 발행, 전체는 실행 기록을 남기고 발행은 뒤로
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleRefreshService {

  static final String REFRESH_ALL_JOB = "candle-refresh-all";
  private static final LocalDate FULL_FROM = LocalDate.of(1970, 1, 1);
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

  private final AssetRepository assetRepository;
  private final AssetEventProducer assetEventProducer;
  private final AdminJobRunRepository adminJobRunRepository;
  private final CandleRefreshAllPublisher refreshAllPublisher;

  public record RefreshResult(int requested, int published, List<String> notFound, LocalDate from, LocalDate to) {
  }

  // 지정한 종목만 배당까지 다시 수집. 발행은 Kafka 컨슈머가 비동기로 처리
  public RefreshResult refresh(List<String> symbols, LocalDate from) {
    LocalDate start = from != null ? from : FULL_FROM;
    LocalDate to = LocalDate.now(NEW_YORK);
    List<String> requested = symbols != null ? symbols : List.of();
    int published = 0;
    int delisted = 0;
    List<String> notFound = new ArrayList<>();

    for (String raw : requested) {
      String symbol = raw.trim().toUpperCase();
      Asset asset = assetRepository.findById(symbol).orElse(null);
      if (asset == null) {
        notFound.add(symbol);
        continue;
      }
      // 상장폐지 종목은 야후가 404 를 낸다. 발행해봐야 빈 응답만 받는다
      if (!Boolean.TRUE.equals(asset.getActive())) {
        delisted++;
        continue;
      }
      assetEventProducer.publishAssetCreated(asset, true, start, to, true);
      published++;
    }

    log.info("캔들 재수집 요청: 요청 {}개, 발행 {}개, 상장폐지 {}개, 없는 종목 {}개, from={}",
        requested.size(), published, delisted, notFound.size(), start);
    return new RefreshResult(requested.size(), published, notFound, start, to);
  }

  // 활성 종목 전체를 1970-01-01 부터. 실행 기록을 먼저 남기고 발행은 뒤에서(1만 건 넘게 발행하면 게이트웨이 30초를 넘김)
  public AdminJobRun refreshAll() {
    List<Asset> assets = assetRepository.findByActiveTrue();
    AdminJobRun run = adminJobRunRepository.save(new AdminJobRun(REFRESH_ALL_JOB, LocalDateTime.now(KST), assets.size()));

    refreshAllPublisher.publish(assets, FULL_FROM, LocalDate.now(NEW_YORK));
    log.info("전체 캔들 재수집 요청: 대상 {}개, from={}", assets.size(), FULL_FROM);
    return run;
  }

  // 마지막 전체 재수집 기록. 한 번도 안 했으면 빔
  public Optional<AdminJobRun> lastRefreshAll() {
    return adminJobRunRepository.findById(REFRESH_ALL_JOB);
  }
}
