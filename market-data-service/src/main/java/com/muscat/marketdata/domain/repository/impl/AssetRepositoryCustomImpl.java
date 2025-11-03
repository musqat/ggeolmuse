package com.muscat.marketdata.domain.repository.impl;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.QAsset;
import com.muscat.marketdata.domain.repository.AssetRepositoryCustom;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
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
}
