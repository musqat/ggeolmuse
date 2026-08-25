package com.muscat.backtest.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

  public static final String SIMULATION_RESULTS = "simulationResults";
  public static final String LATEST_FX_RATE = "latestFxRate";

  /**
   * 캐시마다 수명이 다르다.
   *
   * simulationResults 는 과거 데이터라 오래 둬도 되지만,
   * latestFxRate 는 최신 환율이라 짧게 잡아야 한다.
   */
  @Bean
  public CacheManager cacheManager() {
    SimpleCacheManager manager = new SimpleCacheManager();
    manager.setCaches(java.util.List.of(
      buildCache(SIMULATION_RESULTS, Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .recordStats()),
      buildCache(LATEST_FX_RATE, Caffeine.newBuilder()
        // 값이 하나뿐이라 크기는 의미가 없다.
        // 60초면 환율 갱신 주기에 비해 충분히 짧고, 백테스트 한 판에서
        // 반복되는 호출은 전부 걸러진다.
        .maximumSize(1)
        .expireAfterWrite(60, TimeUnit.SECONDS)
        .recordStats())
    ));
    return manager;
  }

  private org.springframework.cache.Cache buildCache(String name,
    Caffeine<Object, Object> spec) {
    CaffeineCacheManager single = new CaffeineCacheManager(name);
    single.setCaffeine(spec);
    return single.getCache(name);
  }
}
