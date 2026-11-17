package com.muscat.jpatest;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

// 저장소 테스트 Postgres 컨테이너(같은 설정의 테스트 클래스끼리 컨텍스트 캐시로 하나를 같이 씀)
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgres() {
    return new PostgreSQLContainer<>("postgres:15-alpine");
  }
}
