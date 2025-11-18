package com.muscat.commonlib.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 상관관계 ID (Correlation ID) 필터
 */
@Component
@Order(1)  // 가장 먼저 실행되어야 함
@Slf4j
public class CorrelationIdFilter implements Filter {

  /**
   * 상관관계 ID를 전달하는 HTTP 헤더
   */
  public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

  /**
   * MDC에 저장할 키 이름 Logback 설정에서 %X{correlationId}로 참조
   */
  public static final String CORRELATION_ID_KEY = "correlationId";

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException {

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    try {
      // 1. 헤더에서 상관관계 ID 가져오기
      String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);

      // 2. 헤더에 없으면 새로운 UUID 생성
      if (correlationId == null || correlationId.trim().isEmpty()) {
        correlationId = generateCorrelationId();
        log.debug("Generated new correlation ID: {}", correlationId);
      } else {
        log.debug("Using existing correlation ID: {}", correlationId);
      }

      // 3. MDC에 저장 (이후 모든 로그에 자동 포함됨)
      MDC.put(CORRELATION_ID_KEY, correlationId);

      // 4. 응답 헤더에도 추가 (클라이언트가 추적 가능)
      httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);

      log.trace("Correlation ID filter applied: method={}, uri={}, correlationId={}",
        httpRequest.getMethod(), httpRequest.getRequestURI(), correlationId);

      // 5. 다음 필터로 전달
      chain.doFilter(request, response);

    } finally {
      // 6. 요청 완료 후 MDC 정리 (메모리 누수 방지)
      // ThreadLocal을 사용하므로 반드시 정리해야 함
      MDC.clear();
    }
  }

  /**
   * 새로운 상관관계 ID 생성
   *
   * <p>UUID Version 4 사용 (무작위 생성)
   * 형식: 550e8400-e29b-41d4-a716-446655440000
   *
   * @return UUID 문자열
   */
  private String generateCorrelationId() {
    return UUID.randomUUID().toString();
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    log.info("CorrelationIdFilter initialized - Correlation tracking enabled");
  }

  @Override
  public void destroy() {
    log.info("CorrelationIdFilter destroyed");
  }
}
