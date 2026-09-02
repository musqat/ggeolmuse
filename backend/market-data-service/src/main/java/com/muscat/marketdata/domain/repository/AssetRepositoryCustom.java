package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Asset;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AssetRepositoryCustom {

  // 키워드로 심볼 또는 회사명 검색 (DB에서 필터링)
  List<Asset> searchByKeyword(String keyword, int limit);

  // 활성 상태인 종목을 시가총액 기준 정렬하여 페이징 조회
  // ascending: true면 오름차순, false면 내림차순 / assetType: null이면 전체
  Page<Asset> findActiveSortedByMarketCap(Pageable pageable, boolean ascending, String assetType);
}
