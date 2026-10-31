package com.muscat.configserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;

// config-server 응답 시간 지표의 지연 구간
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
    "spring.profiles.active=native",
    "spring.cloud.config.server.native.search-locations=classpath:/config-repo/",
    "management.server.port=0",
    "CONFIG_SERVER_PASSWORD=test-password"})
class LatencyBucketTest {

  @Autowired
  private TestRestTemplate rest;

  @LocalManagementPort
  private int managementPort;

  @Test
  @DisplayName("설정 요청의 응답 시간 지표에 100ms · 300ms · 1초 · 2초 · 5초 구간이 있다")
  void configRequestHasLatencyBuckets() {
    rest.withBasicAuth("config", "test-password").getForEntity("/demo-service/default", String.class);

    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(configRequestBounds()).contains("0.1", "0.3", "1.0", "2.0", "5.0"));
  }

  private List<String> configRequestBounds() {
    String scrape = rest.getForObject(
        "http://localhost:" + managementPort + "/actuator/prometheus", String.class);
    return scrape.lines()
        .filter(line -> line.startsWith("http_server_requests_seconds_bucket{"))
        .filter(line -> line.contains("uri=\"/{name}/{profiles"))
        .map(line -> line.replaceAll(".*le=\"([^\"]+)\".*", "$1"))
        .toList();
  }
}
