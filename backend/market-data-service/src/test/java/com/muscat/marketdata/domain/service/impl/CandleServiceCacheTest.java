package com.muscat.marketdata.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.service.CandleService;
import java.math.BigDecimal;
import java.time.LocalDate;
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

// unless 는 캐시 프록시에서만 동작해 스프링 컨텍스트로 확인
@SpringJUnitConfig(CandleServiceCacheTest.Config.class)
@DisplayName("시세 캐시 - 시세 없는 응답은 남기지 않는다")
class CandleServiceCacheTest {

  private static final String SYMBOL = "AAPL";
  private static final LocalDate DATE = LocalDate.of(2026, 1, 9);

  // @Configuration 을 빼야 앱의 @ComponentScan 에 안 잡힘(cacheManager 충돌)
  @EnableCaching
  static class Config {

    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager("ohlc", "currentPrice");
    }

    @Bean
    CandleRepository candleRepository() {
      return mock(CandleRepository.class);
    }

    @Bean
    AssetRepository assetRepository() {
      return mock(AssetRepository.class);
    }

    @Bean
    CandleService candleService(CandleRepository candleRepository, AssetRepository assetRepository,
      CacheManager cacheManager) {
      return new CandleServiceImpl(candleRepository, assetRepository, new MarketDataProperties(), cacheManager);
    }
  }

  @Autowired
  private CandleService candleService;

  @Autowired
  private CandleRepository candleRepository;

  @Autowired
  private AssetRepository assetRepository;

  @Autowired
  private CacheManager cacheManager;

  @BeforeEach
  void clear() {
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    reset(candleRepository, assetRepository);
  }

  private static Candle candle() {
    BigDecimal price = new BigDecimal("150.00");
    return Candle.builder().symbol(SYMBOL).date(DATE)
      .open(price).high(price).low(price).close(price).adjustedClose(price)
      .volume(1000L).currency("USD").build();
  }

  @Test
  @DisplayName("OHLC: 시세 없음은 캐시하지 않아 데이터가 들어오면 바로 보이고, 시세 있음은 캐시한다")
  void ohlc_UnavailableNotCached_AvailableCached() {
    given(candleRepository.findBySymbolAndDate(SYMBOL, DATE))
      .willReturn(Optional.empty())
      .willReturn(Optional.of(candle()));

    assertThat(candleService.getOHLCPrice(SYMBOL, DATE).isAvailable()).isFalse();
    assertThat(candleService.getOHLCPrice(SYMBOL, DATE).isAvailable()).isTrue();
    assertThat(candleService.getOHLCPrice(SYMBOL, DATE).isAvailable()).isTrue();

    verify(candleRepository, times(2)).findBySymbolAndDate(SYMBOL, DATE);
  }

  @Test
  @DisplayName("현재가: 시세 없음은 캐시하지 않아 데이터가 들어오면 바로 보이고, 시세 있음은 캐시한다")
  void currentPrice_UnavailableNotCached_AvailableCached() {
    given(candleRepository.findLatestBySymbol(SYMBOL))
      .willReturn(Optional.empty())
      .willReturn(Optional.of(candle()));
    given(candleRepository.findLatestBySymbolBeforeDate(SYMBOL, DATE)).willReturn(Optional.empty());
    given(assetRepository.findById(SYMBOL)).willReturn(Optional.empty());

    assertThat(candleService.getCurrentPrice(SYMBOL).isAvailable()).isFalse();
    assertThat(candleService.getCurrentPrice(SYMBOL).isAvailable()).isTrue();
    assertThat(candleService.getCurrentPrice(SYMBOL).isAvailable()).isTrue();

    verify(candleRepository, times(2)).findLatestBySymbol(SYMBOL);
  }
}
