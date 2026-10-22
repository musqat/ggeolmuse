package com.muscat.marketdata.domain.service.impl;

import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import com.muscat.marketdata.domain.service.FxRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
    public List<FxRate> findByDates(List<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) {
            return List.of();
        }
        return fxRateRepository.findByDateIn(dates);
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
