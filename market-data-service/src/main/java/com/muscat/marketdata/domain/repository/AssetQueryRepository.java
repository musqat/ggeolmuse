package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.QAsset;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AssetQueryRepository {

  private final JPAQueryFactory queryFactory;
  private static final QAsset asset = QAsset.asset;

  // 전체 심볼 목록 조회
  public List<String> findAllSymbols() {
    return queryFactory
        .select(asset.symbol)
        .from(asset)
        .orderBy(asset.symbol.asc())
        .fetch();
  }

  // 동적 필터링 - 여러 조건 조합 가능
  public List<Asset> findWithDynamicFilters(String country, String currency, String assetType) {
    return queryFactory
        .selectFrom(asset)
        .where(
            eqCountry(country),
            eqCurrency(currency),
            eqAssetType(assetType)
        )
        .orderBy(asset.symbol.asc())
        .fetch();
  }

  // 국가별 자산 조회
  public List<Asset> findByCountry(String country) {
    return queryFactory
        .selectFrom(asset)
        .where(asset.country.eq(country))
        .orderBy(asset.symbol.asc())
        .fetch();
  }

  // 통화별 자산 조회
  public List<Asset> findByCurrency(String currency) {
    return queryFactory
        .selectFrom(asset)
        .where(asset.currency.eq(currency))
        .orderBy(asset.symbol.asc())
        .fetch();
  }

  // 자산 유형별 조회
  public List<Asset> findByAssetType(String assetType) {
    return queryFactory
        .selectFrom(asset)
        .where(asset.assetType.eq(assetType))
        .orderBy(asset.symbol.asc())
        .fetch();
  }

  // 국가+통화 조합 조회
  public List<Asset> findByCountryAndCurrency(String country, String currency) {
    return queryFactory
        .selectFrom(asset)
        .where(
            asset.country.eq(country),
            asset.currency.eq(currency)
        )
        .orderBy(asset.symbol.asc())
        .fetch();
  }

  // 심볼 존재 여부 확인
  public boolean existsBySymbol(String symbol) {
    return queryFactory
        .selectOne()
        .from(asset)
        .where(asset.symbol.eq(symbol))
        .fetchFirst() != null;
  }

  // 동적 조건 메서드들
  private BooleanExpression eqCountry(String country) {
    return country != null ? asset.country.eq(country) : null;
  }

  private BooleanExpression eqCurrency(String currency) {
    return currency != null ? asset.currency.eq(currency) : null;
  }

  private BooleanExpression eqAssetType(String assetType) {
    return assetType != null ? asset.assetType.eq(assetType) : null;
  }
}