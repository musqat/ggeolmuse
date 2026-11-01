package com.muscat.marketdata.domain.repository.impl;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.QAsset;
import com.muscat.marketdata.domain.entity.QDividend;
import com.muscat.marketdata.domain.model.DividendYieldRule;
import com.muscat.marketdata.domain.repository.AssetRepositoryCustom;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * AssetRepositoryCustom 구현체
 */
@Repository
@RequiredArgsConstructor
public class AssetRepositoryCustomImpl implements AssetRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private static final QAsset asset = QAsset.asset;
    private static final QDividend dividend = QDividend.dividend;

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
    public long countWithCandles() {
        Long count = queryFactory
            .select(asset.count())
            .from(asset)
            .where(asset.latestDate.isNotNull())
            .fetchOne();
        return count != null ? count : 0L;
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

        BooleanExpression whereClause = activeOfType(assetType);

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

    @Override
    public List<String> findActiveSymbolsByDividendYield(boolean ascending, String assetType, DividendYieldRule rule) {
        NumberExpression<BigDecimal> yearly = dividend.amount.sum();
        // 수익률을 못 구하거나(배당 없음 · 종가 없음 · 0 이하) 상한을 넘는 종목
        BooleanExpression noYield = yearly.isNull()
            .or(asset.latestClose.isNull())
            .or(asset.latestClose.loe(BigDecimal.ZERO))
            .or(yearly.gt(asset.latestClose.multiply(rule.maxRatio())));
        NumberExpression<Integer> tail = new CaseBuilder().when(noYield).then(1).otherwise(0);
        // 뒤로 보낸 종목은 둘째 기준을 0 으로 둬 종목 코드순
        NumberExpression<BigDecimal> yield = new CaseBuilder().when(noYield).then(BigDecimal.ZERO)
            .otherwise(yearly.divide(asset.latestClose));

        return queryFactory
            .select(asset.symbol)
            .from(asset)
            .leftJoin(dividend).on(dividend.symbol.eq(asset.symbol),
                dividend.exDate.goe(rule.from()),
                dividend.amount.loe(asset.latestClose.multiply(rule.specialRatio())))
            .where(activeOfType(assetType))
            .groupBy(asset.symbol, asset.latestClose)
            .orderBy(tail.asc(), ascending ? yield.asc() : yield.desc(), asset.symbol.asc())
            .fetch();
    }

    // 상장 중이고 유형이 맞는 종목(비었거나 ALL 이면 유형 무시)
    private static BooleanExpression activeOfType(String assetType) {
        BooleanExpression active = asset.active.isTrue();
        if (assetType == null || "ALL".equalsIgnoreCase(assetType)) {
            return active;
        }
        return active.and(asset.assetType.eq(assetType.toUpperCase()));
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

  // 정렬 가능한 필드만(모르는 이름은 무시)
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
