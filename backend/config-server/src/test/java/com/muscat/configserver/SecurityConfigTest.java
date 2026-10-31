package com.muscat.configserver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

// config-server 관리 포트 · API 포트 인증
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
    "spring.profiles.active=native",
    "spring.cloud.config.server.native.search-locations=classpath:/config-repo/",
    "management.server.port=0",
    "CONFIG_SERVER_PASSWORD=test-password"})
class SecurityConfigTest {

  @Autowired
  private TestRestTemplate rest;

  @LocalManagementPort
  private int managementPort;

  @Test
  @DisplayName("관리 포트의 health 는 인증 없이 열린다")
  void managementHealthIsOpen() {
    ResponseEntity<String> response = rest.getForEntity(
        "http://localhost:" + managementPort + "/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
  }

  @Test
  @DisplayName("API 포트의 actuator 모양 경로는 설정 요청으로 받아 인증이 필요하다")
  void apiPortActuatorPathNeedsAuth() {
    ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("설정 요청은 인증이 없으면 401")
  void configRequestWithoutAuthIsRejected() {
    ResponseEntity<String> response = rest.getForEntity("/demo-service/default", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("인증한 설정 요청은 저장소의 값을 받는다")
  void configRequestWithAuthReturnsValues() {
    ResponseEntity<String> response = rest.withBasicAuth("config", "test-password")
        .getForEntity("/demo-service/default", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"demo.greeting\":\"hello\"");
  }
}
