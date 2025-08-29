package com.muscat.trade.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)
@Slf4j
public class MDCLoggingFilter extends OncePerRequestFilter {

  private static final String REQUEST_ID = "requestId";
  private static final String USER_ID = "userId";
  private static final String METHOD = "method";
  private static final String URI = "uri";
  private static final String REMOTE_ADDR = "remoteAddr";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                FilterChain filterChain) throws ServletException, IOException {
    
    long startTime = System.currentTimeMillis();
    
    try {
      // MDC 설정
      setupMDC(request);
      
      // 요청 로깅
      log.info("요청 시작: {} {} from {}", 
          request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
      
      // 다음 필터 실행
      filterChain.doFilter(request, response);
      
    } finally {
      // 응답 로깅
      long duration = System.currentTimeMillis() - startTime;
      log.info("요청 완료: status={}, duration={}ms", response.getStatus(), duration);
      
      // MDC 정리
      MDC.clear();
    }
  }

  private void setupMDC(HttpServletRequest request) {
    // 요청 ID 생성
    String requestId = UUID.randomUUID().toString().substring(0, 8);
    MDC.put(REQUEST_ID, requestId);
    
    // 요청 정보
    MDC.put(METHOD, request.getMethod());
    MDC.put(URI, request.getRequestURI());
    MDC.put(REMOTE_ADDR, getClientIpAddress(request));
    
    // 사용자 ID (JWT에서 추출)
    try {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        if (userId != null) {
          MDC.put(USER_ID, userId);
        }
      }
    } catch (Exception e) {
      // 인증 정보 추출 실패는 무시 (익명 요청일 수 있음)
      log.debug("사용자 ID 추출 실패: {}", e.getMessage());
    }
  }

  private String getClientIpAddress(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
      return xForwardedFor.split(",")[0].trim();
    }
    
    String xRealIp = request.getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
      return xRealIp;
    }
    
    return request.getRemoteAddr();
  }
}