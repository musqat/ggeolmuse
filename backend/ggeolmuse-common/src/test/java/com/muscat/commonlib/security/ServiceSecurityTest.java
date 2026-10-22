package com.muscat.commonlib.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ServiceSecurityTest.Probe.class)
@ContextConfiguration(classes = {ServiceSecurityTest.SecurityChains.class, ServiceSecurityTest.Probe.class})
@DisplayName("서비스 보안 설정 조각")
class ServiceSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  // 관리 포트 · 관리자 · API 세 체인을 순서대로 둠
  @Configuration
  @EnableWebSecurity
  static class SecurityChains {

    // admin-token 만 realm 역할 admin 을 가진 토큰으로 풂
    @Bean
    JwtDecoder jwtDecoder() {
      return token -> Jwt.withTokenValue(token)
        .header("alg", "none")
        .subject("user-1")
        .claim("realm_access", Map.of("roles", token.equals("admin-token") ? List.of("admin") : List.of()))
        .build();
    }

    @Bean
    @Order(0)
    SecurityFilterChain management(HttpSecurity http) throws Exception {
      return ServiceSecurity.managementChain(http);
    }

    @Bean
    @Order(1)
    SecurityFilterChain admin(HttpSecurity http) throws Exception {
      return ServiceSecurity.adminChain(http, new KeycloakJwtAuthenticationConverter());
    }

    @Bean
    @Order(2)
    SecurityFilterChain api(HttpSecurity http) throws Exception {
      ServiceSecurity.statelessApi(http)
        .authorizeHttpRequests(auth -> auth
          .requestMatchers(ServiceSecurity.SWAGGER_PATHS).permitAll()
          .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
      return http.build();
    }
  }

  @RestController
  static class Probe {

    @GetMapping({"/api/admin/stats", "/api/things", "/swagger-ui/index.html", "/actuator/health"})
    String ok() {
      return "ok";
    }
  }

  @Test
  @DisplayName("관리 포트(9090) 요청은 토큰 없이 통과한다")
  void managementPortIsOpen() throws Exception {
    mockMvc.perform(get("/actuator/health").with(request -> {
      request.setServerPort(9090);
      return request;
    })).andExpect(status().isOk());
  }

  @Test
  @DisplayName("관리자 경로는 토큰이 없으면 401, realm 역할 admin 이 없으면 403, 있으면 통과한다")
  void adminPathNeedsRealmRole() throws Exception {
    mockMvc.perform(get("/api/admin/stats")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer user-token")).andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer admin-token")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("API 는 토큰이 있어야 하고 문서 경로는 토큰 없이 열린다")
  void apiNeedsTokenButDocsAreOpen() throws Exception {
    mockMvc.perform(get("/api/things")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/things").header("Authorization", "Bearer user-token")).andExpect(status().isOk());
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("API 응답은 세션을 만들지 않고 프레임 금지 헤더를 붙이지 않는다")
  void statelessWithoutFrameOptions() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/things").header("Authorization", "Bearer user-token"))
      .andExpect(status().isOk())
      .andExpect(header().doesNotExist("X-Frame-Options"))
      .andReturn();

    assertThat(result.getRequest().getSession(false)).isNull();
  }
}
