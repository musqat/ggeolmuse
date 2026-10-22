package com.muscat.marketdata.datasource.alphavantage.scheduler;

import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.marketdata.datasource.alphavantage.provider.AlphaVantageCandleSource;
import com.muscat.marketdata.datasource.alphavantage.provider.AlphaVantageSymbolSource;
import com.muscat.marketdata.datasource.alphavantage.service.MarketCapCollectionService;
import com.muscat.marketdata.datasource.common.MarketDataProvider.FxSource;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// AlphaVantage 데이터 통합 스케줄러(환율 · 캔들 · 시가총액)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "marketdata.provider", havingValue = "alphavantage")
public class AlphaVantageScheduler {

  private final FxSource fxSource;
  private final AlphaVantageCandleSource candleSource;
  private final AlphaVantageSymbolSource symbolSource;

  private final AssetRepository assetRepository;
  private final FxRateRepository fxRateRepository;
  private final AssetEventProducer assetEventProducer;
  private final MarketCapCollectionService marketCapCollectionService;

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final int MAX_FALLBACK_DAYS = 7;
  private static final double INITIAL_COLLECTION_THRESHOLD = 0.95;  // 95% 수집 완료 기준
  private static final int MIN_FX_RATES_FOR_INITIAL_COLLECTION = 365;  // 1년치 미만이면 full로 수집

  // DB 환율이 1년치 미만이면 시작할 때 outputsize=full 로 한 번에 채움
  @PostConstruct
  @Transactional
  public void collectInitialFxRatesIfNeeded() {
    try {
      long existingCount = fxRateRepository.count();

      if (existingCount >= MIN_FX_RATES_FOR_INITIAL_COLLECTION) {
        log.info("[초기 환율] 기존 데이터 충분 ({}개), 초기 수집 스킵", existingCount);
        return;
      }

      log.info("=== [초기 환율] 데이터 수집 시작 (기존: {}개) ===", existingCount);

      // outputsize=full로  환율 데이터를 1번의 API 호출로 가져옴
      Map<LocalDate, BigDecimal> allRates = ((com.muscat.marketdata.datasource.alphavantage.provider.AlphaVantageFxSource) fxSource)
        .fetchAllFxRates();

      if (allRates.isEmpty()) {
        log.warn("[초기 환율] 수집된 데이터 없음");
        return;
      }

      // FxRate 엔티티로 변환 및 저장
      List<FxRate> fxRates = allRates.entrySet().stream()
        .map(entry -> FxRate.builder()
          .date(entry.getKey())
          .rate(MoneyUtils.roundExchangeRate(entry.getValue()))
          .currencyPair("USD/KRW")
          .build())
        .toList();

      fxRateRepository.saveAll(fxRates);

      log.info("=== [초기 환율] 데이터 수집 완료: {}개 저장 ===", fxRates.size());
    } catch (Exception e) {
      log.error("[초기 환율] 데이터 수집 실패", e);
    }
  }

  // 주말 · 휴일이면 이전 영업일 환율로 채움
  @Scheduled(cron = "${alphavantage.scheduler.fx.cron:0 10 11 * * MON-FRI}",
    zone = "${alphavantage.scheduler.zone:Asia/Seoul}")
  @SchedulerLock(
    name = "updateFxRates",
    lockAtMostFor = "10m",
    lockAtLeastFor = "1m"
  )
  @Transactional
  public void updateFxRates() {
    // 초기 수집 진행 중이면 스케줄러 스킵
    if (isInitialCollectionInProgress()) {
      log.info("=== [AV 스케줄러] 초기 수집 진행 중 - 환율 업데이트 스킵 ===");
      return;
    }

    log.info("=== [AV 스케줄러] 환율 업데이트 시작 ===");

    try {
      LocalDate today = LocalDate.now(KST);
      Optional<FxRate> rate = collectFxRate(today, true);

      if (rate.isPresent()) {
        FxRate saved = fxRateRepository.save(rate.get());
        log.info("[AV 스케줄러] 환율 업데이트 완료: {} -> {}", saved.getDate(), saved.getRate());
      } else {
        log.warn("[AV 스케줄러] 환율 데이터를 가져올 수 없음");
      }
    } catch (Exception e) {
      log.error("[AV 스케줄러] 환율 업데이트 실패", e);
    }
  }

  // 종목마다 이벤트로 넘겨 비동기로 증분 수집(마지막 수집일 이후만)
  @Scheduled(cron = "${alphavantage.scheduler.candle.cron:0 0 7 * * MON-FRI}",
    zone = "${alphavantage.scheduler.zone:Asia/Seoul}")
  @SchedulerLock(
    name = "updateCandles",
    lockAtMostFor = "9m",
    lockAtLeastFor = "1m"
  )
  @Transactional(readOnly = true)
  public void updateCandles() {
    // 초기 수집 진행 중이면 스케줄러 스킵
    if (isInitialCollectionInProgress()) {
      log.info("=== [AV 스케줄러] 초기 수집 진행 중 - 캔들 업데이트 스킵 ===");
      return;
    }

    log.info("=== [AV 스케줄러] 캔들 업데이트 시작 ===");

    try {
      List<Asset> assets = assetRepository.findByActiveTrue();
      log.info("[AV 스케줄러] 업데이트 대상 종목 (활성화만): {}개", assets.size());

      LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));
      // 1년치 요청
      // CandleUpdateService가 마지막 날짜+1부터 자동 수집 (중복 방지)
      LocalDate from = today.minusYears(1);

