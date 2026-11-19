package com.muscat.marketdata.api.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.muscat.marketdata.common.exception.MarketDataException;
import com.muscat.marketdata.datasource.yf.collector.SymbolCollector;
import com.muscat.marketdata.domain.service.AssetPriceBatchUpdater;
import com.muscat.marketdata.domain.service.AssetService;
import com.muscat.marketdata.domain.service.CandleRefreshService;
import com.muscat.marketdata.domain.service.UnadjustedScanService;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
  @DisplayName("신규 종목 수집기가 없는 provider 면 수집기 없음 오류")
  void collectorMissing() {
    given(symbolCollectorProvider.getIfAvailable()).willReturn(null);

    assertThatThrownBy(controller::collectNewSymbols)
      .isInstanceOf(MarketDataException.class)
      .hasFieldOrPropertyWithValue("errorCode", "SYMBOL_COLLECTOR_UNAVAILABLE");
  }

  @Test
  @DisplayName("전체 갱신이 이미 돌고 하나가 기다리면 세 버튼 모두 작업 중 오류이고 새로 돌리지 않음")
  void busy() {
    given(symbolCollectorProvider.getIfAvailable()).willReturn(mock(SymbolCollector.class));
    given(jobRunner.submit(any(), any())).willReturn(false);

    List.<ThrowingCallable>of(controller::updateMarketCaps, controller::updateCandles, controller::collectNewSymbols)
      .forEach(call -> assertThatThrownBy(call)
        .isInstanceOf(MarketDataException.class)
        .hasFieldOrPropertyWithValue("errorCode", "ADMIN_JOB_BUSY"));
    verifyNoInteractions(assetService, priceBatchUpdater);
  }
}
