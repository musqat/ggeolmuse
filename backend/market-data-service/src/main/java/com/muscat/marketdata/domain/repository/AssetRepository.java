package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Asset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetRepository extends JpaRepository<Asset, String>, AssetRepositoryCustom {

  // 활성 상태인 모든 종목 조회
  List<Asset> findByActiveTrue();

  List<Asset> findByActiveTrueAndAssetTypeNot(String assetType);

  List<Asset> findByMarketCapIsNullAndAssetTypeNot(String assetType);

  long countByAssetTypeNot(String assetType);
}
