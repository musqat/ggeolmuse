package com.muscat.marketdata.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;

@DisplayName("관리자 작업 상태(파드 사이 공유)")
class AdminJobStateTest {

  private static final String JOB = "job";

  private EmbeddedDatabase db;
  private AdminJobState podA;
  private AdminJobState podB;

  @BeforeEach
  void setUp() {
    db = AdminJobStates.database();
    podA = AdminJobStates.pod(db);
    podB = AdminJobStates.pod(db);
  }

  @AfterEach
  void tearDown() {
    db.shutdown();
  }

  @Test
  @DisplayName("한 파드가 돌리는 중이면 다른 파드의 시작은 거절되고 진행 중으로 보인다")
  void secondStartRejected() {
    assertThat(podA.tryStart(JOB)).isTrue();

    assertThat(podB.tryStart(JOB)).isFalse();
    assertThat(podB.isRunning(JOB)).isTrue();
  }

  @Test
  @DisplayName("진행 중에 저장한 상태를 다른 파드가 본다")
  void progressVisibleToOtherPod() {
    podA.tryStart(JOB);
    podA.save(JOB, Map.of("processed", 5));

    assertThat(podB.detail(JOB, Map.class)).hasValueSatisfying(d -> assertThat(d).contains(entry("processed", 5)));
  }

  @Test
  @DisplayName("끝내면 진행 중이 아니고 마지막 상태가 남으며 다시 시작할 수 있다")
  void finishReleases() {
    podA.tryStart(JOB);
    podA.finish(JOB, Map.of("count", 3));

    assertThat(podB.isRunning(JOB)).isFalse();
    assertThat(podB.detail(JOB, Map.class)).hasValueSatisfying(d -> assertThat(d).contains(entry("count", 3)));
    assertThat(podB.tryStart(JOB)).isTrue();
  }

  @Test
  @DisplayName("갱신이 오래 끊기면 진행 중이 아니고 다른 파드가 다시 시작한다")
  void staleHeartbeatReleases() {
    podA.tryStart(JOB);
    new JdbcTemplate(db).update("UPDATE admin_job_state SET heartbeat_at = ? WHERE name = ?",
      LocalDateTime.now().minus(AdminJobState.STALE_AFTER).minusMinutes(1), JOB);

    assertThat(podB.isRunning(JOB)).isFalse();
    assertThat(podB.tryStart(JOB)).isTrue();
  }

  @Test
  @DisplayName("시작 없이 끝내도 마지막 상태가 남는다")
  void finishWithoutStart() {
    podA.finish(JOB, Map.of("count", 1));

    assertThat(podB.detail(JOB, Map.class)).isPresent();
    assertThat(podB.isRunning(JOB)).isFalse();
  }

  @Test
  @DisplayName("기록이 없으면 상태가 비어 있고 진행 중이 아니다")
  void emptyWhenNoRecord() {
    assertThat(podB.detail(JOB, Map.class)).isEmpty();
    assertThat(podB.isRunning(JOB)).isFalse();
  }
}
