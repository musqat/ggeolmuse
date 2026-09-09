package com.muscat.marketdata.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 처리 설정
 * 데이터 수집 작업을 병렬로 처리하기 위한 스레드풀 설정
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

  // DB 커넥션 풀(20)보다 작게 둔다. 스레드가 커넥션을 다 쥐면 새 커넥션을 못 만든다
  @Value("${marketdata.async.pool-size:6}")
  private int poolSize;

  @Override
  public Executor getAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(poolSize);
    executor.setMaxPoolSize(poolSize);
    executor.setQueueCapacity(30000);  // 대기 큐. 전 종목 재수집이 겹쳐도 넘치지 않게
    executor.setThreadNamePrefix("market-data-async-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);

    // 큐가 가득 차면 호출자 스레드에서 실행
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

    executor.initialize();
    return executor;
  }
}
