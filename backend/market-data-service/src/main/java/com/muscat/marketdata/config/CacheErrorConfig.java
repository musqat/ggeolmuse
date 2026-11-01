package com.muscat.marketdata.config;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Configuration;

// 캐시를 못 읽거나 못 쓰면 경고 로그만 남기고 캐시 미스로 처리(Redis 가 없어도 DB 로 응답)
@Configuration
public class CacheErrorConfig implements CachingConfigurer {

  @Override
  public CacheErrorHandler errorHandler() {
    return new LoggingCacheErrorHandler();
  }
}
