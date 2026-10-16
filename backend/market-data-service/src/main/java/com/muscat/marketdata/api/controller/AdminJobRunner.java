package com.muscat.marketdata.api.controller;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

// 관리자가 누르는 전체 갱신. 한 번에 하나만 돌고 하나만 기다림. 넘치면 받지 않음
@Component
class AdminJobRunner {

  private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
      new ArrayBlockingQueue<>(1), job -> new Thread(job, "admin-job"));

  boolean submit(Runnable job) {
    try {
      executor.execute(job);
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
