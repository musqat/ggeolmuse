package com.muscat.marketdata.datasource.alphavantage.service;

import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import com.muscat.marketdata.domain.service.FxRateService;
import com.muscat.marketdata.datasource.alphavantage.collector.FxDataCollector;
import com.muscat.marketdata.infra.kafka.FxRateEventProducer;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AlphaVantage 기반 환율 서비스 구현체
 */
@Slf4j
@Service
@ConditionalOnProperty(
    name = "marketdata.provider",
    havingValue = "alphavantage"
)
@RequiredArgsConstructor
public class AlphaVantageFxRateService implements FxRateService {

  private final FxRateRepository fxRateRepository;
  private final FxDataCollector dataCollector;
  private final MarketDataProperties properties;
  private final FxRateEventProducer fxRateEventProducer;

  @Override
  @Transactional
  @CacheEvict(cacheNames = {"fxRate"}, key = "#date", beforeInvocation = false)
  public FxRate saveRate(LocalDate date, BigDecimal usdToKrw) {
    Objects.requireNonNull(date, "날짜는 필수입니다");
    Objects.requireNonNull(usdToKrw, "환율은 필수입니다");

    // MoneyUtils를 사용한 환율 정규화 (소수점 6자리)
    BigDecimal normalizedRate = MoneyUtils.roundExchangeRate(usdToKrw);

    // 환율 유효성 검증
    MoneyUtils.validateExchangeRate(normalizedRate);

    Optional<FxRate> existing = fxRateRepository.findByDate(date);
    BigDecimal previousRate = null;

    if (existing.isEmpty()) {
      FxRate newRate = FxRate.builder().date(date).rate(normalizedRate).build();
      FxRate saved = fxRateRepository.save(newRate);
      log.debug("[환율저장] {} -> {}", date, normalizedRate);

      // Kafka 이벤트 발행 (신규 환율)
      fxRateEventProducer.publishFxRateUpdated(saved, null);

      return saved;
    } else {
      FxRate existingRate = existing.get();
      if (existingRate.getRate() == null || existingRate.getRate().compareTo(normalizedRate) != 0) {
        previousRate = existingRate.getRate(); // 이전 환율 저장
        FxRate updatedRate = FxRate.builder().date(date).rate(normalizedRate).build();
        FxRate saved = fxRateRepository.save(updatedRate);
        log.debug("[환율업데이트] {} -> {} (이전: {})", date, normalizedRate, previousRate);

        // Kafka 이벤트 발행 (환율 변경)
        fxRateEventProducer.publishFxRateUpdated(saved, previousRate);

        return saved;
      }
      return existingRate;
    }
  }

  @Override
  @Cacheable(cacheNames = "fxRate", key = "#date")
  @Transactional(readOnly = true)
  public FxRate findByDate(LocalDate date) {
    return fxRateRepository.findByDate(date).orElse(null);
  }

  @Override
  @Transactional(readOnly = true)
  public List<FxRate> findByDates(List<LocalDate> dates) {
    if (dates == null || dates.isEmpty()) {
      return List.of();
    }
    return fxRateRepository.findByDateIn(dates);
  }

  @Override
  @Transactional
  public Optional<FxRate> getLatestRate() {
    Optional<FxRate> latest = fxRateRepository.findLatestRate();

    if (latest.isPresent()) {
      return latest;
    }

    log.info("[환율조회] DB가 비어있어서 오늘 환율을 API에서 가져옵니다");
    LocalDate today = LocalDate.now();
    Optional<FxRate> todayRate = dataCollector.collectSingleDate(today, true);
    return todayRate.map(rate -> saveRate(today, rate.getRate()));
  }

  @Override
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

}
