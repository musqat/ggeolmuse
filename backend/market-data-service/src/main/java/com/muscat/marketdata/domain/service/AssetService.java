package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.domain.dto.AssetSummaryDto;
import com.muscat.marketdata.domain.entity.Asset;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 종목(Asset) CRUD · 조회 서비스
 */
public interface AssetService {

  // 등록 전 공급원에서 회사 정보를 미리 봄
  Optional<Asset> previewSymbol(String symbol);

  // 심볼 또는 회사명으로 찾기(최대 20개)
  List<Asset> searchSymbols(String keyword);

  // 이름 · 국가 · 통화 · 유형이 비면 공급원에서 채움
  Asset createAsset(String symbol, String name, String country,
    String currency, String assetType,
    boolean collectData, LocalDate fromDate,
    LocalDate toDate, boolean includeDividends);

  Optional<Asset> getAsset(String symbol);

  List<Asset> getAllAssets();

  List<String> getActiveSymbols();

  // 가격 · 최신 데이터 날짜를 붙인 요약(active 가 false 면 상장폐지 종목)
  Page<AssetSummaryDto> getAllAssetSummaries(Pageable pageable, boolean active);

  void updateAssetPrice(String symbol);

  void updateAssetMarketCap(String symbol);

  // 목록 API 한 번으로 전체 시가총액 갱신(갱신한 종목 수 반환)
  int updateAllMarketCaps();

  // 잘못 수집된 회사명을 관리자가 고침
  Asset updateAssetName(String symbol, String name);

  // active 를 끄고 delistedDate 를 기록(soft delete)
  void deleteAsset(String symbol);

  // 여러 종목을 soft delete. 실제로 끈 종목 수를 돌려줌
  int deleteAssets(java.util.List<String> symbols);

  // active 를 켜고 delistedDate 를 지움
  void restoreAsset(String symbol);
}
