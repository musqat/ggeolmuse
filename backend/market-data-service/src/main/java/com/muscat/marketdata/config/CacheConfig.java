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
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
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

    // 1. 현재가 조회 - 30초 TTL (실시간성)
    cacheConfigurations.put("currentPrice",
        defaultConfig.entryTtl(Duration.ofSeconds(30)));

    // 2. 최신 환율 조회 - 1시간 TTL (빈번한 조회 최적화)
    cacheConfigurations.put("latestFxRate",
        defaultConfig.entryTtl(Duration.ofHours(1)));

    // 3. 일자별 캔들 조회 - 12시간 TTL (캔들 · 배당을 저장하면 비움)
    cacheConfigurations.put("ohlc",
        defaultConfig.entryTtl(Duration.ofHours(12)));

    // 4. 배당수익률 정렬 순서 - 12시간 TTL (캔들 · 배당을 저장하면 비움)
    cacheConfigurations.put("dividendYieldOrder",
        defaultConfig.entryTtl(Duration.ofHours(12)));

    log.info("Configured caches: {}", cacheConfigurations.keySet());

    // 캐시 비우기는 KEYS 대신 SCAN(KEYS 는 키가 많으면 그동안 Redis 를 막음)
    RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(
        redisConnectionFactory, BatchStrategies.scan(1000));

    return RedisCacheManager.builder(cacheWriter)
        .cacheDefaults(defaultConfig)
        .withInitialCacheConfigurations(cacheConfigurations)
        .build();
  }

  // 캐시 값 직렬화(값과 함께 타입 정보 저장)
  // 모르는 필드는 무시(되돌린 옛 코드가 새 코드가 넣은 값을 읽을 때 실패하지 않게)
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
