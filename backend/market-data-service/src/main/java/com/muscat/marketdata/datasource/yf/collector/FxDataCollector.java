package com.muscat.marketdata.datasource.yf.collector;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import com.muscat.marketdata.datasource.common.MarketDataProvider.FxSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Yahoo Finance (KoreaExim) 환율 데이터 초기 수집
 *
 * KoreaEximFxClient를 통해 USD/KRW 환율 데이터를 수집합니다.
 */
@Slf4j
@Component
@Order(2)
@ConditionalOnProperty(
    name = "marketdata.provider",
    havingValue = "yahoo"
)
@RequiredArgsConstructor
public class FxDataCollector implements CommandLineRunner {

    private final FxSource fxSource;
    private final FxRateRepository fxRateRepository;
    private final MarketDataProperties properties;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @PostConstruct
    void logSettings() {
        log.info("[YF-환율수집] 초기화 설정: 과거데이터={}, 증분={}",
                properties.getFxIngest().getBackfill().isEnabled(),
                properties.getFxIngest().getIncremental().isEnabled());
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
            log.error("[YF-환율수집] 실패 - 서비스는 계속 실행됩니다", e);
        }
    }

    @Transactional
    protected void runHistoricalCollection() {
        LocalDate endDate = LocalDate.now(KST);
        LocalDate startDate = endDate.minusDays(properties.getFxIngest().getBackfill().getLookbackDays());

        log.info("[YF-환율수집] 과거데이터 시작: {} ~ {}", startDate, endDate);

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

            log.info("[YF-환율수집] 배치 완료: {} ~ {}, 저장={}", currentDate, batchEndDate, collected.size());
            currentDate = batchEndDate.plusDays(1);
        }

        log.info("[YF-환율수집] 과거데이터 완료: 총 {}건", totalSaved);
    }

    @Transactional
    protected void runIncrementalCollection() {
        LocalDate today = LocalDate.now(KST);
        LocalDate lastDate = fxRateRepository.findAll().stream()
                .map(FxRate::getDate)
                .max(LocalDate::compareTo)
                .orElse(null);

        LocalDate fromDate = (lastDate == null)
                ? today.minusDays(properties.getFxIngest().getIncremental().getDefaultDays())
                : lastDate.plusDays(1);

        if (!fromDate.isAfter(today)) {
            log.info("[YF-환율수집] 증분수집: {} ~ {}", fromDate, today);
            List<FxRate> collected = collectDateRange(fromDate, today);
            fxRateRepository.saveAll(collected);
            log.info("[YF-환율수집] 증분수집 완료: {}건", collected.size());
        } else {
            log.info("[YF-환율수집] 증분수집 스킵: 최신 데이터 (마지막={}, 오늘={})", lastDate, today);
        }
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
                log.warn("[YF-환율수집] 실패: date={}, error={}", currentDate, e.getMessage());
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
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                collectSingleDate(date, true).ifPresent(collected::add);
            }
        }

        return collected;
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
}
