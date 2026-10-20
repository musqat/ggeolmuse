package com.muscat.marketdata.domain.repository.impl;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.QAsset;
import com.muscat.marketdata.domain.repository.AssetRepositoryCustom;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AssetRepositoryCustom 구현체
 */
@Repository
@RequiredArgsConstructor
public class AssetRepositoryCustomImpl implements AssetRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    private static final QAsset asset = QAsset.asset;

    @Override
    public List<String> findActiveSymbols() {
        return queryFactory
            .select(asset.symbol)
            .from(asset)
            .where(asset.active.isTrue())
            .orderBy(asset.symbol.asc())
            .fetch();
    }

    @Override
    public List<Asset> searchByKeyword(String keyword, int limit) {
        String upperKeyword = keyword.toUpperCase();

        return queryFactory
            .selectFrom(asset)
            .where(
                asset.symbol.upper().contains(upperKeyword)
                    .or(asset.name.upper().contains(upperKeyword))
            )
            .orderBy(asset.symbol.asc())
            .limit(limit)
            .fetch();
    }

    @Override
    public Page<Asset> findActiveSortedByMarketCap(Pageable pageable, boolean ascending, String assetType) {
        // NULL은 항상 마지막, marketCap 정렬, symbol 부정렬
        OrderSpecifier<?>[] orders = ascending
            ? new OrderSpecifier[]{asset.marketCap.asc().nullsLast(), asset.symbol.asc()}
            : new OrderSpecifier[]{asset.marketCap.desc().nullsLast(), asset.symbol.asc()};

        // assetType 필터 조건 생성
        var whereClause = asset.active.isTrue();
        if (assetType != null && !"ALL".equalsIgnoreCase(assetType)) {
            whereClause = whereClause.and(asset.assetType.eq(assetType.toUpperCase()));
        }

        // 페이징 조회
        List<Asset> content = queryFactory
            .selectFrom(asset)
            .where(whereClause)
            .orderBy(orders)
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        // 전체 개수 조회
        Long total = queryFactory
            .select(asset.count())
            .from(asset)
            .where(whereClause)
            .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    // 종목별 from 이후 배당 합
    private static final String YEARLY_DIVIDENDS =
        " LEFT JOIN (SELECT symbol, SUM(amount) AS yearly FROM dividend WHERE ex_date >= :from GROUP BY symbol) d ON d.symbol = a.symbol";

    // 상장 중이고 유형이 맞는 종목. ALL 이면 유형 무시
    private static final String ACTIVE_OF_TYPE = " WHERE a.active = true AND (:assetType = 'ALL' OR a.asset_type = :assetType)";

    @Override
    @SuppressWarnings("unchecked")
    public Page<Asset> findActiveSortedByDividendYield(Pageable pageable, boolean ascending, String assetType, LocalDate from) {
        String type = assetType == null || "ALL".equalsIgnoreCase(assetType) ? "ALL" : assetType.toUpperCase();
        // 수익률을 못 구하는 종목은 뒤로. 종가 0 은 NULLIF 로 나누지 않음
        String order = " ORDER BY CASE WHEN d.yearly IS NULL OR a.latest_close IS NULL OR a.latest_close <= 0 THEN 1 ELSE 0 END,"
            + " d.yearly / NULLIF(a.latest_close, 0) " + (ascending ? "ASC" : "DESC") + ", a.symbol ASC";

        List<Asset> content = entityManager.createNativeQuery("SELECT a.* FROM asset a" + YEARLY_DIVIDENDS + ACTIVE_OF_TYPE + order, Asset.class)
            .setParameter("from", from)
            .setParameter("assetType", type)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .getResultList();
        Number total = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM asset a" + ACTIVE_OF_TYPE)
            .setParameter("assetType", type)
            .getSingleResult();
        return new PageImpl<>(content, pageable, total.longValue());
    }

  // 파생 쿼리의 nullsLast 는 Criteria 가 처리하지 못해 QueryDSL 로 SQL 을 직접 만듦
  @Override
  public Page<Asset> findSorted(Pageable pageable, boolean active) {
    List<OrderSpecifier<?>> orders = new ArrayList<>();
    for (Sort.Order order : pageable.getSort()) {
      ComparableExpressionBase<?> path = sortPath(order.getProperty());
      if (path == null) {
        continue;
      }
      orders.add(order.isAscending() ? path.asc().nullsLast() : path.desc().nullsLast());
    }
    orders.add(asset.symbol.asc());

    List<Asset> content = queryFactory
      .selectFrom(asset)
      .where(asset.active.eq(active))
      .orderBy(orders.toArray(new OrderSpecifier[0]))
      .offset(pageable.getOffset())
      .limit(pageable.getPageSize())
      .fetch();

    Long total = queryFactory
      .select(asset.count())
      .from(asset)
      .where(asset.active.eq(active))
      .fetchOne();

    return new PageImpl<>(content, pageable, total != null ? total : 0L);
  }

  // 정렬 가능한 필드만 받는다. 모르는 이름은 무시한다
  private static ComparableExpressionBase<?> sortPath(String property) {
    return switch (property) {
      case "symbol" -> asset.symbol;
      case "name" -> asset.name;
      case "country" -> asset.country;
      case "currency" -> asset.currency;
      case "assetType" -> asset.assetType;
      case "marketCap" -> asset.marketCap;
      case "latestClose" -> asset.latestClose;
      case "latestDate" -> asset.latestDate;
      default -> null;
    };
  }
}
