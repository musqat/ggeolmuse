package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetRepository extends JpaRepository<Asset, String> {

    List<Asset> findByCountry(String country);
    
    List<Asset> findByCurrency(String currency);
}
