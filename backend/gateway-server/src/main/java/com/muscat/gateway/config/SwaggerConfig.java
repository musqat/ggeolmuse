package com.muscat.gateway.config;

import java.util.ArrayList;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Gateway Swagger 통합 설정
 * 모든 마이크로서비스의 API 문서를 Gateway에서 통합하여 제공
 * 접속 URL: http://localhost:8070/swagger-ui.html - 서비스별 API
 * 문서를 드롭다운으로 선택 가능
 */
@Configuration
public class SwaggerConfig {

  @Bean
  @Primary
  public List<GroupedOpenApi> apis(RouteDefinitionLocator locator,
    @Value("${services.user.uri}") String userServiceUri,
    @Value("${services.trade.uri}") String tradeServiceUri,
    @Value("${services.market-data.uri}") String marketDataServiceUri,
    @Value("${services.backtest.uri}") String backtestServiceUri) {

    List<GroupedOpenApi> groups = new ArrayList<>();

    // User Service API 그룹
    groups.add(GroupedOpenApi.builder()
      .group("1. User Service")
      .pathsToMatch("/api/auth/**", "/api/users/**", "/api/accounts/**")
      .build());

    // Trade Service API 그룹
    groups.add(GroupedOpenApi.builder()
      .group("2. Trade Service")
      .pathsToMatch("/api/trade/**", "/api/portfolio/**", "/api/transactions/**",
        "/api/trade-history/**")
      .build());

    // Market Data Service API 그룹
    groups.add(GroupedOpenApi.builder()
      .group("3. Market Data Service")
      .pathsToMatch("/api/market/**")
      .build());

    // Backtest Service API 그룹
    groups.add(GroupedOpenApi.builder()
      .group("4. Backtest Service")
      .pathsToMatch("/api/backtest/**", "/api/analysis/**", "/api/trading-simulation/**")
      .build());

    // Admin APIs 그룹 (ADMIN 권한 필요)
    groups.add(GroupedOpenApi.builder()
      .group("5. Admin APIs")
      .pathsToMatch("/api/admin/**")
      .build());

    return groups;
  }
}
