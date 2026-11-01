package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.model.DividendYieldRule;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AssetRepositoryCustom {

  // 키워드로 심볼 또는 회사명 검색 (DB에서 필터링)
  List<Asset> searchByKeyword(String keyword, int limit);

  // 활성 상태인 종목을 시가총액 기준 정렬하여 페이징 조회
  // ascending: true면 오름차순, false면 내림차순 / assetType: null이면 전체
  Page<Asset> findActiveSortedByMarketCap(Pageable pageable, boolean ascending, String assetType);

  // 활성 종목 전체의 티커를 배당수익률(특별 배당을 뺀 배당 합 ÷ 최신 종가) 순으로(못 구하거나 상한을 넘는 종목은 맨 뒤)
  List<String> findActiveSymbolsByDividendYield(boolean ascending, String assetType, DividendYieldRule rule);

  // 상장 중인 종목의 티커만 조회(검색용)
  List<String> findActiveSymbols();

  // 최신 캔들 날짜가 채워진 종목 수(캔들을 저장할 때 같이 채움)
  long countWithCandles();

  // 상장 여부로 걸러 임의 정렬로 페이징 조회(NULL 은 항상 마지막)
  Page<Asset> findSorted(Pageable pageable, boolean active);
}
