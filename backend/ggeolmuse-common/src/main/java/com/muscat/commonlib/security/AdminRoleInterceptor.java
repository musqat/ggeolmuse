package com.muscat.commonlib.security;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.commonlib.exception.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerInterceptor;

// 관리자 API 는 토큰의 admin 권한에 더해 user-service 의 DB 역할로 막음(역할을 내린 사용자의 남은 토큰)
@Slf4j
@RequiredArgsConstructor
public class AdminRoleInterceptor implements HandlerInterceptor {

  public static final String ADMIN_PATHS = "/api/admin/**";

  private final AdminRoleLookup adminRoleLookup;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      return true;
    }
    String keycloakId = jwt.getSubject();
    if (keycloakId == null || keycloakId.isBlank()) {
      throw new BusinessException(CommonResponse.ADMIN_ROLE_REQUIRED);
    }
    boolean admin;
    try {
      admin = adminRoleLookup.isActiveAdmin(keycloakId);
    } catch (RuntimeException e) {
      log.warn("관리자 역할 확인 실패: path={}, error={}", request.getRequestURI(), e.getMessage());
      throw new BusinessException(CommonResponse.ADMIN_ROLE_UNAVAILABLE, e);
    }
    if (!admin) {
      throw new BusinessException(CommonResponse.ADMIN_ROLE_REQUIRED);
    }
    return true;
  }
}
