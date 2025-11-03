package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Asset Repository
 */
@Repository
public interface AssetRepository extends JpaRepository<Asset, String>, AssetRepositoryCustom {

    /**
     * 활성 상태인 모든 종목 조회
     */
    List<Asset> findByActiveTrue();

    /**
     * 시가총액이 없는 종목 조회 (데이터 수집용)
     */
    List<Asset> findByMarketCapIsNull();
}
