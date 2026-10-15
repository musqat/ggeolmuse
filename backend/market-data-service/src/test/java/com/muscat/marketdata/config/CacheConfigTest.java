package com.muscat.marketdata.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.marketdata.domain.dto.StockPriceDto;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

class CacheConfigTest {

  @Test
  @DisplayName("캐시 값에 모르는 필드가 있어도 읽는다. 되돌린 옛 코드가 새 코드가 넣은 값을 읽을 때")
  void readsCachedValueWithUnknownField() {
    GenericJackson2JsonRedisSerializer serializer = CacheConfig.valueSerializer();
    String stored = new String(
        serializer.serialize(StockPriceDto.builder().symbol("AAPL").available(true).build()),
        StandardCharsets.UTF_8);
    // 새 버전 코드가 필드를 하나 더 저장한 값
    String newer = stored.substring(0, stored.lastIndexOf('}')) + ",\"fieldFromNewerVersion\":1}";

    Object read = serializer.deserialize(newer.getBytes(StandardCharsets.UTF_8));

    assertThat(read).isInstanceOf(StockPriceDto.class);
    assertThat(((StockPriceDto) read).getSymbol()).isEqualTo("AAPL");
  }
}
