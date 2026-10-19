package com.muscat.marketdata.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.muscat.marketdata.datasource.yf.collector.SymbolCollector;
import com.muscat.marketdata.domain.service.AssetPriceBatchUpdater;
import com.muscat.marketdata.domain.service.AssetService;
import com.muscat.marketdata.domain.service.CandleRefreshService;
import com.muscat.marketdata.domain.service.UnadjustedScanService;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 전체 갱신 버튼")
class AdminMarketControllerTest {

  @Mock private AssetService assetService;
  @Mock private AssetPriceBatchUpdater priceBatchUpdater;
  @Mock private UnadjustedScanService scanService;
  @Mock private CandleRefreshService candleRefreshService;
  @Mock private AdminJobRunner jobRunner;
  @Mock private ObjectProvider<SymbolCollector> symbolCollectorProvider;

  @InjectMocks
  private AdminMarketController controller;

  @Test
  @DisplayName("신규 종목 수집기가 없는 provider 면 503 이고 안내를 준다")
  void collectorMissing() {
    given(symbolCollectorProvider.getIfAvailable()).willReturn(null);

    var response = controller.collectNewSymbols();

    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(Objects.requireNonNull(response.getBody()).getMessage()).contains("yahoo");
  }

  @Test
  @DisplayName("전체 갱신이 이미 돌고 하나가 기다리면 세 버튼 모두 409 이고 새로 돌리지 않는다")
  void busy() {
    given(symbolCollectorProvider.getIfAvailable()).willReturn(mock(SymbolCollector.class));
    given(jobRunner.submit(any(), any())).willReturn(false);

    assertThat(controller.updateMarketCaps().getStatusCode().value()).isEqualTo(409);
    assertThat(controller.updateCandles().getStatusCode().value()).isEqualTo(409);
    assertThat(controller.collectNewSymbols().getStatusCode().value()).isEqualTo(409);
    verifyNoInteractions(assetService, priceBatchUpdater);
  }
}
