package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.domain.entity.FxRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 환율 서비스 인터페이스
 */
public interface FxRateService {

  /**
   * 환율 저장 또는 업데이트
   *
   * @param date      환율 날짜
   * @param usdToKrw  USD->KRW 환율
   * @return 저장된 환율 엔티티
   */
  FxRate saveRate(LocalDate date, BigDecimal usdToKrw);

  /**
   * 특정 날짜의 환율 조회
   *
   * @param date 조회할 날짜
   * @return 환율 엔티티 (없으면 null)
   */
  FxRate findByDate(LocalDate date);

  /**
   * 여러 날짜의 환율 조회
   *
   * @param dates 조회할 날짜 목록
   * @return 환율 엔티티 목록
   */
  List<FxRate> findByDates(List<LocalDate> dates);

  /**
   * 가장 최근 환율 조회
   *
   * @return 최근 환율 (없으면 API에서 가져옴)
   */
  Optional<FxRate> getLatestRate();

  /**
   * 과거 환율 데이터 생성 (테스트용)
   *
   * @param startDate 시작 날짜
   * @param endDate   종료 날짜
   * @param baseRate  기준 환율
   * @return 생성된 환율 데이터 개수
   */
  int generateHistoricalRates(LocalDate startDate, LocalDate endDate, BigDecimal baseRate);
}
