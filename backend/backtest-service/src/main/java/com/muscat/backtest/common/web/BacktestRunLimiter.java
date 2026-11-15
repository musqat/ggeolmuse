package com.muscat.backtest.common.web;

import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.web.servlet.HandlerInterceptor;

// 동시에 도는 백테스트 수 상한(자리가 없으면 정한 시간까지 기다리고 그래도 없으면 429)
public class BacktestRunLimiter implements HandlerInterceptor {

  private final Semaphore slots;
  private final Duration wait;

  public BacktestRunLimiter(int maxConcurrentRuns, Duration wait) {
    this.slots = new Semaphore(maxConcurrentRuns);
    this.wait = wait;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!acquire()) {
      throw new BacktestException(BacktestResponse.TOO_MANY_BACKTESTS);
    }
    return true;
  }

  // 정한 시간 안에 자리를 받았는지(기다리는 중 인터럽트되면 못 받음)
  private boolean acquire() {
    try {
      return slots.tryAcquire(wait.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  // 실행이 끝나면 자리 반환(예외로 끝나도)
  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
    Exception ex) {
    slots.release();
  }
}
