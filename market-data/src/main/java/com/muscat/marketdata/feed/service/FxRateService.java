package com.muscat.marketdata.feed.service;

import com.muscat.marketdata.common.util.FxRateCalculator;
import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.feed.collector.FxDataCollector;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FxRateService {

    private final FxDataCollector dataCollector;
    private final FxRateCalculator calculator;
    private final MarketDataProperties properties;

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public FxRate saveRate(LocalDate date, BigDecimal usdToKrw) {
        Objects.requireNonNull(date, "날짜는 필수입니다");
        Objects.requireNonNull(usdToKrw, "환율은 필수입니다");
        
        // MoneyUtils를 사용한 환율 정규화 (소수점 6자리)
        BigDecimal normalizedRate = MoneyUtils.roundExchangeRate(usdToKrw);
        
        // 환율 유효성 검증
        MoneyUtils.validateExchangeRate(normalizedRate);

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
        Optional<FxRate> collectedRate = dataCollector.collectSingleDate(targetDate, useBusinessDayFallback);
        if (collectedRate.isPresent()) {
            return saveRate(targetDate, collectedRate.get().getRate());
        }
        throw new IllegalStateException("환율 데이터를 찾을 수 없습니다: " + targetDate);
    }

    @Transactional
    public List<FxRate> syncDateRange(LocalDate startDate, LocalDate endDate) {
        Objects.requireNonNull(startDate, "시작일은 필수입니다");
        Objects.requireNonNull(endDate, "종료일은 필수입니다");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠릅니다");
        }

        List<FxRate> collectedRates = dataCollector.collectDateRange(startDate, endDate);
        List<FxRate> savedRates = new ArrayList<>();
        for (FxRate rate : collectedRates) {
            savedRates.add(saveRate(rate.getDate(), rate.getRate()));
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
        Optional<FxRate> todayRate = dataCollector.collectSingleDate(LocalDate.now(), true);
        return todayRate.map(rate -> saveRate(LocalDate.now(), rate.getRate()));
    }

    public boolean isDataSourceHealthy() {
        try {
            dataCollector.collectSingleDate(LocalDate.now().minusDays(1), false);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public BigDecimal calculateInverseRate(BigDecimal usdToKrw) {
        return calculator.calculateInverseRate(usdToKrw);
    }
    
    @Transactional
    public int generateHistoricalRates(LocalDate startDate, LocalDate endDate, BigDecimal baseRate) {
        List<FxRate> generatedRates = calculator.generateHistoricalRates(startDate, endDate, baseRate);
        
        int savedCount = 0;
        for (FxRate rate : generatedRates) {
            // 기존 데이터가 있으면 건너뛰기
            if (findByDate(rate.getDate()) == null) {
                saveRate(rate.getDate(), rate.getRate());
                savedCount++;
            }
        }
        
        log.info("[환율생성] 과거 환율 데이터 생성 완료: {}건", savedCount);
        return savedCount;
    }
}