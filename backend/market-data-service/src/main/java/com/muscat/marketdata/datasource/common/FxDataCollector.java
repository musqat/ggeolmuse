package com.muscat.marketdata.datasource.common;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import com.muscat.marketdata.datasource.common.MarketDataProvider.FxSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// 공급원별 FxSource 로 앱 시작 때 과거 · 빠진 환율을 채우고 하루치를 받는 수집기
@Slf4j
@Component
@RequiredArgsConstructor
public class FxDataCollector {

    private final FxSource fxSource;
    private final FxRateRepository fxRateRepository;
    private final MarketDataProperties properties;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @PostConstruct
    void logSettings() {
        log.info("[환율수집] 초기화 설정: 과거데이터={}, 증분={}",
                properties.getFxIngest().getBackfill().isEnabled(),
                properties.getFxIngest().getIncremental().isEnabled());
    }

    // 앱이 준비된 뒤 다른 스레드에서 과거 · 빠진 환율 채우기(실패는 로그만)
    @Async
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void collectOnStartup() {
        try {
            if (properties.getFxIngest().getBackfill().isEnabled()) {
                runHistoricalCollection();
            }

            if (properties.getFxIngest().getIncremental().isEnabled()) {
                runIncrementalCollection();
            }
        } catch (Exception e) {
            log.error("[환율수집] 실패 - 서비스는 계속 실행됩니다", e);
        }
    }

    @Transactional
    protected void runHistoricalCollection() {
        LocalDate endDate = LocalDate.now(KST);
        LocalDate startDate = endDate.minusDays(properties.getFxIngest().getBackfill().getLookbackDays());

        log.info("[환율수집] 과거데이터 시작: {} ~ {}", startDate, endDate);

        int totalSaved = 0;
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            LocalDate batchEndDate = currentDate.plusMonths(1).minusDays(1);
            if (batchEndDate.isAfter(endDate)) {
                batchEndDate = endDate;
            }

            List<FxRate> collected = collectDateRange(currentDate, batchEndDate);
            fxRateRepository.saveAll(collected);
            totalSaved += collected.size();

            log.info("[환율수집] 배치 완료: {} ~ {}, 저장={}", currentDate, batchEndDate, collected.size());
            currentDate = batchEndDate.plusDays(1);
        }

        log.info("[환율수집] 과거데이터 완료: 총 {}건", totalSaved);
    }

    // 시작일 ~ 오늘 평일 중 저장 안 된 날을 최근 날부터 최대 defaultDays 일
    @Transactional
    protected void runIncrementalCollection() {
        LocalDate today = LocalDate.now(KST);
        List<LocalDate> missing = missingWeekdays(properties.getFxIngest().getStartDate(), today);
        if (missing.isEmpty()) {
            log.info("[환율수집] 증분수집 스킵: 빈 날 없음 (오늘={})", today);
            return;
        }

        int limit = properties.getFxIngest().getIncremental().getDefaultDays();
        List<LocalDate> targets = missing.subList(Math.max(0, missing.size() - limit), missing.size());
        log.info("[환율수집] 증분수집: 빈 날 {}일 중 {}일 ({} ~ {})",
                missing.size(), targets.size(), targets.get(0), targets.get(targets.size() - 1));

        List<FxRate> collected = new ArrayList<>();
        for (LocalDate date : targets) {
            try {
                collectSingleDate(date, true).ifPresent(collected::add);
            } catch (Exception e) {
                log.warn("[환율수집] 빈 날 수집 실패: date={}, error={}", date, e.getMessage());
            }
        }
        fxRateRepository.saveAll(collected);
        log.info("[환율수집] 증분수집 완료: {}건", collected.size());
    }

    // from ~ to 평일 중 저장 안 된 날(오래된 날부터)
    public List<LocalDate> missingWeekdays(LocalDate from, LocalDate to) {
        Set<LocalDate> stored = fxRateRepository.findByDateBetween(from, to).stream()
                .map(FxRate::getDate)
                .collect(Collectors.toSet());
        return weekdays(from, to).stream().filter(date -> !stored.contains(date)).toList();
    }

    /**
     * 특정 날짜의 환율 데이터 수집 (주말 fallback 지원)
     */
    public Optional<FxRate> collectSingleDate(LocalDate targetDate, boolean useBusinessDayFallback) {
        LocalDate currentDate = targetDate;
        int attempts = 0;

        while (attempts <= properties.getFx().getMaxFallbackDays()) {
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
                log.warn("[환율수집] 실패: date={}, error={}", currentDate, e.getMessage());
            }

            if (!useBusinessDayFallback) {
                break;
            }

            currentDate = getPreviousBusinessDay(currentDate);
            attempts++;

            if (attempts > properties.getFx().getMaxFallbackDays()) {
                throw new IllegalStateException("환율 폴백 기간 초과: " + targetDate);
            }
        }

        return Optional.empty();
    }

    /**
     * 날짜 범위의 환율 데이터 수집 (평일만)
     */
    public List<FxRate> collectDateRange(LocalDate startDate, LocalDate endDate) {
        List<FxRate> collected = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (isWeekday(date)) {
                collectSingleDate(date, true).ifPresent(collected::add);
            }
        }

        return collected;
    }

    // from ~ to 의 평일
    public static List<LocalDate> weekdays(LocalDate from, LocalDate to) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (isWeekday(date)) {
                days.add(date);
            }
        }
        return days;
    }

    private static boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private static LocalDate getPreviousBusinessDay(LocalDate date) {
        LocalDate prev = date.minusDays(1);

        while (prev.getDayOfWeek() == DayOfWeek.SATURDAY || prev.getDayOfWeek() == DayOfWeek.SUNDAY) {
            prev = prev.minusDays(1);
        }

        return prev;
    }
}
