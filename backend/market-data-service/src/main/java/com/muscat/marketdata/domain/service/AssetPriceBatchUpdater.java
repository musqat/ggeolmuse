package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.common.exception.MarketDataException;
import com.muscat.marketdata.domain.entity.Asset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 전체 종목 가격 갱신. 단건 갱신이 트랜잭션이라 프록시를 거치게 다른 빈에서 부름
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetPriceBatchUpdater {

  private final AssetService assetService;

  // 한 종목이 실패해도 다음 종목으로. 성공한 수 반환
  public int updateAllPrices() {
    List<Asset> assets = assetService.getAllAssets();
    int updated = 0;
    for (Asset asset : assets) {
      try {
        assetService.updateAssetPrice(asset.getSymbol());
        updated++;
      } catch (MarketDataException e) {
        log.warn("캔들 데이터 업데이트 실패: symbol={}, code={}, message={}",
            asset.getSymbol(), e.getErrorCode(), e.getErrorMessage());
      } catch (Exception e) {
        log.warn("캔들 데이터 업데이트 실패: symbol={}", asset.getSymbol(), e);
      }
    }
    log.info("캔들 전체 업데이트 완료: {}개 중 {}개", assets.size(), updated);
    return updated;
  }
}
