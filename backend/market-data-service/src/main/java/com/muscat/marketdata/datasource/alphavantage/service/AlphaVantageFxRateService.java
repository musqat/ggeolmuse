package com.muscat.marketdata.datasource.alphavantage.service;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import com.muscat.marketdata.domain.service.FxRateService;
import com.muscat.marketdata.datasource.alphavantage.collector.FxDataCollector;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

  @Override
  @Cacheable(cacheNames = "fxRate", key = "#date")
  @Transactional(readOnly = true)
  public FxRate findByDate(LocalDate date) {
    // 정확한 날짜가 없으면 가장 가까운 이전 영업일 환율 조회 (주말/공휴일 대비)
    return fxRateRepository.findFirstByDateLessThanEqualOrderByDateDesc(date).orElse(null);
  }

  @Override
  @Transactional(readOnly = true)
  public List<FxRate> findByDates(List<LocalDate> dates) {
    if (dates == null || dates.isEmpty()) {
      return List.of();
    }
    return fxRateRepository.findByDateIn(dates);
  }

  // Optional.empty() 는 캐시에 null 로 들어가 거부당한다. YahooFxRateService 와 같은 이유다.
  @Override
  @Cacheable(cacheNames = "latestFxRate", key = "'latest'", unless = "#result == null")
  @Transactional(readOnly = true)
  public Optional<FxRate> getLatestRate() {
    Optional<FxRate> latest = fxRateRepository.findLatestRate();

    if (latest.isPresent()) {
      log.debug("[환율조회] DB에서 최신 환율 조회 성공: {}", latest.get().getRate());
      return latest;
    }

    log.warn("[환율조회] DB에 환율 데이터가 없습니다. 외부 API 호출은 스케줄러에서 수행됩니다.");
    return Optional.empty();
  }

}
