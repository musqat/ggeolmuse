package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.domain.dto.AssetSummaryDto;
import com.muscat.marketdata.domain.entity.Asset;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Asset(자산/종목) 관리 서비스 종목 CRUD 및 조회 기능을 제공합니다.
 */
public interface AssetService {

  /**
   * 심볼로 회사 정보 미리보기 (Alpha Vantage에서 조회)
   *
   * @param symbol 종목 심볼
   * @return 회사 정보 (존재하지 않으면 empty)
   */
  Optional<Asset> previewSymbol(String symbol);

  /**
   * 키워드로 심볼 검색
   *
   * @param keyword 검색 키워드 (심볼 또는 회사명)
   * @return 검색 결과 (최대 20개)
   */
  List<Asset> searchSymbols(String keyword);

  /**
   * 새로운 종목 추가
   *
   * @param symbol           종목 심볼
   * @param name             회사명 (null이면 자동 조회)
   * @param country          국가 (null이면 자동 조회)
   * @param currency         통화 (null이면 자동 조회)
   * @param assetType        자산 유형 (null이면 자동 조회)
   * @param collectData      데이터 수집 여부
   * @param fromDate         수집 시작일
   * @param toDate           수집 종료일
   * @param includeDividends 배당 포함 여부
   * @return 생성된 Asset 엔티티
   */
  Asset createAsset(String symbol, String name, String country,
    String currency, String assetType,
    boolean collectData, LocalDate fromDate,
    LocalDate toDate, boolean includeDividends);

  /**
   * 특정 종목 조회
   *
   * @param symbol 종목 심볼
   * @return Asset 엔티티 (존재하지 않으면 empty)
   */
  Optional<Asset> getAsset(String symbol);

  /**
   * 전체 종목 목록 조회
   *
   * @return 전체 Asset 리스트
   */
  List<Asset> getAllAssets();

  /**
   * 전체 종목 요약 정보 조회 (가격, 최신 데이터 날짜 포함, 페이지네이션)
   *
   * @param pageable 페이지 정보 (page, size, sort)
   */
  Page<AssetSummaryDto> getAllAssetSummaries(
    Pageable pageable);

  /**
   * 특정 종목의 가격 데이터 업데이트
   * AlphaVantage API를 호출하여 최신 캔들 데이터를 수집합니다.
   *
   * @param symbol 종목 심볼
   */
  void updateAssetPrice(String symbol);

  /**
   * 특정 종목의 시가총액 업데이트
   * AlphaVantage OVERVIEW API를 호출하여 최신 시가총액을 수집합니다.
   *
   * @param symbol 종목 심볼
   */
  void updateAssetMarketCap(String symbol);

  /**
   * 전체 활성 종목 시가총액 일괄 업데이트
   * 외부 소스(NASDAQ Screener)를 1회 조회하여 모든 종목을 갱신합니다.
   *
   * @return 업데이트된 종목 수
   */
  int updateAllMarketCaps();

  /**
   * 종목 삭제 (soft delete)
   * active를 false로 설정하고 delistedDate를 기록합니다.
   *
   * @param symbol 종목 심볼
   */
  void deleteAsset(String symbol);
}
