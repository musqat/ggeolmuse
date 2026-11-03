package com.muscat.commonlib.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.Retryer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

// 서비스 Feign 클라이언트 공통 설정(로그 · 재시도 · 토큰 전달)
@Configuration
@Slf4j
public class FeignConfig {

  @Bean
  public Logger.Level feignLoggerLevel() {
    return Logger.Level.BASIC;
  }

  // Feign 자체 재시도 끔(재시도는 Resilience4j 만)
  @Bean
  public Retryer retryer() {
    return Retryer.NEVER_RETRY;
  }

  @Bean
  public RequestInterceptor jwtTokenInterceptor() {
    return requestTemplate -> {
      try {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
          String token = jwt.getTokenValue();
          requestTemplate.header("Authorization", "Bearer " + token);
        }
      } catch (Exception e) {
        log.warn("JWT 토큰 처리 오류: target={}, error={}",
          requestTemplate.feignTarget().name(), e.getMessage());
      }
    };
  }
}
