package com.muscat.user.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RateLimitService 단위 테스트")
class RateLimitServiceTest {

  private RateLimitService rateLimitService;

  @BeforeEach
  void setUp() {
    rateLimitService = new RateLimitService();
  }

  @Nested
  @DisplayName("tryAcquire 테스트")
  class TryAcquireTests {

    @Test
    @DisplayName("첫 요청은 항상 허용")
    void tryAcquire_FirstRequest_Success() {
      // given
      String email = "first@example.com";

      // when
      boolean result = rateLimitService.tryAcquire(email);

      // then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("1분 내 중복 요청은 거부")
    void tryAcquire_WithinOneMinute_Denied() {
      // given
      String email = "duplicate@example.com";
      rateLimitService.tryAcquire(email); // 첫 요청

      // when
      boolean result = rateLimitService.tryAcquire(email); // 바로 다시 요청

      // then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("1분 경과 후 요청은 허용")
    void tryAcquire_AfterOneMinute_Success() throws InterruptedException {
      // given
      String email = "delayed@example.com";
      rateLimitService.tryAcquire(email);

      // when - 실제로 1분을 기다릴 수 없으므로, reset 사용
      rateLimitService.reset(email);
      boolean result = rateLimitService.tryAcquire(email);

      // then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("다른 이메일 주소는 독립적으로 처리")
    void tryAcquire_DifferentEmails_Independent() {
      // given
      String email1 = "user1@example.com";
      String email2 = "user2@example.com";

      // when
      boolean result1 = rateLimitService.tryAcquire(email1);
      boolean result2 = rateLimitService.tryAcquire(email2);

      // then
      assertThat(result1).isTrue();
      assertThat(result2).isTrue();
    }

    @Test
    @DisplayName("같은 이메일의 연속 요청 차단")
    void tryAcquire_ConsecutiveRequests_OnlyFirstAllowed() {
      // given
      String email = "repeat@example.com";

      // when
      boolean first = rateLimitService.tryAcquire(email);
      boolean second = rateLimitService.tryAcquire(email);
      boolean third = rateLimitService.tryAcquire(email);

      // then
      assertThat(first).isTrue();
      assertThat(second).isFalse();
      assertThat(third).isFalse();
    }
  }

  @Nested
  @DisplayName("getRemainingWaitSeconds 테스트")
  class GetRemainingWaitSecondsTests {

    @Test
    @DisplayName("첫 요청 시 대기 시간 0초")
    void getRemainingWaitSeconds_FirstRequest_ZeroSeconds() {
      // given
      String email = "new@example.com";

      // when
      long remainingSeconds = rateLimitService.getRemainingWaitSeconds(email);

      // then
      assertThat(remainingSeconds).isEqualTo(0);
    }

    @Test
    @DisplayName("요청 직후 대기 시간은 약 60초")
    void getRemainingWaitSeconds_JustAfterRequest_AboutSixtySeconds() {
      // given
      String email = "wait@example.com";
      rateLimitService.tryAcquire(email);

      // when
      long remainingSeconds = rateLimitService.getRemainingWaitSeconds(email);

      // then - 실행 시간 때문에 정확히 60초는 아니지만 58~60초 사이
      assertThat(remainingSeconds).isBetween(58L, 60L);
    }

    @Test
    @DisplayName("reset 후 대기 시간 0초")
    void getRemainingWaitSeconds_AfterReset_ZeroSeconds() {
      // given
      String email = "reset@example.com";
      rateLimitService.tryAcquire(email);

      // when
      rateLimitService.reset(email);
      long remainingSeconds = rateLimitService.getRemainingWaitSeconds(email);

      // then
      assertThat(remainingSeconds).isEqualTo(0);
    }

    @Test
    @DisplayName("음수 대기 시간은 0으로 반환")
    void getRemainingWaitSeconds_NegativeValue_ReturnsZero() {
      // given
      String email = "negative@example.com";
      rateLimitService.tryAcquire(email);
      rateLimitService.reset(email); // 기록 삭제

      // when
      long remainingSeconds = rateLimitService.getRemainingWaitSeconds(email);

      // then
      assertThat(remainingSeconds).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("reset 테스트")
  class ResetTests {

    @Test
    @DisplayName("특정 이메일 reset 후 재요청 허용")
    void reset_SpecificEmail_AllowsNewRequest() {
      // given
      String email = "reset@example.com";
      rateLimitService.tryAcquire(email);
      assertThat(rateLimitService.tryAcquire(email)).isFalse(); // 차단 확인

      // when
      rateLimitService.reset(email);
      boolean result = rateLimitService.tryAcquire(email);

      // then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("reset은 다른 이메일에 영향 없음")
    void reset_OneEmail_DoesNotAffectOthers() {
      // given
      String email1 = "user1@example.com";
      String email2 = "user2@example.com";
      rateLimitService.tryAcquire(email1);
      rateLimitService.tryAcquire(email2);

      // when
      rateLimitService.reset(email1);

      // then
      assertThat(rateLimitService.tryAcquire(email1)).isTrue();  // reset된 이메일은 허용
      assertThat(rateLimitService.tryAcquire(email2)).isFalse(); // 다른 이메일은 여전히 차단
    }

    @Test
    @DisplayName("존재하지 않는 이메일 reset해도 에러 없음")
    void reset_NonExistentEmail_NoError() {
      // when & then - 예외 발생하지 않음
      rateLimitService.reset("nonexistent@example.com");
    }
  }

  @Nested
  @DisplayName("resetAll 테스트")
  class ResetAllTests {

    @Test
    @DisplayName("모든 기록 삭제 후 전체 재요청 허용")
    void resetAll_ClearsAllRecords() {
      // given
      String email1 = "user1@example.com";
      String email2 = "user2@example.com";
      String email3 = "user3@example.com";
      rateLimitService.tryAcquire(email1);
      rateLimitService.tryAcquire(email2);
      rateLimitService.tryAcquire(email3);

      // when
      rateLimitService.resetAll();

      // then
      assertThat(rateLimitService.tryAcquire(email1)).isTrue();
      assertThat(rateLimitService.tryAcquire(email2)).isTrue();
      assertThat(rateLimitService.tryAcquire(email3)).isTrue();
    }

    @Test
    @DisplayName("빈 상태에서 resetAll 호출해도 에러 없음")
    void resetAll_EmptyState_NoError() {
      // when & then - 예외 발생하지 않음
      rateLimitService.resetAll();
    }

    @Test
    @DisplayName("resetAll 후 대기 시간 모두 0초")
    void resetAll_AllWaitTimesZero() {
      // given
      String email1 = "user1@example.com";
      String email2 = "user2@example.com";
      rateLimitService.tryAcquire(email1);
      rateLimitService.tryAcquire(email2);

      // when
      rateLimitService.resetAll();

      // then
      assertThat(rateLimitService.getRemainingWaitSeconds(email1)).isEqualTo(0);
      assertThat(rateLimitService.getRemainingWaitSeconds(email2)).isEqualTo(0);
    }
  }

  @Nested
  @DisplayName("cleanup 테스트")
  class CleanupTests {

    @Test
    @DisplayName("cleanup은 최근 기록에 영향 없음")
    void cleanup_RecentRecords_NotAffected() {
      // given
      String email = "recent@example.com";
      rateLimitService.tryAcquire(email);

      // when
      rateLimitService.cleanup();

      // then - 최근 기록이므로 여전히 차단
      assertThat(rateLimitService.tryAcquire(email)).isFalse();
    }

    @Test
    @DisplayName("빈 상태에서 cleanup 호출해도 에러 없음")
    void cleanup_EmptyState_NoError() {
      // when & then - 예외 발생하지 않음
      rateLimitService.cleanup();
    }

    @Test
    @DisplayName("여러 이메일 기록 중 최근 것만 유지")
    void cleanup_MultipleRecords_KeepsRecentOnes() {
      // given
      String email1 = "old@example.com";
      String email2 = "recent@example.com";
      rateLimitService.tryAcquire(email1);
      rateLimitService.tryAcquire(email2);

      // when
      rateLimitService.cleanup();

      // then - 둘 다 최근 기록이므로 여전히 차단됨
      assertThat(rateLimitService.tryAcquire(email1)).isFalse();
      assertThat(rateLimitService.tryAcquire(email2)).isFalse();
    }
  }

  @Nested
  @DisplayName("통합 시나리오 테스트")
  class IntegrationTests {

    @Test
    @DisplayName("실제 사용 시나리오: 이메일 재전송 요청")
    void integration_EmailResendScenario() {
      // given
      String email = "user@example.com";

      // when & then
      // 1. 첫 이메일 전송 허용
      assertThat(rateLimitService.tryAcquire(email)).isTrue();

      // 2. 바로 재전송 시도 - 거부
      assertThat(rateLimitService.tryAcquire(email)).isFalse();

      // 3. 대기 시간 확인
      long waitTime = rateLimitService.getRemainingWaitSeconds(email);
      assertThat(waitTime).isGreaterThan(0);

      // 4. Reset 후 재시도 - 허용
      rateLimitService.reset(email);
      assertThat(rateLimitService.tryAcquire(email)).isTrue();
    }

    @Test
    @DisplayName("여러 사용자 동시 요청 처리")
    void integration_MultipleUsersConcurrent() {
      // given
      String[] emails = {
        "user1@example.com",
        "user2@example.com",
        "user3@example.com",
        "user4@example.com",
        "user5@example.com"
      };

      // when & then
      // 모든 사용자의 첫 요청은 허용
      for (String email : emails) {
        assertThat(rateLimitService.tryAcquire(email)).isTrue();
      }

      // 모든 사용자의 두 번째 요청은 거부
      for (String email : emails) {
        assertThat(rateLimitService.tryAcquire(email)).isFalse();
      }

      // 전체 리셋 후 모든 요청 허용
      rateLimitService.resetAll();
      for (String email : emails) {
        assertThat(rateLimitService.tryAcquire(email)).isTrue();
      }
    }
  }
}
