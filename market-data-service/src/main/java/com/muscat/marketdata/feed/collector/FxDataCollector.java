package com.muscat.marketdata.feed.collector;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.provider.MarketDataProvider.FxSource;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@Order(2)
@EnableScheduling
@RequiredArgsConstructor
public class FxDataCollector implements CommandLineRunner {

    private final FxSource fxSource;
    private final MarketDataProperties properties;

    @PersistenceContext
    private EntityManager em;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @PostConstruct
    void logSettings() {
        log.info("[환율수집] 설정: 과거데이터수집={}, 증분수집={}, 스케줄러={}",
                properties.getFxIngest().getBackfill().isEnabled(),
                properties.getFxIngest().getIncremental().isEnabled(),
                properties.getFxIngest().getScheduler().isEnabled());
    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            if (properties.getFxIngest().getBackfill().isEnabled()) {
                runHistoricalCollection();
            }

            if (properties.getFxIngest().getIncremental().isEnabled()) {
                runIncrementalCollection();
            }
        } catch (Exception e) {
            log.error("[환율수집] 환율 데이터 수집 실패 - 서비스는 계속 실행됩니다", e);
        }
    }

    @Transactional
    protected void runHistoricalCollection() {
        LocalDate endDate = LocalDate.now(KST);
        LocalDate startDate = endDate.minusDays(properties.getFxIngest().getBackfill().getLookbackDays());

        log.info("[환율수집] 과거데이터수집 시작: {} ~ {} ({}일 과거)", startDate, endDate, properties.getFxIngest().getBackfill().getLookbackDays());

        LocalDate currentDate = startDate;
        int totalSaved = 0;

        while (!currentDate.isAfter(endDate)) {
            LocalDate batchEndDate = currentDate.plusMonths(1).minusDays(1);
            if (batchEndDate.isAfter(endDate)) {
                batchEndDate = endDate;
            }

            List<FxRate> collectedRates = collectDateRange(currentDate, batchEndDate);
            int savedCount = saveCollectedRates(collectedRates);
            totalSaved += savedCount;

            log.info("[환율수집] 과거데이터 배치 완료: {} ~ {}, 저장건수={}", currentDate, batchEndDate, savedCount);
            currentDate = batchEndDate.plusDays(1);
        }

        log.info("[환율수집] 과거데이터수집 완료: 총 저장건수={}", totalSaved);
    }

    @Transactional
    protected void runIncrementalCollection() {
        LocalDate today = LocalDate.now(KST);
        LocalDate lastCollectedDate = findLatestCollectedDate();
        LocalDate fromDate = (lastCollectedDate == null)
                ? today.minusDays(properties.getFxIngest().getIncremental().getDefaultDays())
                : lastCollectedDate.plusDays(1);

        if (!fromDate.isAfter(today)) {
            log.info("[환율수집] 증분수집 시작: {} ~ {}", fromDate, today);
            List<FxRate> collectedRates = collectDateRange(fromDate, today);
            int savedCount = saveCollectedRates(collectedRates);
            log.info("[환율수집] 증분수집 완료: 저장건수={}", savedCount);
        } else {
            log.info("[환율수집] 증분수집 건너뜀: 이미 최신 (마지막수집일={}, 오늘={})", lastCollectedDate, today);
        }
    }

    @Scheduled(cron = "${marketdata.constants.fx-ingest.scheduler.cron:0 10 11 * * MON-FRI}",
            zone = "${marketdata.constants.fx-ingest.scheduler.zone:Asia/Seoul}")
    @Transactional
    public void collectDailyRateAt1110() {
        if (!properties.getFxIngest().getScheduler().isEnabled()) {
            return;
        }

        try {
            LocalDate today = LocalDate.now(KST);
            log.info("[환율수집] 일일 스케줄 실행: {}", today);
            Optional<FxRate> collectedRate = collectSingleDate(today, true);

            if (collectedRate.isPresent()) {
                FxRate savedRate = saveRate(today, collectedRate.get().getRate());
                log.info("[환율수집] 일일 수집 완료: {} -> {}", savedRate.getDate(), savedRate.getRate());
            } else {
                log.warn("[환율수집] 일일 수집 실패: 환율 데이터를 가져올 수 없습니다");
            }
        } catch (Exception e) {
            log.error("[환율수집] 일일 수집 실패", e);
        }
    }

    @Transactional(readOnly = true)
    public LocalDate findLatestCollectedDate() {
        return em.createQuery("select max(f.date) from FxRate f", LocalDate.class)
                .getSingleResult();
    }

    /**
     * 특정 날짜의 환율 데이터를 외부 API에서 수집
     */
    public Optional<FxRate> collectSingleDate(LocalDate targetDate, boolean useBusinessDayFallback) {
        log.debug("환율 데이터 수집 시도: date={}, fallback={}", targetDate, useBusinessDayFallback);
        
        LocalDate currentDate = targetDate;
        int attempts = 0;

        while (attempts <= properties.getFx().getMaxFallbackDays()) {
            try {
                Optional<BigDecimal> rateOpt = fxSource.fetchFx(currentDate);
                if (rateOpt.isPresent()) {
                    BigDecimal rate = rateOpt.get();
                    log.debug("환율 데이터 수집 성공: date={}, rate={}", currentDate, rate);
                    return Optional.of(FxRate.builder()
                        .date(targetDate) // 원래 요청 날짜로 저장
                        .rate(rate)
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

            if (attempts > properties.getFx().getMaxFallbackDays()) {
                throw new IllegalStateException("환율 데이터 폴백 기간 초과: " + targetDate + "부터 " + 
                    properties.getFx().getMaxFallbackDays() + "일");
            }
        }

        log.warn("환율 데이터 수집 실패: date={}", targetDate);
        return Optional.empty();
    }

    /**
     * 날짜 범위의 환율 데이터를 외부 API에서 수집
     */
    public List<FxRate> collectDateRange(LocalDate startDate, LocalDate endDate) {
        log.info("환율 데이터 범위 수집: {} ~ {}", startDate, endDate);
        
        List<FxRate> collectedRates = new ArrayList<>();
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            // 영업일만 처리
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                Optional<FxRate> rate = collectSingleDate(date, true);
                rate.ifPresent(collectedRates::add);
            }
        }
        
        log.info("환율 데이터 범위 수집 완료: {} 건", collectedRates.size());
        return collectedRates;
    }

    // 수집된 환율 데이터 일괄 저장
    private int saveCollectedRates(List<FxRate> collectedRates) {
        int savedCount = 0;
        for (FxRate rate : collectedRates) {
            saveRate(rate.getDate(), rate.getRate());
            savedCount++;
        }
        return savedCount;
    }

    /**
     * 환율 데이터 저장
     */
    private FxRate saveRate(LocalDate date, BigDecimal usdToKrw) {
        Objects.requireNonNull(date, "날짜는 필수입니다");
        Objects.requireNonNull(usdToKrw, "환율은 필수입니다");
        BigDecimal normalizedRate = MoneyUtils.roundExchangeRate(usdToKrw);

        FxRate existing = em.find(FxRate.class, date);
        if (existing == null) {
            FxRate newRate = FxRate.builder().date(date).rate(normalizedRate).build();
            em.persist(newRate);
            log.debug("[환율저장] {} -> {}", date, normalizedRate);
            return newRate;
        } else {
            if (existing.getRate() == null || existing.getRate().compareTo(normalizedRate) != 0) {
                em.detach(existing);
                FxRate updatedRate = FxRate.builder().date(date).rate(normalizedRate).build();
                em.merge(updatedRate);
                log.debug("[환율업데이트] {} -> {}", date, normalizedRate);
                return updatedRate;
            }
            return existing;
        }
    }

    /**
     * 이전 영업일 계산
     */
    private static LocalDate getPreviousBusinessDay(LocalDate date) {
        LocalDate previousDate = date.minusDays(1);
        
        while (previousDate.getDayOfWeek() == DayOfWeek.SATURDAY || 
               previousDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            previousDate = previousDate.minusDays(1);
        }
        
        return previousDate;
    }
}