package com.muscat.marketdata.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

// Redis 캐시 설정
@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

  private final RedisConnectionFactory redisConnectionFactory;

  @Bean
  public CacheManager cacheManager() {
    log.info("Redis Cache Manager 시작");

    // 기본 캐시 설정 (TTL 10분)
    RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofMinutes(10))
        .serializeKeysWith(
            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
        )
        .serializeValuesWith(
            RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer())
        )
        .disableCachingNullValues(); // null 값은 캐싱하지 않음

    // 캐시별 개별 설정
    Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

    // 1. OHLC 범위 조회 (백테스트용) - 24시간 TTL
    cacheConfigurations.put("ohlcPriceRange",
        defaultConfig.entryTtl(Duration.ofHours(24)));

    // 2. 주식 목록 (페이지별) - 5분 TTL
    cacheConfigurations.put("stockPrices",
        defaultConfig.entryTtl(Duration.ofMinutes(5)));

    // 3. 현재가 조회 - 30초 TTL (실시간성)
    cacheConfigurations.put("currentPrice",
        defaultConfig.entryTtl(Duration.ofSeconds(30)));

    // 4. 환율 조회 - 24시간 TTL
    cacheConfigurations.put("fxRate",
        defaultConfig.entryTtl(Duration.ofHours(24)));

    // 5. 배당 이력 조회 - 24시간 TTL
    cacheConfigurations.put("dividends",
        defaultConfig.entryTtl(Duration.ofHours(24)));

    // 6. 최신 환율 조회 - 1시간 TTL (빈번한 조회 최적화)
    cacheConfigurations.put("latestFxRate",
        defaultConfig.entryTtl(Duration.ofHours(1)));

    log.info("Configured caches: {}", cacheConfigurations.keySet());

    return RedisCacheManager.builder(redisConnectionFactory)
        .cacheDefaults(defaultConfig)
        .withInitialCacheConfigurations(cacheConfigurations)
        .build();
  }

  // 캐시 값 직렬화. 값과 함께 타입 정보를 저장한다
  // 모르는 필드는 무시. 되돌린 옛 코드가 새 코드가 넣은 값을 읽을 때 실패하지 않게
  static GenericJackson2JsonRedisSerializer valueSerializer() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    objectMapper.activateDefaultTyping(
        BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType(Object.class)
            .build(),
        ObjectMapper.DefaultTyping.NON_FINAL,
        JsonTypeInfo.As.PROPERTY
    );
    return new GenericJackson2JsonRedisSerializer(objectMapper);
  }
}
