package com.muscat.commonlib.config;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class FeignConfig {

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
                10, TimeUnit.SECONDS,  // connectTimeout
                30, TimeUnit.SECONDS,  // readTimeout
                true
        );
    }

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(1000, 2000, 3);
    }

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