package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.domain.entity.FxRate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 환율 서비스 인터페이스
 */
public interface FxRateService {

  // 없으면 null
  FxRate findByDate(LocalDate date);

  List<FxRate> findByDates(List<LocalDate> dates);

  // 없으면 API 에서 받아옴
  Optional<FxRate> getLatestRate();

}
