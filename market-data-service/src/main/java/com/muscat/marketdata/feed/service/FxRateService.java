package com.muscat.marketdata.feed.service;

import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import com.muscat.marketdata.domain.repository.FxRateQueryRepository;
import com.muscat.marketdata.feed.collector.FxDataCollector;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FxRateService {

  private final FxRateRepository fxRateRepository;
  private final FxRateQueryRepository fxRateQueryRepository;
  private final FxDataCollector dataCollector;
  private final MarketDataProperties properties;

  @Transactional
  public FxRate saveRate(LocalDate date, BigDecimal usdToKrw) {
    Objects.requireNonNull(date, "날짜는 필수입니다");
    Objects.requireNonNull(usdToKrw, "환율은 필수입니다");

    // MoneyUtils를 사용한 환율 정규화 (소수점 6자리)
    BigDecimal normalizedRate = MoneyUtils.roundExchangeRate(usdToKrw);

    // 환율 유효성 검증
    MoneyUtils.validateExchangeRate(normalizedRate);

    Optional<FxRate> existing = fxRateRepository.findByDate(date);
    if (existing.isEmpty()) {
      FxRate newRate = FxRate.builder().date(date).rate(normalizedRate).build();
      FxRate saved = fxRateRepository.save(newRate);
      log.debug("[환율저장] {} -> {}", date, normalizedRate);
      return saved;
    } else {
      FxRate existingRate = existing.get();
      if (existingRate.getRate() == null || existingRate.getRate().compareTo(normalizedRate) != 0) {
        FxRate updatedRate = FxRate.builder().date(date).rate(normalizedRate).build();
        FxRate saved = fxRateRepository.save(updatedRate);
        log.debug("[환율업데이트] {} -> {}", date, normalizedRate);
        return saved;
      }
      return existingRate;
    }
  }

  @Transactional(readOnly = true)
  public FxRate findByDate(LocalDate date) {
    return fxRateRepository.findByDate(date).orElse(null);
  }


  @Transactional
  public Optional<FxRate> getLatestRate() {
    Optional<FxRate> latest = fxRateQueryRepository.findLatestRate();

    if (latest.isPresent()) {
      return latest;
    }

    log.info("[환율조회] DB가 비어있어서 오늘 환율을 API에서 가져옵니다");
    LocalDate today = LocalDate.now();
    Optional<FxRate> todayRate = dataCollector.collectSingleDate(today, true);
    return todayRate.map(rate -> saveRate(today, rate.getRate()));
  }


  @Transactional
  public int generateHistoricalRates(LocalDate startDate, LocalDate endDate, BigDecimal baseRate) {
    log.info("과거 환율 생성 시작: {} ~ {}, 기준환율={}", startDate, endDate, baseRate);

    Random random = new Random();
    BigDecimal currentRate = baseRate;
    int savedCount = 0;

    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      if (isBusinessDay(date) && findByDate(date) == null) {
        currentRate = generateNextRate(random, currentRate);
        saveRate(date, currentRate);
        savedCount++;
      }
    }

    log.info("[환율생성] 과거 환율 데이터 생성 완료: {}건", savedCount);
    return savedCount;
  }

  // 영업일 체크 (주말 제외)
  private boolean isBusinessDay(LocalDate date) {
    DayOfWeek dayOfWeek = date.getDayOfWeek();
    return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
  }

  // 다음 환율 생성 (랜덤 변동 적용)
  private BigDecimal generateNextRate(Random random, BigDecimal currentRate) {
    double changePercent = random.nextGaussian() * 0.01; // 표준편차 1%
    BigDecimal multiplier = BigDecimal.ONE.add(BigDecimal.valueOf(changePercent));
    BigDecimal newRate = currentRate.multiply(multiplier);
    return MoneyUtils.roundExchangeRate(newRate);
  }

  // ========== DEPRECATED METHODS ==========

  @Deprecated(since = "2024.12", forRemoval = true)
  @Transactional(readOnly = true)
  public List<FxRate> findRateRange(LocalDate startDate, LocalDate endDate) {
    return fxRateQueryRepository.findByDateRange(startDate, endDate);
  }

  @Deprecated(since = "2024.12", forRemoval = true)
  @Transactional
  public FxRate syncSingleDate(LocalDate targetDate, boolean useBusinessDayFallback) {
    Optional<FxRate> collectedRate = dataCollector.collectSingleDate(targetDate,
        useBusinessDayFallback);
    if (collectedRate.isPresent()) {
      return saveRate(targetDate, collectedRate.get().getRate());
    }
    throw new IllegalStateException("환율 데이터를 찾을 수 없습니다: " + targetDate);
  }

  @Deprecated(since = "2024.12", forRemoval = true)
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

  @Deprecated(since = "2024.12", forRemoval = true)
  public boolean isDataSourceHealthy() {
    try {
      dataCollector.collectSingleDate(LocalDate.now().minusDays(1), false);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @Deprecated(since = "2024.12", forRemoval = true)
  public BigDecimal calculateInverseRate(BigDecimal usdToKrw) {
    if (usdToKrw == null) {
      throw new IllegalArgumentException("환율은 필수입니다");
    }
    return BigDecimal.ONE.divide(usdToKrw, properties.getFx().getScale(),
        java.math.RoundingMode.HALF_UP);
  }
}