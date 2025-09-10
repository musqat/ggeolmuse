package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.QAsset;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

// 자산 메타데이터 조회용 Repository
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
}