package com.muscat.user.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.user.config.ShedLockConfig;
import com.muscat.user.domain.user.repository.UserDeletionRepository;
import com.muscat.user.infra.kafka.UserEventProducer;
import java.time.Clock;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

// 잠금 테이블 마이그레이션 · ShedLock 설정 · 탈퇴 정리를 같이 띄운 잠금 동작
@SpringJUnitConfig(classes = {ShedLockConfig.class, UserDeletionCleaner.class, UserDeletionLockTest.Beans.class})
@DisplayName("탈퇴 정리 잠금")
class UserDeletionLockTest {

  // 설정 어노테이션 없는 빈 정의(서비스의 @ComponentScan 에 안 걸리게)
  static class Beans {

    @Bean
    DataSource dataSource() {
      return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true)
        .addScript("classpath:db/migration/V14__Create_Shedlock.sql").build();
    }

    @Bean
    Clock clock() {
      return Clock.systemDefaultZone();
    }
  }

  @MockitoBean
  private UserDeletionRepository userDeletionRepository;

  @MockitoBean
  private UserEventProducer userEventProducer;

  @MockitoBean
  private KeycloakService keycloakService;

  @Autowired
  private UserDeletionCleaner cleaner;

  @Autowired
  private DataSource dataSource;

  private JdbcTemplate jdbc;

  // 잠금 행은 지우지 않고 만료시킴(ShedLock 이 있는 행을 기억해 UPDATE 로만 잡음)
  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.update("MERGE INTO shedlock (name, lock_until, locked_at, locked_by) KEY (name) "
      + "VALUES (?, DATEADD('DAY', -1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, ?)", UserDeletionCleaner.LOCK_NAME, "expired");
  }

  private void holdLockOnOtherPod() {
    jdbc.update("UPDATE shedlock SET lock_until = DATEADD('DAY', 1, CURRENT_TIMESTAMP), locked_by = ? WHERE name = ?",
      "other-pod", UserDeletionCleaner.LOCK_NAME);
  }

  @Test
  @DisplayName("다른 파드가 잠금을 가지고 있으면 다시 정리를 건너뛴다")
  void retryPending_SkipsWhileAnotherPodHoldsLock() {
    holdLockOnOtherPod();

    cleaner.retryPending();

    verify(userDeletionRepository, never()).findByEventPublishedAtIsNullOrKeycloakDeletedAtIsNull();
    verify(userDeletionRepository, never()).deleteByEventPublishedAtIsNotNullAndKeycloakDeletedAtBefore(any());
  }

  @Test
  @DisplayName("잠금이 없으면 다시 정리가 돌고 잠금 행을 남긴다")
  void retryPending_RunsAndRecordsLock() {
    cleaner.retryPending();

    verify(userDeletionRepository).findByEventPublishedAtIsNullOrKeycloakDeletedAtIsNull();
    assertThat(jdbc.queryForObject("SELECT locked_by FROM shedlock WHERE name = ?", String.class,
      UserDeletionCleaner.LOCK_NAME)).isNotEqualTo("expired");
  }

  @Test
  @DisplayName("다른 파드가 다시 정리 중이면 탈퇴 직후 정리를 넘기고 다음 다시 정리에 맡긴다")
  void cleanUp_SkipsWhileAnotherPodHoldsLock() {
    holdLockOnOtherPod();

    cleaner.cleanUp("kc-1");

    verify(userDeletionRepository, never()).findById(anyString());
  }

  @Test
  @DisplayName("탈퇴 직후 정리가 끝나면 잠금을 풀어 다음 다시 정리가 바로 돈다")
  void cleanUp_ReleasesLockAfterward() {
    cleaner.cleanUp("kc-1");

    verify(userDeletionRepository).findById("kc-1");
    cleaner.retryPending();
    verify(userDeletionRepository).findByEventPublishedAtIsNullOrKeycloakDeletedAtIsNull();
  }
}
