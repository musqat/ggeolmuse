package com.muscat.trade.infra.client.config;

import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

@Configuration
@Slf4j
public class FeignConfig {

  @Bean
  public RequestInterceptor jwtTokenInterceptor() {
    return requestTemplate -> {
      try {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
          String token = jwt.getTokenValue();
          requestTemplate.header("Authorization", "Bearer " + token);
          log.debug("JWT 토큰 헤더 추가: target={}", requestTemplate.feignTarget().name());
        } else {
          log.debug("JWT 토큰 없음: target={}", requestTemplate.feignTarget().name());
        }
      } catch (Exception e) {
        log.warn("JWT 토큰 처리 오류: target={}, error={}", 
            requestTemplate.feignTarget().name(), e.getMessage());
      }
    };
  }
}