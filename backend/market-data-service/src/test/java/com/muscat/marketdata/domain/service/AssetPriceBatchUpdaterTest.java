package com.muscat.marketdata.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.common.enums.response.MarketDataResponse;
import com.muscat.marketdata.common.exception.MarketDataException;
import com.muscat.marketdata.domain.entity.Asset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("전체 종목 가격 갱신")
class AssetPriceBatchUpdaterTest {

  @Mock private AssetService assetService;

  @InjectMocks private AssetPriceBatchUpdater updater;

  private static Asset asset(String symbol) {
    return Asset.builder().symbol(symbol).name(symbol).build();
  }

  @Test
  @DisplayName("한 종목이 시세 예외, 다른 종목이 실행 예외여도 나머지를 갱신하고 성공한 수를 돌려준다")
  void updateAllPrices_ContinuesAfterFailures() {
    given(assetService.getAllAssets()).willReturn(List.of(asset("EMPTY"), asset("BOOM"), asset("AAPL")));
    willThrow(new MarketDataException(MarketDataResponse.PRICE_DATA_NOT_FOUND)).given(assetService).updateAssetPrice("EMPTY");
    willThrow(new RuntimeException("boom")).given(assetService).updateAssetPrice("BOOM");

    int updated = updater.updateAllPrices();

    assertThat(updated).isEqualTo(1);
    verify(assetService).updateAssetPrice("AAPL");
  }
}
