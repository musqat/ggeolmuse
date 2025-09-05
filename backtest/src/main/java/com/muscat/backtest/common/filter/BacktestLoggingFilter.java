package com.muscat.backtest.common.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Enumeration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BacktestLoggingFilter extends OncePerRequestFilter {

  private static final Logger API_LOGGER = LoggerFactory.getLogger("API_LOGGER");
  private static final int MAX_PAYLOAD_LENGTH = 1000;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    // 헬스체크나 정적 리소스는 로깅 제외
    if (isSkipLogging(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    // 요청/응답 래퍼 생성
    ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
    ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

    // 시작 시간 기록
    long startTime = System.currentTimeMillis();

    // Trace ID 생성 및 MDC 설정
    String traceId = generateTraceId();
    setupMDC(wrappedRequest, traceId);

    try {
      // 요청 로깅
      logRequest(wrappedRequest, traceId);

      // 다음 필터 실행
      filterChain.doFilter(wrappedRequest, wrappedResponse);

    } finally {
      // 응답 로깅
      long duration = System.currentTimeMillis() - startTime;
      logResponse(wrappedRequest, wrappedResponse, traceId, duration);

      wrappedResponse.copyBodyToResponse();
      MDC.clear();
    }
  }

  private boolean isSkipLogging(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri.contains("/actuator") ||
        uri.contains("/health") ||
        uri.contains("/favicon.ico") ||
        uri.contains("/static") ||
        uri.contains("/css") ||
        uri.contains("/js") ||
        uri.contains("/images");
  }

  private String generateTraceId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private void setupMDC(HttpServletRequest request, String traceId) {
    MDC.put("traceId", traceId);
    MDC.put("method", request.getMethod());
    MDC.put("uri", request.getRequestURI());

    // Authorization 헤더에서 userId 추출 시도
    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      String userId = extractUserId(request);
      if (userId != null) {
        MDC.put("userId", userId);
      }
    }

    // 요청 파라미터에서 userId 추출
    String userIdParam = request.getParameter("userId");
    if (userIdParam != null) {
      MDC.put("userId", userIdParam);
    }
  }

  private String extractUserId(HttpServletRequest request) {
    // userId 헤더 확인
    String userIdHeader = request.getHeader("X-User-Id");
    if (userIdHeader != null) {
      return userIdHeader;
    }

    // userId 파라미터 확인
    String userIdParam = request.getParameter("userId");
    if (userIdParam != null) {
      return userIdParam;
    }

    // JWT 토큰에서 userId 추출 시도 (간단한 구현)
    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      try {
        // JWT 토큰의 payload 부분에서 userId 추출 (간단한 방식)
        String token = authorization.substring(7);
        String userId = parseUserIdFromToken(token);
        if (userId != null) {
          return userId;
        }
      } catch (Exception e) {
        log.debug("JWT 토큰 파싱 실패", e);
      }
    }

    return null;
  }

  private void logRequest(ContentCachingRequestWrapper request, String traceId) {
    try {
      StringBuilder logMsg = new StringBuilder();
      logMsg.append("REQUEST [").append(traceId).append("] ");
      logMsg.append(request.getMethod()).append(" ").append(request.getRequestURI());

      // Query Parameters
      String queryString = request.getQueryString();
      if (queryString != null) {
        logMsg.append("?").append(queryString);
      }

      // Headers (민감한 정보 제외)
      logMsg.append(" | Headers: ");
      Enumeration<String> headerNames = request.getHeaderNames();
      while (headerNames.hasMoreElements()) {
        String headerName = headerNames.nextElement();
        if (!isSensitiveHeader(headerName)) {
          logMsg.append(headerName).append("=").append(request.getHeader(headerName)).append(" ");
        }
      }

      // Request Body (POST/PUT 요청의 경우)
      if ("POST".equalsIgnoreCase(request.getMethod()) || "PUT".equalsIgnoreCase(
          request.getMethod())) {
        String payload = getRequestPayload(request);
        if (payload != null && !payload.isEmpty()) {
          logMsg.append(" | Body: ").append(truncatePayload(payload));
        }
      }

      API_LOGGER.info(logMsg.toString());

    } catch (Exception e) {
      log.warn("요청 로깅 중 오류 발생", e);
    }
  }

  private void logResponse(ContentCachingRequestWrapper request,
      ContentCachingResponseWrapper response,
      String traceId, long duration) {
    try {
      // MDC에 응답 정보 추가
      MDC.put("status", String.valueOf(response.getStatus()));
      MDC.put("duration", String.valueOf(duration));

      StringBuilder logMsg = new StringBuilder();
      logMsg.append("RESPONSE [").append(traceId).append("] ");
      logMsg.append(response.getStatus()).append(" ");
      logMsg.append(duration).append("ms");

      // Response Body
      String responsePayload = getResponsePayload(response);
      if (responsePayload != null && !responsePayload.isEmpty()) {
        logMsg.append(" | Body: ").append(truncatePayload(responsePayload));
      }

      // 응답 상태에 따라 로그 레벨 조정
      if (response.getStatus() >= 400) {
        API_LOGGER.error(logMsg.toString());
      } else {
        API_LOGGER.info(logMsg.toString());
      }

    } catch (Exception e) {
      log.warn("응답 로깅 중 오류 발생", e);
    }
  }

  private boolean isSensitiveHeader(String headerName) {
    return "authorization".equalsIgnoreCase(headerName) ||
        "cookie".equalsIgnoreCase(headerName) ||
        "x-auth-token".equalsIgnoreCase(headerName);
  }

  private String getRequestPayload(ContentCachingRequestWrapper request) {
    try {
      byte[] content = request.getContentAsByteArray();
      if (content.length > 0) {
        return new String(content, StandardCharsets.UTF_8);
      }
    } catch (Exception e) {
      log.debug("Request payload 추출 실패", e);
    }
    return null;
  }

  private String getResponsePayload(ContentCachingResponseWrapper response) {
    try {
      byte[] content = response.getContentAsByteArray();
      if (content.length > 0) {
        return new String(content, StandardCharsets.UTF_8);
      }
    } catch (Exception e) {
      log.debug("Response payload 추출 실패", e);
    }
    return null;
  }

  private String truncatePayload(String payload) {
    if (payload.length() > MAX_PAYLOAD_LENGTH) {
      return payload.substring(0, MAX_PAYLOAD_LENGTH) + "...[truncated]";
    }
    return payload;
  }

  private String parseUserIdFromToken(String token) {
    try {
      // JWT에서 payload 부분만 추출
      String[] parts = token.split("\\.");
      if (parts.length != 3) {
        return null;
      }

      // Base64 디코딩
      String payload = parts[1];
      byte[] decodedBytes = Base64.getUrlDecoder().decode(payload);
      String decodedPayload = new String(decodedBytes, StandardCharsets.UTF_8);

      // JSON 파싱해서 userId 추출
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode jsonNode = objectMapper.readTree(decodedPayload);

      //  JWT 클레임에서 userId 찾기
      JsonNode userIdNode = jsonNode.get("userId");
      if (userIdNode != null) {
        return userIdNode.asText();
      }

      // sub(subject) 클레임에서 userId 찾기
      JsonNode subNode = jsonNode.get("sub");
      if (subNode != null) {
        return subNode.asText();
      }

      // username 클레임에서 userId 찾기
      JsonNode usernameNode = jsonNode.get("username");
      if (usernameNode != null) {
        return usernameNode.asText();
      }

    } catch (Exception e) {
      log.debug("JWT 토큰 파싱 중 오류: {}", e.getMessage());
    }

    return null;
  }
}