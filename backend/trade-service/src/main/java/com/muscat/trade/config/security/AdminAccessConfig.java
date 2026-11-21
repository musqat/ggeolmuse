package com.muscat.trade.config.security;

import com.muscat.commonlib.security.AdminRoleInterceptor;
import com.muscat.trade.infra.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 관리자 API 는 토큰이 유효해도 user-service 의 DB 역할이 관리자가 아닌 사용자를 막음
@Configuration
@RequiredArgsConstructor
public class AdminAccessConfig implements WebMvcConfigurer {

  // Feign 클라이언트는 요청 때 꺼냄(MVC 설정을 만드는 중에 Feign 이 MVC 변환기를 기다리는 순환 방지)
  private final ObjectProvider<UserServiceClient> userServiceClient;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new AdminRoleInterceptor(
        keycloakId -> userServiceClient.getObject().getAdminRole(keycloakId).admin()))
      .addPathPatterns(AdminRoleInterceptor.ADMIN_PATHS);
  }
}
