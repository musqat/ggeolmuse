package com.muscat.marketdata.feed.collector;

import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.feed.service.FxRateService;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@Order(2)
@EnableScheduling
@RequiredArgsConstructor
public class FxDataCollector implements CommandLineRunner {

    private final FxRateService fxRateService;
    private final FxCollectProps props;

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

            List<FxRate> savedRates = fxRateService.syncDateRange(currentDate, batchEndDate);
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
            List<FxRate> savedRates = fxRateService.syncDateRange(fromDate, today);
            log.info("[환율수집] 증분수집 완료: 저장건수={}", savedRates.size());
        } else {
            log.info("[환율수집] 증분수집 건너뜀: 이미 최신 (마지막수집일={}, 오늘={})", lastCollectedDate, today);
        }
    }

    @Scheduled(cron = "${marketdata.fx.feed.scheduler.cron:0 10 11 * * MON-FRI}",
            zone = "${marketdata.fx.feed.scheduler.zone:Asia/Seoul}")
    public void collectDailyRateAt1110() {
        if (!props.getScheduler().isEnabled()) {
            return;
        }

        try {
            LocalDate today = LocalDate.now(KST);
            log.info("[환율수집] 일일 스케줄 실행: {}", today);
            FxRate savedRate = fxRateService.syncSingleDate(today, true);
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
}