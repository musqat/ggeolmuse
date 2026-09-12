package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.domain.repository.CandleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 시작일 이후 분할 계수가 기록된 종목 탐색. 3천만 행을 훑어 몇 분 걸려 게이트웨이 30초 제한을 넘는다.
 * 백그라운드로 돌리고 마지막 결과를 들고 있는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnadjustedScanService {

  private final CandleRepository candleRepository;

  // self-injection: 같은 빈에서 직접 부르면 @Async 프록시를 안 탄다
  @Lazy
  @Autowired
  private UnadjustedScanService self;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicReference<Result> last = new AtomicReference<>(null);

  @Getter
  @Builder
  public static class Result {
    private final LocalDate from;
    private final int count;
    private final List<String> symbols;
    private final Instant finishedAt;
    private final long tookMillis;
    private final String error;
  }

  public boolean isRunning() {
    return running.get();
  }

  public Result getLast() {
    return last.get();
  }

  /** 이미 돌고 있으면 false 를 준다. */
  public boolean start(LocalDate from) {
    if (!running.compareAndSet(false, true)) {
      return false;
    }
    self.scan(from);
    return true;
  }

  @Async
  public void scan(LocalDate from) {
    long begin = System.currentTimeMillis();
    log.info("[분할탐색] 시작: from={}", from);
    try {
      List<String> symbols = candleRepository.findSymbolsWithSplits(from);
      last.set(Result.builder()
          .from(from)
          .count(symbols.size())
          .symbols(symbols)
          .finishedAt(Instant.now())
          .tookMillis(System.currentTimeMillis() - begin)
          .build());
      log.info("[분할탐색] 완료: {}개, {}ms", symbols.size(), System.currentTimeMillis() - begin);
    } catch (Exception e) {
      log.error("[분할탐색] 실패: from={}", from, e);
      last.set(Result.builder()
          .from(from)
          .count(0)
          .symbols(List.of())
          .finishedAt(Instant.now())
          .tookMillis(System.currentTimeMillis() - begin)
          .error(e.getMessage())
          .build());
    } finally {
      running.set(false);
    }
  }
}
