package com.muscat.marketdata.provider.yf;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.provider.MarketDataProvider.SymbolSource;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class TestSymbolSource implements SymbolSource {

  private final AssetRepository assetRepository;

  @Override
  public List<Asset> fetchSymbols() {
    List<Asset> existingAssets = assetRepository.findAll();
    log.info("SymbolSource - DB에서 기존 심볼 {}개 조회", existingAssets.size());
    return existingAssets;
  }
}