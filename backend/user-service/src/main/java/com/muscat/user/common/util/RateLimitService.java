package com.muscat.user.common.util;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 이메일 전송 Rate Limiting 서비스
 * 1분에 1번 제한. 파드가 여럿이어도 같은 한도를 보도록 Redis 에 두고, 지난 기록은 TTL 로 사라진다
 */
@Slf4j
@Service
public class RateLimitService {

  private static final String KEY_PREFIX = "ratelimit:mail:";
  private static final Duration RATE_LIMIT = Duration.ofMinutes(1);

  private final StringRedisTemplate redisTemplate;

  public RateLimitService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * Rate limit 체크 및 기록. 키가 없을 때만 1분짜리 키를 두고 통과시킨다
   */
  public boolean tryAcquire(String email) {
    try {
      Boolean acquired = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + email, "1", RATE_LIMIT);
      if (Boolean.TRUE.equals(acquired)) {
        return true;
      }
      log.warn("Rate limit exceeded for email: {}", email);
      return false;
    } catch (DataAccessException e) {
      // Redis 가 잠깐 안 될 때 메일 재발송 · 재설정까지 막지 않는다. 게이트웨이도 IP 로 따로 막는다
      log.warn("Rate limit 확인 실패, 통과시킴: email={}, error={}", email, e.getMessage());
      return true;
    }
  }

  /**
   * 남은 대기 시간(초)
   */
  public long getRemainingWaitSeconds(String email) {
    try {
      Long ttl = redisTemplate.getExpire(KEY_PREFIX + email, TimeUnit.SECONDS);
      return ttl != null && ttl > 0 ? ttl : 0;
    } catch (DataAccessException e) {
      log.warn("Rate limit 대기 시간 확인 실패: email={}, error={}", email, e.getMessage());
      return 0;
    }
  }
}