      int published = 0;
      for (Asset asset : assets) {
        try {
          // Kafka 이벤트 발행 (비동기 처리)
          // CandleUpdateService가 마지막 날짜 확인하여 증분 수집
          assetEventProducer.publishAssetCreated(
            asset,
            true,           // collectData
            from,           // 1년치 (이미 있으면 마지막 날짜+1부터, 중복 방지)
            today,
            false           // includeDividends (일일 업데이트에서는 제외)
          );
          published++;
          log.debug("[AV 스케줄러] 캔들 수집 이벤트 발행: {}", asset.getSymbol());
        } catch (Exception e) {
          log.warn("[AV 스케줄러] 캔들 이벤트 발행 실패: symbol={}", asset.getSymbol(), e);
        }
      }

      log.info("=== [AV 스케줄러] 캔들 업데이트 완료: {}개 이벤트 발행 ===", published);
    } catch (Exception e) {
      log.error("[AV 스케줄러] 캔들 업데이트 실패", e);
    }
  }

  // OVERVIEW API 로 시가총액 갱신
  @Scheduled(cron = "${alphavantage.scheduler.marketcap.cron:0 0 10 * * MON-FRI}",
    zone = "${alphavantage.scheduler.zone:Asia/Seoul}")
  @SchedulerLock(
    name = "updateMarketCaps",
    lockAtMostFor = "30m",
    lockAtLeastFor = "5m"
  )
  public void updateMarketCaps() {
    // 초기 수집 진행 중이면 스케줄러 스킵
    if (isInitialCollectionInProgress()) {
      log.info("=== [AV 스케줄러] 초기 수집 진행 중 - 시가총액 업데이트 스킵 ===");
      return;
    }

    log.info("=== [AV 스케줄러] 시가총액 업데이트 시작 ===");

    try {
      marketCapCollectionService.updateAllMarketCaps();
    } catch (Exception e) {
      log.error("[AV 스케줄러] 시가총액 업데이트 실패", e);
    }

    log.info("=== [AV 스케줄러] 시가총액 업데이트 완료 ===");
  }

  // ==================== Helper Methods ====================

  /**
   * 특정 날짜의 환율 데이터 수집 (주말 fallback 지원)
   */
  private Optional<FxRate> collectFxRate(LocalDate targetDate, boolean useBusinessDayFallback) {
    LocalDate currentDate = targetDate;
    int attempts = 0;

    while (attempts <= MAX_FALLBACK_DAYS) {
      try {
        Optional<BigDecimal> rateOpt = fxSource.fetchFx(currentDate);
        if (rateOpt.isPresent()) {
          BigDecimal rate = MoneyUtils.roundExchangeRate(rateOpt.get());
          return Optional.of(FxRate.builder()
            .date(targetDate)
            .rate(rate)
            .currencyPair("USD/KRW")
            .build());
        }
      } catch (Exception e) {
        log.warn("환율 데이터 수집 실패: date={}, error={}", currentDate, e.getMessage());
      }

      if (!useBusinessDayFallback) {
        break;
      }

      currentDate = getPreviousBusinessDay(currentDate);
      attempts++;

      if (attempts > MAX_FALLBACK_DAYS) {
        throw new IllegalStateException("환율 폴백 기간 초과: " + targetDate);
      }
    }

    return Optional.empty();
  }

  /**
   * 이전 영업일 계산
   */
  private static LocalDate getPreviousBusinessDay(LocalDate date) {
    LocalDate prev = date.minusDays(1);

    while (prev.getDayOfWeek() == DayOfWeek.SATURDAY || prev.getDayOfWeek() == DayOfWeek.SUNDAY) {
      prev = prev.minusDays(1);
    }

    return prev;
  }

  // 캔들이 있는 종목이 95% 미만이면 초기 수집 중으로 보고 건너뜀(API 한도 경쟁 방지)
  private boolean isInitialCollectionInProgress() {
    try {
      long totalAssets = assetRepository.count();
      long assetsWithCandles = assetRepository.countWithCandles();

      if (totalAssets == 0) {
        log.debug("[스케줄러 체크] Asset 없음, 스케줄러 실행");
        return false;
      }

      double completionRate = (double) assetsWithCandles / totalAssets;
      boolean inProgress = completionRate < INITIAL_COLLECTION_THRESHOLD;

      if (inProgress) {
        log.info("[스케줄러 체크] 초기 수집 진행 중: {}/{}개 ({}%), 스케줄러 스킵",
          assetsWithCandles, totalAssets, String.format("%.1f", completionRate * 100));
      } else {
        log.debug("[스케줄러 체크] 초기 수집 완료: {}/{}개 ({}%), 스케줄러 실행",
          assetsWithCandles, totalAssets, String.format("%.1f", completionRate * 100));
      }

      return inProgress;
    } catch (Exception e) {
      log.warn("[스케줄러 체크] 초기 수집 여부 확인 실패, 안전하게 스케줄러 실행", e);
      return false;  // 에러 시 안전하게 스케줄러 실행
    }
  }
}
