package com.muscat.marketdata.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

// 테스트용 관리자 작업 상태: 마이그레이션 SQL 로 만든 H2 하나에 파드마다 상태 객체 하나
final class AdminJobStates {

  private AdminJobStates() {
  }

  static EmbeddedDatabase database() {
    return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true)
      .addScript("classpath:db/migration/V15__Create_Admin_Job_State.sql").build();
  }

  static AdminJobState pod(DataSource dataSource) {
    return new AdminJobState(new JdbcTemplate(dataSource), new ObjectMapper().findAndRegisterModules());
  }
}
