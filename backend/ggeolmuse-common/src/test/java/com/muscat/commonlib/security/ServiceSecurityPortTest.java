package com.muscat.commonlib.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = ServiceSecurityPortTest.App.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@DisplayName("관리 포트 체인의 포트 판정")
class ServiceSecurityPortTest {

  private static final int MANAGEMENT_PORT = TestSocketUtils.findAvailableTcpPort();

  @LocalServerPort
  private int apiPort;

  @DynamicPropertySource
  static void managementPort(DynamicPropertyRegistry registry) {
    registry.add("management.server.port", () -> MANAGEMENT_PORT);
  }

  // API 포트와 관리 포트(둘 다 무작위)를 같이 받는 톰캣
  @SpringBootConfiguration
  @ImportAutoConfiguration({
    ServletWebServerFactoryAutoConfiguration.class,
    DispatcherServletAutoConfiguration.class,
    WebMvcAutoConfiguration.class,
    SecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class
  })
  @Import({ServiceSecurityTest.SecurityChains.class, Probe.class})
  static class App {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> managementConnector() {
      return factory -> {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setPort(MANAGEMENT_PORT);
        factory.addAdditionalTomcatConnectors(connector);
      };
    }
  }

  @RestController
  static class Probe {

    @GetMapping({"/api/admin/stats", "/api/internal/things", "/actuator/health"})
    String ok() {
      return "ok";
    }
  }

  @Test
  @DisplayName("API 포트로 받은 요청은 Host 헤더가 관리 포트여도 관리자 · 내부 경로에서 401 이다")
  void apiPortIgnoresManagementHostHeader() throws IOException {
    assertThat(status(apiPort, "/api/admin/stats", "localhost:" + MANAGEMENT_PORT)).isEqualTo(401);
    assertThat(status(apiPort, "/api/internal/things", "localhost:" + MANAGEMENT_PORT)).isEqualTo(401);
  }

  @Test
  @DisplayName("관리 포트로 받은 헬스 요청은 Host 헤더와 상관없이 토큰 없이 통과한다")
  void managementPortHealthIsOpen() throws IOException {
    assertThat(status(MANAGEMENT_PORT, "/actuator/health", "localhost:" + MANAGEMENT_PORT)).isEqualTo(200);
    assertThat(status(MANAGEMENT_PORT, "/actuator/health", "localhost")).isEqualTo(200);
  }

  @Test
  @DisplayName("관리 포트는 management.server.port 값이고 설정이 없거나 API 포트와 같으면 어느 포트와도 맞지 않는다")
  void managementPortFollowsProperty() {
    assertThat(ServiceSecurity.managementPort(new MockEnvironment().withProperty("management.server.port", "9191")))
      .isEqualTo(9191);
    assertThat(ServiceSecurity.managementPort(new MockEnvironment())).isEqualTo(-1);
    assertThat(ServiceSecurity.managementPort(new MockEnvironment().withProperty("management.server.port", "8080")))
      .isEqualTo(-1);
    assertThat(ServiceSecurity.managementPort(new MockEnvironment()
      .withProperty("management.server.port", "8081").withProperty("server.port", "8081"))).isEqualTo(-1);
  }

  // Host 헤더를 그대로 실은 GET 의 응답 상태 코드
  private static int status(int port, String path, String host) throws IOException {
    try (Socket socket = new Socket("127.0.0.1", port)) {
      socket.setSoTimeout(5_000);
      String request = "GET " + path + " HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n";
      socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
      BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
      return Integer.parseInt(reader.readLine().split(" ")[1]);
    }
  }
}
