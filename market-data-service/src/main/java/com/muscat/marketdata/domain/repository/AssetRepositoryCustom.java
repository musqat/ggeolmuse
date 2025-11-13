package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Asset;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Asset Repository Custom Interface
 */
public interface AssetRepositoryCustom {

  /**
   * 키워드로 심볼 또는 회사명 검색 (DB에서 필터링)
   */
  List<Asset> searchByKeyword(String keyword, int limit);

  /**
   * 활성 상태인 종목을 시가총액 기준 정렬하여 페이징 조회
   *
   * @param pageable  페이지 정보
   * @param ascending true: 오름차순, false: 내림차순
   * @param assetType 자산 유형 필터 (null이면 전체, "EQUITY", "ETF")
   * @return 정렬된 Asset 페이지 (NULL은 항상 마지막)
   */
  Page<Asset> findActiveSortedByMarketCap(Pageable pageable, boolean ascending, String assetType);
}
