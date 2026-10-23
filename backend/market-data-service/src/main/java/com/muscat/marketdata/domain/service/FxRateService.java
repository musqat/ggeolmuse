package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.domain.entity.FxRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 환율 서비스 인터페이스
 */
public interface FxRateService {

  // 없으면 null
  FxRate findByDate(LocalDate date);

  // 날짜마다 그날 이전 가장 가까운 영업일 환율, 그 전 환율이 아예 없는 날은 빠짐
  Map<LocalDate, BigDecimal> findByDates(List<LocalDate> dates);

  // 없으면 API 에서 받아옴
  Optional<FxRate> getLatestRate();

}
