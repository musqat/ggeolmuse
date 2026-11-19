package com.muscat.user.config;

import com.muscat.user.common.enums.response.KeycloakResponse;
import com.muscat.user.common.exception.KeycloakException;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

// 운영 프로필에서 사용자 관리 클라이언트 secret 이 비면 기동을 멈추는 검사
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class KeycloakAdminClientCheck {

  public KeycloakAdminClientCheck(KeycloakAdminClientProperties properties) {
    String secret = properties.getSecret();
    if (secret == null || secret.isBlank()) {
      throw new KeycloakException(KeycloakResponse.ADMIN_CLIENT_SECRET_MISSING);
    }
  }
}
