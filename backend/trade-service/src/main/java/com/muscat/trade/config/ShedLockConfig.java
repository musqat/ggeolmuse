package com.muscat.trade.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

// ShedLock 설정: 스케줄 작업이 파드 하나에서만 돌게 함(잠금은 DB 의 shedlock 테이블)
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "5m")
public class ShedLockConfig {

  // 시각은 DB 기준(파드마다 시계가 달라도 같은 잠금 판단)
  @Bean
  public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
      .withJdbcTemplate(new JdbcTemplate(dataSource))
      .usingDbTime()
      .build());
  }
}
