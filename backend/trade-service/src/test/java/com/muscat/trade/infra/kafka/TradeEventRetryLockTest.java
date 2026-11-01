package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.trade.config.ShedLockConfig;
import com.muscat.trade.domain.repository.TradeRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

// 잠금 테이블 마이그레이션 · ShedLock 설정 · 다시 보내기 스케줄러를 같이 띄운 잠금 동작
@SpringJUnitConfig(classes = {ShedLockConfig.class, TradeEventRetryScheduler.class, TradeEventRetryLockTest.Beans.class})
@DisplayName("체결 이벤트 다시 보내기 잠금")
class TradeEventRetryLockTest {

  // 설정 어노테이션 없는 빈 정의(서비스의 @ComponentScan 에 안 걸리게)
  static class Beans {

    @Bean
    DataSource dataSource() {
      return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true)
        .addScript("classpath:db/migration/V10__Create_Shedlock.sql").build();
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @MockitoBean
  private TradeRepository tradeRepository;

  @MockitoBean
  private TradeEventProducer tradeEventProducer;

  @MockitoBean
  private TradePublishRecorder tradePublishRecorder;

  @Autowired
  private TradeEventRetryScheduler scheduler;

  @Autowired
  private DataSource dataSource;

  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.update("DELETE FROM shedlock");
  }

  @Test
  @DisplayName("다른 파드가 잠금을 쥐고 있으면 이번 회차를 건너뛴다")
  void skipsWhileAnotherPodHoldsLock() {
    jdbc.update("INSERT INTO shedlock (name, lock_until, locked_at, locked_by) "
      + "VALUES (?, DATEADD('DAY', 1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, ?)", TradeEventRetryScheduler.LOCK_NAME, "other-pod");

    scheduler.resend();

    verify(tradeRepository, never()).findTop100ByEventPublishedAtIsNullAndCreatedAtBeforeOrderByIdAsc(any());
  }

  @Test
  @DisplayName("잠금이 없으면 돌고 잠금 행을 남긴다")
  void runsAndRecordsLock() {
    scheduler.resend();

    verify(tradeRepository).findTop100ByEventPublishedAtIsNullAndCreatedAtBeforeOrderByIdAsc(any());
    assertThat(jdbc.queryForObject("SELECT locked_by FROM shedlock WHERE name = ?", String.class,
      TradeEventRetryScheduler.LOCK_NAME)).isNotBlank();
  }
}
