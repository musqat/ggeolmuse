package com.muscat.trade.domain.service;

import com.muscat.trade.domain.dto.response.DividendResponseDto;
import java.time.LocalDate;
import java.util.List;

public interface DividendService {

  // 사용자의 배당 내역 조회 (캐싱 포함)
  // Holdings 기반으로 배당을 조회하고, 없으면 market-data에서 가져와서 캐싱
  List<DividendResponseDto> getUserDividends(String userId);

  // 특정 종목의 배당 내역 조회 및 캐싱
  List<DividendResponseDto> getDividendsWithCache(String userId, String symbol,
      LocalDate startDate, LocalDate endDate);

  // 배당 캐시 강제 갱신
  void refreshDividendCache(String userId);
}
