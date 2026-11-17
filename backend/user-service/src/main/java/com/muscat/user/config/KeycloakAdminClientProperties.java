package com.muscat.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Keycloak 관리 API(사용자 만들기 · 비밀번호 · 역할)를 부르는 클라이언트
@Data
@ConfigurationProperties(prefix = "keycloak.admin-client")
public class KeycloakAdminClientProperties {

  private String id;
  private String secret;
}
