package com.muscat.marketdata.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.model.DividendYieldRule;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.service.DividendYieldOrder;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

class CacheConfigTest {

  @Test
  @DisplayName("캐시 값에 모르는 필드가 있어도 읽는다. 되돌린 옛 코드가 새 코드가 넣은 값을 읽을 때")
  void readsCachedValueWithUnknownField() {
    GenericJacksonJsonRedisSerializer serializer = CacheConfig.valueSerializer();
    String stored = new String(
      serializer.serialize(StockPriceDto.builder().symbol("AAPL").available(true).build()),
      StandardCharsets.UTF_8);
    // 새 버전 코드가 필드를 하나 더 저장한 값
    String newer = stored.substring(0, stored.lastIndexOf('}')) + ",\"fieldFromNewerVersion\":1}";

    Object read = serializer.deserialize(newer.getBytes(StandardCharsets.UTF_8));

    assertThat(read).isInstanceOf(StockPriceDto.class);
    assertThat(((StockPriceDto) read).getSymbol()).isEqualTo("AAPL");
  }

  // Boot 3 가 Redis 에 저장한 값
  private static final String STORED_PRICE = "{\"@class\":\"com.muscat.marketdata.domain.dto.StockPriceDto\",\"symbol\":\"AAPL\","
    + "\"name\":\"Apple Inc.\",\"currentPrice\":[\"java.math.BigDecimal\",238.15],\"previousClose\":[\"java.math.BigDecimal\",235.90],"
    + "\"changePercent\":[\"java.math.BigDecimal\",0.95],\"volume\":45678900,\"date\":[2026,1,9],\"currency\":\"USD\","
    + "\"available\":true,\"active\":true,\"marketCap\":3500000000000,\"assetType\":\"STOCK\","
    + "\"dividendYieldPercent\":[\"java.math.BigDecimal\",0.44]}";
  private static final String STORED_OHLC = "{\"@class\":\"com.muscat.marketdata.domain.dto.OHLCPriceDto\",\"symbol\":\"AAPL\","
    + "\"date\":[2026,1,9],\"openPrice\":[\"java.math.BigDecimal\",236.00],\"highPrice\":[\"java.math.BigDecimal\",239.10],"
    + "\"lowPrice\":[\"java.math.BigDecimal\",235.50],\"closePrice\":[\"java.math.BigDecimal\",238.15],"
    + "\"adjustedClose\":[\"java.math.BigDecimal\",238.15],\"volume\":45678900,\"currency\":\"USD\",\"available\":false}";
  private static final String STORED_FX = "{\"@class\":\"com.muscat.marketdata.domain.entity.FxRate\",\"date\":[2026,1,9],"
    + "\"rate\":[\"java.math.BigDecimal\",1452.30],\"currencyPair\":\"USD/KRW\"}";

  @Test
  @DisplayName("올리기 전에 저장한 현재가 · 캔들 · 환율 캐시 값을 그대로 읽는다")
  void readsValuesStoredBeforeUpgrade() {
    GenericJacksonJsonRedisSerializer serializer = CacheConfig.valueSerializer();

    StockPriceDto price = (StockPriceDto) serializer.deserialize(STORED_PRICE.getBytes(StandardCharsets.UTF_8));
    assertThat(price.getCurrentPrice()).isEqualByComparingTo("238.15");
    assertThat(price.getDate()).isEqualTo(LocalDate.of(2026, 1, 9));
    assertThat(price.isAvailable()).isTrue();

    OHLCPriceDto ohlc = (OHLCPriceDto) serializer.deserialize(STORED_OHLC.getBytes(StandardCharsets.UTF_8));
    assertThat(ohlc.getClosePrice()).isEqualByComparingTo("238.15");
    assertThat(ohlc.getDate()).isEqualTo(LocalDate.of(2026, 1, 9));

    FxRate fx = (FxRate) serializer.deserialize(STORED_FX.getBytes(StandardCharsets.UTF_8));
    assertThat(fx.getRate()).isEqualByComparingTo("1452.30");
    assertThat(fx.getDate()).isEqualTo(LocalDate.of(2026, 1, 9));
  }

