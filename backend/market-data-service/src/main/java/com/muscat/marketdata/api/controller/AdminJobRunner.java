package com.muscat.marketdata.api.controller;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 관리자가 누르는 전체 갱신. 한 번에 하나만 돌고 하나만 기다림. 넘치면 받지 않음
@Slf4j
@Component
class AdminJobRunner {

  private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
      new ArrayBlockingQueue<>(1), job -> new Thread(job, "admin-job"));

  // 실패한 일은 이름과 함께 로그로 남기고 다음 일을 받음
  boolean submit(String name, Runnable job) {
    try {
      executor.execute(() -> {
        try {
          job.run();
        } catch (Exception e) {
          log.error("{} 실패", name, e);
        }
      });
      return true;
    } catch (RejectedExecutionException e) {
      return false;
    }
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }
}
