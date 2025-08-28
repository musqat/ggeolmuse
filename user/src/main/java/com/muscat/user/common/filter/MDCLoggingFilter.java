package com.muscat.user.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

@Component
@Slf4j
public class MDCLoggingFilter implements Filter {

  // MDC 키
  public static final String REQUEST_ID = "rid";
  public static final String USER_ID = "userId";
  public static final String ENDPOINT = "endpoint";
  public static final String METHOD = "method";
  public static final String CLIENT_IP = "clientIp";

  // 성능 임계값
  private static final long SLOW_REQUEST_THRESHOLD = 5000L;  // 5초
  private static final long NORMAL_LOG_THRESHOLD = 1000L;    // 1초
  private static final int MAX_USER_ID_LENGTH = 10;

  // IP 헤더 목록
  private static final String[] IP_HEADER_NAMES = {
      "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP",
      "HTTP_X_FORWARDED_FOR", "HTTP_X_FORWARDED", "HTTP_FORWARDED_FOR",
      "HTTP_FORWARDED", "HTTP_CLIENT_IP"
  };

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    long startTime = System.currentTimeMillis();

    try {
      setupMDC(httpRequest);
      logRequestStart(httpRequest);
      
      chain.doFilter(request, response);
      
    } catch (Exception e) {
      log.error("MDC 필터 처리 중 오류 발생", e);
      throw e;
    } finally {
      long duration = System.currentTimeMillis() - startTime;
      logRequestEnd(httpRequest, duration);
      MDC.clear();
    }
  }

  // MDC 설정
  private void setupMDC(HttpServletRequest request) {
    MDC.put(REQUEST_ID, generateRequestId());
    MDC.put(METHOD, request.getMethod());
    MDC.put(ENDPOINT, request.getRequestURI());
    MDC.put(CLIENT_IP, extractClientIp(request));
    MDC.put(USER_ID, extractUserId().orElse("anonymous"));
  }

  // 요청 ID 생성 (8자리 영숫자)
  private String generateRequestId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
  }

  // 인증된 사용자 ID 추출
  private java.util.Optional<String> extractUserId() {
    try {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();

      if (auth == null || !auth.isAuthenticated()) {
        return java.util.Optional.empty();
      }

      if (auth.getPrincipal() instanceof Jwt jwt) {
        return extractUserIdFromJwt(jwt);
      }

      return java.util.Optional.ofNullable(auth.getName())
          .map(name -> truncateUserId(name));

    } catch (Exception e) {
      log.debug("사용자 ID 추출 실패", e);
      return java.util.Optional.empty();
    }
  }

  // JWT에서 사용자 ID 추출
  private java.util.Optional<String> extractUserIdFromJwt(Jwt jwt) {
    return Arrays.stream(new String[]{"sub", "preferred_username", "email"})
        .map(jwt::getClaimAsString)
        .filter(claim -> claim != null && !claim.isEmpty())
        .map(this::processUserIdClaim)
        .findFirst();
  }

  // 사용자 ID 클레임 처리
  private String processUserIdClaim(String claim) {
    if (claim.contains("@")) {
      return claim.split("@")[0];
    }
    return truncateUserId(claim);
  }

  // 사용자 ID 길이 제한
  private String truncateUserId(String userId) {
    return userId.length() > MAX_USER_ID_LENGTH ? 
        userId.substring(0, MAX_USER_ID_LENGTH) : userId;
  }

  // 클라이언트 IP 추출 (프록시/로드밸런서 고려)
  private String extractClientIp(HttpServletRequest request) {
    return Arrays.stream(IP_HEADER_NAMES)
        .map(request::getHeader)
        .filter(ip -> ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip))
        .map(ip -> ip.split(",")[0].trim())
        .findFirst()
        .orElse(request.getRemoteAddr());
  }

  // 요청 시작 로그
  private void logRequestStart(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String method = request.getMethod();
    
    if (isSensitiveEndpoint(uri)) {
      log.info("요청 시작: {} {}", method, uri);
    } else {
      String fullUrl = buildFullUrl(request);
      log.info("요청 시작: {} {} - UserAgent: {}", 
          method, fullUrl, request.getHeader("User-Agent"));
    }
  }

  // 전체 URL 생성
  private String buildFullUrl(HttpServletRequest request) {
    String queryString = request.getQueryString();
    return queryString != null ? 
        request.getRequestURI() + "?" + queryString : 
        request.getRequestURI();
  }

  // 요청 완료 로그
  private void logRequestEnd(HttpServletRequest request, long duration) {
    String method = request.getMethod();
    String uri = request.getRequestURI();
    
    if (duration > SLOW_REQUEST_THRESHOLD) {
      log.warn("요청 완료 (SLOW): {} {} - 소요시간: {}ms", method, uri, duration);
    } else if (duration > NORMAL_LOG_THRESHOLD) {
      log.info("요청 완료: {} {} - 소요시간: {}ms", method, uri, duration);
    } else {
      log.debug("요청 완료: {} {} - 소요시간: {}ms", method, uri, duration);
    }
  }

  // 민감한 엔드포인트 판별
  private boolean isSensitiveEndpoint(String uri) {
    return Arrays.stream(new String[]{"/auth/", "/login", "/password", "/token"})
        .anyMatch(uri::contains);
  }
}