  @Test
  @DisplayName("올리기 전에 타입 없이 저장한 정렬 순서 목록은 못 읽고, 캐시 오류 처리기가 캐시 미스로 넘긴다")
  void listStoredBeforeUpgradeFallsBackToMiss() {
    byte[] stored = "[\"KO\",\"T\"]".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> CacheConfig.valueSerializer().deserialize(stored)).isInstanceOf(SerializationException.class);
    assertThatCode(() -> new CacheErrorConfig().errorHandler()
      .handleCacheGetError(new SerializationException("old"), mock(Cache.class), "ALL:false"))
      .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("따로 만료 시간을 둔 캐시는 모두 @Cacheable 이 쓰는 이름이다")
  void configuredCachesAreInUse() throws Exception {
    RedisCacheManager manager = (RedisCacheManager) new CacheConfig(mock(RedisConnectionFactory.class)).cacheManager();
    manager.afterPropertiesSet();

    assertThat(manager.getCacheNames()).isNotEmpty().isSubsetOf(cacheNamesInUse());
  }

  @Test
  @DisplayName("일자별 캔들 캐시는 12시간 둔다")
  void ohlcCachedForTwelveHours() {
    RedisCacheManager manager = (RedisCacheManager) new CacheConfig(mock(RedisConnectionFactory.class)).cacheManager();
    manager.afterPropertiesSet();

    assertThat(manager.getCacheConfigurations().get("ohlc").getTtlFunction().getTimeToLive("AAPL:2026-01-09", null))
      .isEqualTo(Duration.ofHours(12));
  }

  @Test
  @DisplayName("배당수익률 정렬 순서 캐시는 12시간 둔다")
  void dividendYieldOrderCachedForTwelveHours() {
    RedisCacheManager manager = (RedisCacheManager) new CacheConfig(mock(RedisConnectionFactory.class)).cacheManager();
    manager.afterPropertiesSet();

    assertThat(manager.getCacheConfigurations().get("dividendYieldOrder").getTtlFunction().getTimeToLive("ALL:false", null))
      .isEqualTo(Duration.ofHours(12));
  }

  @Test
  @DisplayName("배당수익률 정렬 순서는 캐시 값으로 저장했다가 그대로 다시 읽힌다")
  void dividendYieldOrderSurvivesSerialization() {
    AssetRepository assetRepository = mock(AssetRepository.class);
    DividendYieldRule rule = new DividendYieldRule(LocalDate.of(2026, 1, 9), new BigDecimal("0.20"), new BigDecimal("0.50"));
    given(assetRepository.findActiveSymbolsByDividendYield(false, "ALL", rule)).willReturn(List.of("KO", "T"));
    List<String> order = new DividendYieldOrder(assetRepository).symbols("ALL", false, rule);

    GenericJacksonJsonRedisSerializer serializer = CacheConfig.valueSerializer();

    assertThat(serializer.deserialize(serializer.serialize(order))).isEqualTo(List.of("KO", "T"));
  }

  // market-data 패키지 클래스의 @Cacheable 캐시 이름
  private static Set<String> cacheNamesInUse() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter((reader, factory) -> true);
    Set<String> names = new HashSet<>();
    for (BeanDefinition candidate : scanner.findCandidateComponents("com.muscat.marketdata")) {
      Class<?> type = Class.forName(candidate.getBeanClassName(), false, CacheConfigTest.class.getClassLoader());
      for (Method method : type.getDeclaredMethods()) {
        Cacheable cacheable = AnnotationUtils.findAnnotation(method, Cacheable.class);
        if (cacheable != null) {
          names.addAll(Arrays.asList(cacheable.cacheNames()));
        }
      }
    }
    return names;
  }
}
