package com.muscat.user.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@DisplayName("메일 발송 한도 (Redis)")
class RateLimitServiceTest {

  private static final String EMAIL = "a@b.com";
  private static final String KEY = "ratelimit:mail:a@b.com";

  private StringRedisTemplate redisTemplate;
  private ValueOperations<String, String> values;
  private RateLimitService rateLimitService;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    values = mock(ValueOperations.class);
    given(redisTemplate.opsForValue()).willReturn(values);
    rateLimitService = new RateLimitService(redisTemplate);
  }

  @Nested
  @DisplayName("tryAcquire")
  class TryAcquire {

    @Test
    @DisplayName("키가 없으면 1분짜리 키를 두고 통과한다")
    void firstRequest_SetsKeyForOneMinute() {
      given(values.setIfAbsent(eq(KEY), anyString(), eq(Duration.ofMinutes(1)))).willReturn(true);

      assertThat(rateLimitService.tryAcquire(EMAIL)).isTrue();
      verify(values).setIfAbsent(eq(KEY), anyString(), eq(Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("키가 살아 있으면 막는다. 다른 파드가 둔 키여도 같다")
    void keyAlive_Denied() {
      given(values.setIfAbsent(eq(KEY), anyString(), eq(Duration.ofMinutes(1)))).willReturn(false);

      assertThat(rateLimitService.tryAcquire(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("Redis 가 결과를 주지 않으면 막는다")
    void nullResult_Denied() {
      given(values.setIfAbsent(eq(KEY), anyString(), eq(Duration.ofMinutes(1)))).willReturn(null);

      assertThat(rateLimitService.tryAcquire(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("Redis 가 안 되면 메일 기능을 막지 않고 통과시킨다")
    void redisDown_Allows() {
      given(values.setIfAbsent(eq(KEY), anyString(), eq(Duration.ofMinutes(1))))
        .willThrow(new RedisConnectionFailureException("down"));

      assertThat(rateLimitService.tryAcquire(EMAIL)).isTrue();
    }
  }

  @Nested
  @DisplayName("getRemainingWaitSeconds")
  class RemainingWaitSeconds {

    @Test
    @DisplayName("키의 남은 TTL 을 준다")
    void fromTtl() {
      given(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).willReturn(42L);

      assertThat(rateLimitService.getRemainingWaitSeconds(EMAIL)).isEqualTo(42L);
    }

    @Test
    @DisplayName("키가 없거나(-2) 만료가 없거나(-1) 결과가 없으면 0 이다")
    void noKeyOrNoTtl_Zero() {
      given(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).willReturn(-2L, -1L, null);

      assertThat(rateLimitService.getRemainingWaitSeconds(EMAIL)).isZero();
      assertThat(rateLimitService.getRemainingWaitSeconds(EMAIL)).isZero();
      assertThat(rateLimitService.getRemainingWaitSeconds(EMAIL)).isZero();
    }

    @Test
    @DisplayName("Redis 가 안 되면 0 이다")
    void redisDown_Zero() {
      given(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).willThrow(new RedisConnectionFailureException("down"));

      assertThat(rateLimitService.getRemainingWaitSeconds(EMAIL)).isZero();
    }
  }
}
