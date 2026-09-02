package com.muscat.marketdata.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

  @Bean
  public OpenAPI marketDataServiceOpenAPI() {
    return new OpenAPI()
      .info(new Info()
        .title("GGeolmuse Market Data Service API")
        .description("주식 시세, 과거 데이터, 환율 정보 제공 서비스")
        .version("1.0.0")
        .contact(new Contact()
          .name("GGeolmuse")
          .email("hjs90561@naver.com")))
      .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
      .components(new Components()
        .addSecuritySchemes("Bearer Authentication",
          new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description("JWT 토큰을 입력하세요 ")));
  }
}
