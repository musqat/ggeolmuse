package com.muscat.marketdata.feed.service;

import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.provider.MarketDataProvider.FxSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환율 서비스 - 달러/원 환율 관리
 *
 * 데이터 소스:
 * - 한국수출입은행 API
 *
 * 주요 기능:
 * - 환율 저장/조회: 날짜별 USD/KRW 환율 관리
 * - 실시간 동기화: 외부 API에서 최신 환율 가져오기
 * - 영업일 폴백: 주말/공휴일 데이터 없으면 이전 영업일 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxRateService {

  private final FxSource fxSource; // 환율 데이터 소스

  @PersistenceContext
  private EntityManager em;

  private static final int SCALE = 6; // 소수점 6자리까지
  private static final RoundingMode RM = RoundingMode.HALF_UP;
  private static final int MAX_FALLBACK_DAYS = 7; // 최대 7일 전까지 폴백

  /** 환율 저장 (날짜별 단건 저장/업데이트) */
  @Transactional
  public FxRate saveRate(LocalDate date, BigDecimal usdToKrw) {
    Objects.requireNonNull(date, "날짜는 필수입니다");
    Objects.requireNonNull(usdToKrw, "환율은 필수입니다");
    BigDecimal normalizedRate = normalize(usdToKrw);

    FxRate existing = em.find(FxRate.class, date);
    if (existing == null) {
      // 신규 저장
      FxRate newRate = FxRate.builder().date(date).rate(normalizedRate).build();
      em.persist(newRate);
      log.debug("[환율저장] {} -> {}", date, normalizedRate);
      return newRate;
    } else {
      // 기존 데이터 업데이트
      if (existing.getRate() == null || existing.getRate().compareTo(normalizedRate) != 0) {
        em.detach(existing);
        FxRate updatedRate = FxRate.builder().date(date).rate(normalizedRate).build();
        FxRate saved = em.merge(updatedRate);
        log.debug("[환율업데이트] {} -> {}", date, normalizedRate);
        return saved;
      }
      // 변경사항 없음
      return existing;
    }
  }

  /** 특정일 환율 조회 (없으면 null) */
  @Transactional(readOnly = true)
  public FxRate findByDate(LocalDate date) {
    return em.find(FxRate.class, date);
  }

  /** 기간별 환율 조회 (시작일~종료일 포함) */
  @Transactional(readOnly = true)
  public List<FxRate> findRateRange(LocalDate startDate, LocalDate endDate) {
    return em.createQuery("select f from FxRate f where f.date between :start and :end order by f.date", FxRate.class)
        .setParameter("start", startDate)
        .setParameter("end", endDate)
        .getResultList();
  }

  /**
   * 특정일 환율 동기화 (외부 API에서 가져와서 저장)
   */
  @Transactional
  public FxRate syncSingleDate(LocalDate targetDate, boolean useBusinessDayFallback) {
    Objects.requireNonNull(targetDate, "대상 날짜는 필수입니다");

    LocalDate currentDate = targetDate;
    int attempts = 0;

    while (true) {
      // 외부 API에서 환율 조회
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

  /**
   * 기간별 환율 동기화 (외부 API에서 가져와서 저장)
   */
  @Transactional
  public List<FxRate> syncDateRange(LocalDate startDate, LocalDate endDate) {
    Objects.requireNonNull(startDate, "시작일은 필수입니다");
    Objects.requireNonNull(endDate, "종료일은 필수입니다");
    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("종료일이 시작일보다 빠릅니다");
    }

    List<FxRate> savedRates = new ArrayList<>();
    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      // 외부 API에서 환율 조회
      Optional<BigDecimal> rateData = fxSource.fetchUsdKrw(date);
      if (rateData.isPresent()) {
        savedRates.add(saveRate(date, rateData.get()));
      }
    }
    return savedRates;
  }

  /**
   * 최신 환율 조회 (DB 우선, 없으면 API에서 가져오기)
   */
  @Transactional
  public Optional<FxRate> getLatestRate() {
    List<FxRate> latest = em.createQuery("select f from FxRate f order by f.date desc", FxRate.class)
        .setMaxResults(1)
        .getResultList();

    if (!latest.isEmpty()) {
      return Optional.of(latest.get(0));
    }

    // DB가 비어있으면 오늘 환율을 API에서 가져와서 저장
    log.info("[환율조회] DB가 비어있어서 오늘 환율을 API에서 가져옵니다");
    return fxSource.fetchUsdKrw(LocalDate.now())
        .map(rate -> saveRate(LocalDate.now(), rate));
  }

  /**
   * 환율 데이터 소스 상태 확인
   */
  public boolean isDataSourceHealthy() {
    try {
      fxSource.fetchUsdKrw(LocalDate.now().minusDays(1));
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /** 역방향 환율 계산: 원→달러 = 1 / (달러→원) */
  public static BigDecimal calculateInverseRate(BigDecimal usdToKrw) {
    Objects.requireNonNull(usdToKrw, "환율은 필수입니다");
    return BigDecimal.ONE.divide(usdToKrw, SCALE, RM);
  }

  // ===== 내부 유틸리티 메서드 =====

  private static BigDecimal normalize(BigDecimal value) {
    return value.setScale(SCALE, RM);
  }

  private static LocalDate getPreviousBusinessDay(LocalDate date) {
    DayOfWeek dayOfWeek = date.getDayOfWeek();
    if (dayOfWeek == DayOfWeek.MONDAY) return date.minusDays(3); // 월요일 → 금요일
    if (dayOfWeek == DayOfWeek.SUNDAY) return date.minusDays(2); // 일요일 → 금요일
    return date.minusDays(1);                                    // 나머지 → 전일
  }
}