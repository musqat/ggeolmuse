package com.muscat.marketdata.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.repository.DividendRepository;
import com.muscat.marketdata.domain.service.CandleService;
import com.muscat.marketdata.domain.service.impl.CandleServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(CacheErrorConfigTest.Config.class)
@DisplayName("캐시 오류 - Redis 를 못 읽거나 못 써도 DB 값으로 답한다")
class CacheErrorConfigTest {

  private static final String SYMBOL = "AAPL";
  private static final LocalDate DATE = LocalDate.of(2026, 1, 9);

  // @Configuration 을 빼야 앱의 @ComponentScan 에 안 잡힘
  @EnableCaching
  @Import(CacheErrorConfig.class)
  static class Config {

    @Bean
    Cache brokenCache() {
      Cache cache = mock(Cache.class);
      given(cache.getName()).willReturn("ohlc");
      given(cache.get(any())).willThrow(new RedisConnectionFailureException("Redis 연결 실패"));
      willThrow(new RedisConnectionFailureException("Redis 연결 실패")).given(cache).put(any(), any());
      return cache;
    }

    @Bean
    CacheManager cacheManager(Cache brokenCache) {
      return new CacheManager() {
        @Override
        public Cache getCache(String name) {
          return brokenCache;
        }

        @Override
        public List<String> getCacheNames() {
          return List.of("ohlc");
        }
      };
    }

    @Bean
    CandleRepository candleRepository() {
      CandleRepository repository = mock(CandleRepository.class);
      given(repository.findBySymbolAndDate(SYMBOL, DATE)).willReturn(Optional.of(Candle.builder().symbol(SYMBOL).date(DATE)
        .close(new BigDecimal("10")).adjustedClose(new BigDecimal("9.5")).currency("USD").build()));
      return repository;
    }

    @Bean
    CandleService candleService(CandleRepository candleRepository, CacheManager cacheManager) {
      return new CandleServiceImpl(candleRepository, mock(AssetRepository.class), mock(DividendRepository.class),
          new MarketDataProperties(), cacheManager);
    }
  }

  @Autowired
  private CandleService candleService;

  @Test
  @DisplayName("캐시 읽기 · 쓰기가 모두 실패해도 저장소에서 읽어 돌려준다")
  void fallsBackToRepository() {
    OHLCPriceDto result = candleService.getOHLCPrice(SYMBOL, DATE);

    assertThat(result.isAvailable()).isTrue();
    assertThat(result.getClosePrice()).isEqualByComparingTo("10");
  }
}
