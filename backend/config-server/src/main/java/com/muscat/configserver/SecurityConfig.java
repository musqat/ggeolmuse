package com.muscat.configserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  // Management port (9090)
  @Bean
  @Order(0)
  public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
    http
      .securityMatcher(request -> request.getServerPort() == 9090)
      .authorizeHttpRequests(auth -> auth
        .anyRequest().permitAll()
      )
      .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }

  // API port (8888)
  @Bean
  @Order(1)
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      .authorizeHttpRequests(authz -> authz
        .anyRequest().authenticated()
      )
      .httpBasic(basic -> {
      })
      .csrf(csrf -> csrf.disable());
    return http.build();
  }
}
