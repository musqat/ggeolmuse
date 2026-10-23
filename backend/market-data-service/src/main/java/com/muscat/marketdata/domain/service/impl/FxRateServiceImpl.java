package com.muscat.marketdata.domain.service.impl;

import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import com.muscat.marketdata.domain.service.FxRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

// DB 에 쌓인 환율만 읽는 환율 조회 서비스
@Slf4j
@Service
@RequiredArgsConstructor
public class FxRateServiceImpl implements FxRateService {

    private final FxRateRepository fxRateRepository;

    @Override
    @Transactional(readOnly = true)
    public FxRate findByDate(LocalDate date) {
        // 정확한 날짜가 없으면 가장 가까운 이전 영업일 환율 조회 (주말/공휴일 대비)
        return fxRateRepository.findFirstByDateLessThanEqualOrderByDateDesc(date).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<LocalDate, BigDecimal> findByDates(List<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) {
            return Map.of();
        }
        LocalDate from = Collections.min(dates);
        LocalDate to = Collections.max(dates);

        // 첫 날 이전 가장 가까운 값과 기간 안 값에서 날짜마다 그날 이하 가장 늦은 환율
        TreeMap<LocalDate, BigDecimal> known = new TreeMap<>();
        fxRateRepository.findFirstByDateLessThanEqualOrderByDateDesc(from)
            .ifPresent(rate -> known.put(rate.getDate(), rate.getRate()));
        fxRateRepository.findByDateBetween(from, to)
            .forEach(rate -> known.put(rate.getDate(), rate.getRate()));

        Map<LocalDate, BigDecimal> result = new HashMap<>();
        for (LocalDate date : dates) {
            Map.Entry<LocalDate, BigDecimal> found = known.floorEntry(date);
            if (found != null) {
                result.put(date, found.getValue());
            }
        }
        return result;
    }

    // 캐시는 Optional 을 벗겨 저장해 빈 값이 null 이 되고 거부돼 500 이 남. 그래서 null 이면 캐시하지 않음
    // unless 의 #result 도 벗겨진 FxRate 라 isEmpty() 대신 null 검사만
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
