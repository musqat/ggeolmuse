package com.muscat.marketdata.feed.service;

import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.provider.MarketDataProvider.FxSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class FxRateService {

    private final FxSource fxSource;

    @PersistenceContext
    private EntityManager em;

    private static final int SCALE = 6;
    private static final RoundingMode RM = RoundingMode.HALF_UP;
    private static final int MAX_FALLBACK_DAYS = 7;

    @Transactional
    public FxRate saveRate(LocalDate date, BigDecimal usdToKrw) {
        Objects.requireNonNull(date, "날짜는 필수입니다");
        Objects.requireNonNull(usdToKrw, "환율은 필수입니다");
        BigDecimal normalizedRate = normalize(usdToKrw);

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
                FxRate saved = em.merge(updatedRate);
                log.debug("[환율업데이트] {} -> {}", date, normalizedRate);
                return saved;
            }
            return existing;
        }
    }

    @Transactional(readOnly = true)
    public FxRate findByDate(LocalDate date) {
        return em.find(FxRate.class, date);
    }

    @Transactional(readOnly = true)
    public List<FxRate> findRateRange(LocalDate startDate, LocalDate endDate) {
        return em.createQuery("select f from FxRate f where f.date between :start and :end order by f.date", FxRate.class)
                .setParameter("start", startDate)
                .setParameter("end", endDate)
                .getResultList();
    }

    @Transactional
    public FxRate syncSingleDate(LocalDate targetDate, boolean useBusinessDayFallback) {
        Objects.requireNonNull(targetDate, "대상 날짜는 필수입니다");

        LocalDate currentDate = targetDate;
        int attempts = 0;

        while (true) {
            Optional<BigDecimal> rateData = fxSource.fetchUsdKrw(currentDate);
            if (rateData.isPresent()) {
                return saveRate(currentDate, rateData.get());
            }

            if (!useBusinessDayFallback) {
                throw new IllegalStateException("환율 데이터를 찾을 수 없습니다: " + currentDate);
            }

            attempts++;
            if (attempts > MAX_FALLBACK_DAYS) {
                throw new IllegalStateException("환율 데이터 폴백 기간 초과: " + targetDate + "부터 " + MAX_FALLBACK_DAYS + "일");
            }
            currentDate = getPreviousBusinessDay(currentDate);
        }
    }

    @Transactional
    public List<FxRate> syncDateRange(LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(startDate, "시작일은 필수입니다");
        Objects.requireNonNull(endDate, "종료일은 필수입니다");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠릅니다");
        }

        List<FxRate> savedRates = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            Optional<BigDecimal> rateData = fxSource.fetchUsdKrw(date);
            if (rateData.isPresent()) {
                savedRates.add(saveRate(date, rateData.get()));
            }
        }
        return savedRates;
    }

    @Transactional
    public Optional<FxRate> getLatestRate() {
        List<FxRate> latest = em.createQuery("select f from FxRate f order by f.date desc", FxRate.class)
                .setMaxResults(1)
                .getResultList();

        if (!latest.isEmpty()) {
            return Optional.of(latest.get(0));
        }

        log.info("[환율조회] DB가 비어있어서 오늘 환율을 API에서 가져옵니다");
        return fxSource.fetchUsdKrw(LocalDate.now())
                .map(rate -> saveRate(LocalDate.now(), rate));
    }

    public boolean isDataSourceHealthy() {
        try {
            fxSource.fetchUsdKrw(LocalDate.now().minusDays(1));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static BigDecimal calculateInverseRate(BigDecimal usdToKrw) {
        Objects.requireNonNull(usdToKrw, "환율은 필수입니다");
        return BigDecimal.ONE.divide(usdToKrw, SCALE, RM);
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value.setScale(SCALE, RM);
    }

    private static LocalDate getPreviousBusinessDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.MONDAY) return date.minusDays(3);
        if (dayOfWeek == DayOfWeek.SUNDAY) return date.minusDays(2);
        return date.minusDays(1);
    }
    
    @Transactional
    public int generateHistoricalRates(LocalDate startDate, LocalDate endDate, BigDecimal baseRate) {
        Objects.requireNonNull(startDate, "시작일은 필수입니다");
        Objects.requireNonNull(endDate, "종료일은 필수입니다");
        Objects.requireNonNull(baseRate, "기준 환율은 필수입니다");
        
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠릅니다");
        }
        
        log.info("[환율생성] 과거 환율 데이터 생성: {} ~ {}, 기준환율={}", startDate, endDate, baseRate);
        
        int savedCount = 0;
        Random random = new Random();
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            // 기존 데이터가 있으면 건너뛰기
            if (findByDate(date) != null) {
                continue;
            }
            
            // 기준 환율 ±5% 범위에서 랜덤 환율 생성
            double variation = (random.nextDouble() - 0.5) * 0.1; // -5% ~ +5%
            BigDecimal dailyRate = baseRate.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(variation)));
            
            saveRate(date, dailyRate);
            savedCount++;
        }
        
        log.info("[환율생성] 과거 환율 데이터 생성 완료: {}건", savedCount);
        return savedCount;
    }
}