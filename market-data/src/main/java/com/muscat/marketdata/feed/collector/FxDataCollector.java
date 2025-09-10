package com.muscat.marketdata.feed.collector;

import com.muscat.marketdata.common.util.FxRateCalculator;
import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.provider.MarketDataProvider.FxSource;
import com.muscat.marketdata.provider.config.FxCollectProps;
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

    private final FxCollectProps props;
    private final FxSource fxSource;
    private final MarketDataProperties properties;
    private final FxRateCalculator calculator;

    @PersistenceContext
    private EntityManager em;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @PostConstruct
    void logSettings() {
        log.info("[환율수집] 설정: 과거데이터수집={}, 증분수집={}, 스케줄러={}",
                props.getBackfill().isEnabled(),
                props.getIncremental().isEnabled(),
                props.getScheduler().isEnabled());
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (props.getBackfill().isEnabled()) {
            runHistoricalCollection();
        }

        if (props.getIncremental().isEnabled()) {
            runIncrementalCollection();
        }
    }

    @Transactional
    protected void runHistoricalCollection() {
        LocalDate startDate = Objects.requireNonNull(props.getBackfill().getStart(),
                "과거데이터수집 시작일은 필수 설정입니다");
        LocalDate endDate = props.getBackfill().getEnd() != null
                ? props.getBackfill().getEnd()
                : LocalDate.now(KST);

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠릅니다");
        }

        log.info("[환율수집] 과거데이터수집 시작: {} ~ {}", startDate, endDate);

        LocalDate currentDate = startDate;
        int totalSaved = 0;

        while (!currentDate.isAfter(endDate)) {
            LocalDate batchEndDate = currentDate.plusMonths(1).minusDays(1);
            if (batchEndDate.isAfter(endDate)) {
                batchEndDate = endDate;
            }

            List<FxRate> collectedRates = collectDateRange(currentDate, batchEndDate);
            List<FxRate> savedRates = new ArrayList<>();
            for (FxRate rate : collectedRates) {
                savedRates.add(saveRate(rate.getDate(), rate.getRate()));
            }
            totalSaved += savedRates.size();

            log.info("[환율수집] 과거데이터 배치 완료: {} ~ {}, 저장건수={}", currentDate, batchEndDate, savedRates.size());
            currentDate = batchEndDate.plusDays(1);
        }

        log.info("[환율수집] 과거데이터수집 완료: 총 저장건수={}", totalSaved);
    }

    @Transactional
    protected void runIncrementalCollection() {
        LocalDate today = LocalDate.now(KST);
        LocalDate lastCollectedDate = findLatestCollectedDate();
        LocalDate fromDate = (lastCollectedDate == null)
                ? today.minusDays(props.getIncremental().getDefaultDays())
                : lastCollectedDate.plusDays(1);

        if (!fromDate.isAfter(today)) {
            log.info("[환율수집] 증분수집 시작: {} ~ {}", fromDate, today);
            List<FxRate> collectedRates = collectDateRange(fromDate, today);
            List<FxRate> savedRates = new ArrayList<>();
            for (FxRate rate : collectedRates) {
                savedRates.add(saveRate(rate.getDate(), rate.getRate()));
            }
            log.info("[환율수집] 증분수집 완료: 저장건수={}", savedRates.size());
        } else {
            log.info("[환율수집] 증분수집 건너뜀: 이미 최신 (마지막수집일={}, 오늘={})", lastCollectedDate, today);
        }
    }

    @Scheduled(cron = "${marketdata.fx.feed.scheduler.cron:0 10 11 * * MON-FRI}",
            zone = "${marketdata.fx.feed.scheduler.zone:Asia/Seoul}")
    @Transactional
    public void collectDailyRateAt1110() {
        if (!props.getScheduler().isEnabled()) {
            return;
        }

        try {
            LocalDate today = LocalDate.now(KST);
            log.info("[환율수집] 일일 스케줄 실행: {}", today);
            Optional<FxRate> collectedRate = collectSingleDate(today, true);
            FxRate savedRate = null;
            if (collectedRate.isPresent()) {
                savedRate = saveRate(today, collectedRate.get().getRate());
            }
            log.info("[환율수집] 일일 수집 완료: {} -> {}", savedRate.getDate(), savedRate.getRate());
        } catch (Exception e) {
            log.error("[환율수집] 일일 수집 실패", e);
        }
    }

    @Transactional(readOnly = true)
    public LocalDate findLatestCollectedDate() {
        List<LocalDate> maxDateList = em.createQuery("select max(f.date) from FxRate f", LocalDate.class)
                .getResultList();
        return (maxDateList == null || maxDateList.isEmpty()) ? null : maxDateList.get(0);
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