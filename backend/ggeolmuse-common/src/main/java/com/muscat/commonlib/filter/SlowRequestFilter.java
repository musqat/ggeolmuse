package com.muscat.commonlib.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// 5초 넘게 걸린 요청을 경고 한 줄로 남기는 필터
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SlowRequestFilter extends OncePerRequestFilter {

  static final long SLOW_REQUEST_MILLIS = 5_000L;

  private final LongSupplier nanoTime;

  public SlowRequestFilter() {
    this(System::nanoTime);
  }

  SlowRequestFilter(LongSupplier nanoTime) {
    this.nanoTime = nanoTime;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long start = nanoTime.getAsLong();
    try {
      chain.doFilter(request, response);
    } finally {
      long millis = (nanoTime.getAsLong() - start) / 1_000_000;
      if (millis > SLOW_REQUEST_MILLIS) {
        log.warn("느린 요청: {} {} {}ms", request.getMethod(), request.getRequestURI(), millis);
      }
    }
  }
}
