package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Asset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, String> {

  // 특정 거래소 종목 조회
  List<Asset> findByCountry(String country);

  // 통화 기준 조회
  List<Asset> findByCurrency(String currency);
}
