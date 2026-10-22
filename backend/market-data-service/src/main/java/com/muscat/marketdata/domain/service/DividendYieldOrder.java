package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.domain.model.DividendYieldRule;
import com.muscat.marketdata.domain.repository.AssetRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

// 배당수익률 정렬 순서를 캐시해 두는 서비스
@Service
@RequiredArgsConstructor
public class DividendYieldOrder {

  private final AssetRepository assetRepository;

  // 유형 · 방향 · 기준일마다 상장 종목 전체의 티커 순서. 불변 목록은 Redis 값에서 다시 읽지 못해 ArrayList 로
  @Cacheable(cacheNames = "dividendYieldOrder", key = "#assetType + ':' + #ascending + ':' + #rule.from()")
  public List<String> symbols(String assetType, boolean ascending, DividendYieldRule rule) {
    return new ArrayList<>(assetRepository.findActiveSymbolsByDividendYield(ascending, assetType, rule));
  }
}
