package com.muscat.user.common.util;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 이메일 전송 Rate Limiting 서비스
 * 1분에 1번 제한
 */
@Slf4j
@Service
public class RateLimitService {

  // 이메일 주소별 마지막 전송 시간 저장
  private final Map<String, LocalDateTime> lastSendTimes = new ConcurrentHashMap<>();

  // Rate limit 간격 (분)
  private static final long RATE_LIMIT_MINUTES = 1;

  /**
   * Rate limit 체크 및 기록
   */
  public boolean tryAcquire(String email) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime lastSendTime = lastSendTimes.get(email);

    if (lastSendTime == null) {
      // 첫 전송
      lastSendTimes.put(email, now);
      return true;
    }

    long minutesSinceLastSend = ChronoUnit.MINUTES.between(lastSendTime, now);

    if (minutesSinceLastSend >= RATE_LIMIT_MINUTES) {
      // Rate limit 통과
      lastSendTimes.put(email, now);
      return true;
    }

    // Rate limit 걸림
    log.warn("Rate limit exceeded for email: {}. Last send: {}, Minutes since: {}",
             email, lastSendTime, minutesSinceLastSend);
    return false;
  }

  /**
   * 남은 대기 시간(초) 계산
   */
  public long getRemainingWaitSeconds(String email) {
    LocalDateTime lastSendTime = lastSendTimes.get(email);

    if (lastSendTime == null) {
      return 0;
    }

    LocalDateTime now = LocalDateTime.now();
    long secondsSinceLastSend = ChronoUnit.SECONDS.between(lastSendTime, now);
    long requiredWaitSeconds = RATE_LIMIT_MINUTES * 60;

    long remainingSeconds = requiredWaitSeconds - secondsSinceLastSend;
    return Math.max(0, remainingSeconds);
  }

  /**
   * 특정 이메일의 Rate limit 기록 삭제 (테스트용)
   */
  public void reset(String email) {
    lastSendTimes.remove(email);
  }

  /**
   * 모든 Rate limit 기록 삭제 (테스트용)
   */
  public void resetAll() {
    lastSendTimes.clear();
  }

  /**
   * 오래된 기록 정리 (24시간 이상 지난 기록)
   */
  public void cleanup() {
    LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
    lastSendTimes.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    log.debug("Rate limit cleanup completed. Remaining entries: {}", lastSendTimes.size());
  }
}
