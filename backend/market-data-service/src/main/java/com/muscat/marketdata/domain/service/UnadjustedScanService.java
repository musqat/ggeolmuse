package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.domain.repository.CandleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

// 분할 계수가 기록된 종목 탐색(몇 분 걸려 게이트웨이 30초 제한 밖 백그라운드 실행, 진행 중 · 마지막 결과는 파드 사이 공유)
@Slf4j
@Service
@RequiredArgsConstructor
public class UnadjustedScanService {

  static final String JOB_NAME = "unadjusted-scan";

  private final CandleRepository candleRepository;
  private final AdminJobState jobState;

  // self-injection(같은 빈에서 직접 부르면 @Async 프록시를 안 탐)
  @Lazy
  @Autowired
  private UnadjustedScanService self;

  @Getter
  @Builder
  @Jacksonized
  public static class Result {
    private final LocalDate from;
    private final int count;
    private final List<String> symbols;
    private final Instant finishedAt;
    private final long tookMillis;
    private final String error;
  }

  public boolean isRunning() {
    return jobState.isRunning(JOB_NAME);
  }

  public Result getLast() {
    return jobState.detail(JOB_NAME, Result.class).orElse(null);
  }

  /** 이미 돌고 있으면 false */
  public boolean start(LocalDate from) {
    if (!jobState.tryStart(JOB_NAME)) {
      return false;
    }
    self.scan(from);
    return true;
  }

  @Async
  public void scan(LocalDate from) {
    long begin = System.currentTimeMillis();
    log.info("[분할탐색] 시작: from={}", from);
    Result result = null;
    try {
      List<String> symbols = candleRepository.findSymbolsWithSplits(from);
      result = Result.builder()
          .from(from)
          .count(symbols.size())
          .symbols(symbols)
          .finishedAt(Instant.now())
          .tookMillis(System.currentTimeMillis() - begin)
          .build();
      log.info("[분할탐색] 완료: {}개, {}ms", symbols.size(), System.currentTimeMillis() - begin);
    } catch (Exception e) {
      log.error("[분할탐색] 실패: from={}", from, e);
      result = Result.builder()
          .from(from)
          .count(0)
          .symbols(List.of())
          .finishedAt(Instant.now())
          .tookMillis(System.currentTimeMillis() - begin)
          .error(e.getMessage())
          .build();
    } finally {
      jobState.finish(JOB_NAME, result);
    }
  }
}
