package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Asset;
import java.util.List;

/**
 * Asset Repository Custom Interface
 */
public interface AssetRepositoryCustom {

    /**
     * 키워드로 심볼 또는 회사명 검색 (DB에서 필터링)
     */
    List<Asset> searchByKeyword(String keyword, int limit);
}
