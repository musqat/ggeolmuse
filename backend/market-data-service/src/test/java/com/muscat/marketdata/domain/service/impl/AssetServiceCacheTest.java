package com.muscat.marketdata.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

import com.muscat.marketdata.datasource.common.MarketDataProvider.CandleSource;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.service.AssetService;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

// 캐시 비우기는 캐시 프록시에서만 동작해 스프링 컨텍스트로 확인
@SpringJUnitConfig(AssetServiceCacheTest.Config.class)
@DisplayName("종목 관리 - 상장 여부나 최신 종가를 바꾸면 배당수익률 정렬 순서 캐시를 비운다")
class AssetServiceCacheTest {

  private static final String SYMBOL = "AAPL";
  private static final String KEY = "ALL:false:2026-01-09";

  // @Configuration 을 빼야 앱의 @ComponentScan 에 안 잡힘(cacheManager 충돌)
  @EnableCaching
  static class Config {

    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager("dividendYieldOrder");
    }

    @Bean
    AssetRepository assetRepository() {
      return mock(AssetRepository.class);
    }

    @Bean
    CandleSource candleSource() {
      return mock(CandleSource.class);
    }

    @Bean
    AssetService assetService(AssetRepository assetRepository) {
      return new AssetServiceImpl(assetRepository, mock(CandleRepository.class), mock(AssetEventProducer.class));
    }
  }

  @Autowired
  private AssetService assetService;

  @Autowired
  private AssetRepository assetRepository;

  @Autowired
  private CandleSource candleSource;

  @Autowired
  private CacheManager cacheManager;

  @BeforeEach
  void fillCache() {
    reset(assetRepository, candleSource);
    cacheManager.getCache("dividendYieldOrder").put(KEY, List.of(SYMBOL));
    given(assetRepository.findById(SYMBOL)).willReturn(Optional.of(Asset.builder().symbol(SYMBOL).active(true).build()));
  }

  private boolean cached() {
    return cacheManager.getCache("dividendYieldOrder").get(KEY) != null;
  }

  @Test
  @DisplayName("종목을 지우면 비운다")
  void deleteEvicts() {
    assetService.deleteAsset(SYMBOL);

    assertThat(cached()).isFalse();
  }

  @Test
  @DisplayName("지운 종목을 되살리면 비운다")
  void restoreEvicts() {
    assetService.restoreAsset(SYMBOL);

    assertThat(cached()).isFalse();
  }

  @Test
  @DisplayName("여러 종목을 한 번에 지우면 비운다")
  void deleteManyEvicts() {
    given(assetRepository.findAllById(List.of(SYMBOL))).willReturn(List.of(Asset.builder().symbol(SYMBOL).active(true).build()));

    assetService.deleteAssets(List.of(SYMBOL));

    assertThat(cached()).isFalse();
  }

  @Test
  @DisplayName("한 종목 가격을 다시 받으면 비운다")
  void updatePriceEvicts() {
    given(candleSource.fetchDailyAdjusted(eq(SYMBOL), any(), any())).willReturn(List.of(
      Candle.builder().symbol(SYMBOL).date(LocalDate.of(2026, 1, 9)).close(new BigDecimal("10")).build()));

    assetService.updateAssetPrice(SYMBOL);

    assertThat(cached()).isFalse();
  }
}
