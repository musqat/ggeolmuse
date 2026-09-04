package com.muscat.marketdata.domain.repository.impl;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.QAsset;
import com.muscat.marketdata.domain.repository.AssetRepositoryCustom;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AssetRepositoryCustom 구현체
 */
@Repository
@RequiredArgsConstructor
public class AssetRepositoryCustomImpl implements AssetRepositoryCustom {

    private final JPAQueryFactory queryFactory;
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
}
