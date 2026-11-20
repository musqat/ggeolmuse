package com.muscat.commonlib.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// 관리자 쓰기 요청(/api/admin/** 의 POST · PUT · PATCH · DELETE)마다 행위자 · 대상 경로 · 결과를 한 줄로 남기는 필터
@Slf4j
@Component
// Spring Security 필터 체인(-100) 뒤라 인증된 사용자를 읽는 순서
@Order(Ordered.LOWEST_PRECEDENCE)
public class AdminAuditFilter extends OncePerRequestFilter {

  private static final String ADMIN_PREFIX = "/api/admin/";
  private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

  private final LongSupplier nanoTime;

  public AdminAuditFilter() {
    this(System::nanoTime);
  }

  AdminAuditFilter(LongSupplier nanoTime) {
    this.nanoTime = nanoTime;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !isAdminWrite(request);
  }

  // 보안 체인이 401 · 403 으로 막아 이 필터까지 오지 않은 관리자 쓰기 요청 한 줄
  public static void logRejected(HttpServletRequest request, int status) {
    if (isAdminWrite(request)) {
      log.warn("관리자 요청: actor={} {} {} status={} 보안 체인에서 막음",
        actor(SecurityContextHolder.getContext().getAuthentication()), request.getMethod(), request.getRequestURI(),
        status);
    }
  }

  private static boolean isAdminWrite(HttpServletRequest request) {
    return WRITE_METHODS.contains(request.getMethod()) && request.getRequestURI().startsWith(ADMIN_PREFIX);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
    throws ServletException, IOException {
    long start = nanoTime.getAsLong();
    int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    try {
      chain.doFilter(request, response);
      status = response.getStatus();
    } finally {
      long millis = (nanoTime.getAsLong() - start) / 1_000_000;
      String line = "관리자 요청: actor={} {} {} status={} {}ms";
      Object[] args = {actor(SecurityContextHolder.getContext().getAuthentication()), request.getMethod(),
        request.getRequestURI(), status, millis};
      if (status >= 400) {
        log.warn(line, args);
      } else {
        log.info(line, args);
      }
    }
  }

  // 토큰 sub(이메일은 로그에 남기지 않음, 토큰이 아니면 인증 이름, 인증이 없으면 anonymous)
  private static String actor(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwt) {
      return jwt.getToken().getSubject();
    }
    if (authentication != null && authentication.isAuthenticated()) {
      return authentication.getName();
    }
    return "anonymous";
  }
}
