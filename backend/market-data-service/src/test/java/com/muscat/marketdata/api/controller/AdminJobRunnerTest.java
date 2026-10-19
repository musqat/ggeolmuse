package com.muscat.marketdata.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("관리자 전체 갱신 실행기")
class AdminJobRunnerTest {

  @Test
  @DisplayName("한 번에 하나만 돌고 하나만 기다리며, 넘치면 받지 않고 비면 다시 받는다")
  void oneRunningOneWaiting() throws Exception {
    AdminJobRunner runner = new AdminJobRunner();
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(2);

    assertThat(runner.submit("일", () -> {
      started.countDown();
      awaitQuietly(release);
      finished.countDown();
    })).isTrue();
    assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

    assertThat(runner.submit("일", finished::countDown)).isTrue();
    assertThat(runner.submit("일", finished::countDown)).isFalse();

    release.countDown();
    assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();

    CountDownLatch again = new CountDownLatch(1);
    assertThat(runner.submit("일", again::countDown)).isTrue();
    assertThat(again.await(2, TimeUnit.SECONDS)).isTrue();
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(2, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  @DisplayName("일이 실패해도 실행기는 다음 일을 받는다")
  void failingJobDoesNotStopRunner() throws Exception {
    AdminJobRunner runner = new AdminJobRunner();
    CountDownLatch next = new CountDownLatch(1);

    assertThat(runner.submit("실패하는 일", () -> { throw new IllegalStateException("boom"); })).isTrue();
    assertThat(runner.submit("다음 일", next::countDown)).isTrue();

    assertThat(next.await(2, TimeUnit.SECONDS)).isTrue();
  }
}
