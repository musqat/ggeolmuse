package com.muscat.marketdata.datasource.yf.service;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import com.muscat.marketdata.domain.service.FxRateService;
import com.muscat.marketdata.datasource.yf.collector.FxDataCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Yahoo Finance (KoreaExim) 환율 서비스 구현체
 */
@Slf4j
@Service
@ConditionalOnProperty(
    name = "marketdata.provider",
    havingValue = "yahoo"
)
@RequiredArgsConstructor
public class YahooFxRateService implements FxRateService {

    private final FxRateRepository fxRateRepository;
    private final FxDataCollector dataCollector;
    private final MarketDataProperties properties;

    @Override
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

    /**
     * Spring 캐시는 Optional 을 벗겨서 저장한다. 환율이 없으면 Optional.empty()
     * 가 null 이 되고 캐시가 null 을 거부하면서 IllegalArgumentException 이 난다.
     * 컨트롤러의 404 분기까지 가지도 못하고 500 이 나간다.
     *
     * unless 의 #result 도 벗겨진 뒤라 FxRate 다. Optional 인 줄 알고
     * isEmpty() 를 부르면 SpEL 이 터진다. null 검사만 한다.
     */
    @Override
    @Cacheable(cacheNames = "latestFxRate", key = "'latest'", unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<FxRate> getLatestRate() {
        Optional<FxRate> latest = fxRateRepository.findLatestRate();

        if (latest.isPresent()) {
            log.debug("[YF-환율조회] DB에서 최신 환율 조회 성공: {}", latest.get().getRate());
            return latest;
        }

        log.warn("[YF-환율조회] DB에 환율 데이터가 없습니다. 외부 API 호출은 스케줄러에서 수행됩니다.");
        return Optional.empty();
    }

}
