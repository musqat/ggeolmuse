package com.muscat.marketdata.domain.service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 수집 결과를 모아 1분마다 한 줄로 남기고 같은 값을 지표로 내는 집계(종목마다 찍으면 긴 수집의 앞부분이 로그 회전으로 사라짐)
@Slf4j
@Component
public class CollectionStats {

  private static final long REPORT_INTERVAL_MILLIS = 60_000;

  private final AtomicLong symbolsOk = new AtomicLong();
  private final AtomicLong symbolsEmpty = new AtomicLong();
  private final AtomicLong symbolsFailed = new AtomicLong();
  private final AtomicLong splitResyncs = new AtomicLong();

  private final AtomicLong candlesInserted = new AtomicLong();
  private final AtomicLong candlesUpdated = new AtomicLong();
  private final AtomicLong dividendsSaved = new AtomicLong();

  private final AtomicLong requestsNotFound = new AtomicLong();
  private final AtomicLong requestsHttpError = new AtomicLong();
  private final AtomicLong requestsUnreachable = new AtomicLong();

  private final AtomicLong startedAt = new AtomicLong();

  // 마지막으로 완료로 본 수집의 시각(epoch 초, 아직 없으면 0)
  private final AtomicLong lastCompletedEpochSeconds = new AtomicLong();

  private final Counter okCounter;
  private final Counter emptyCounter;
  private final Counter failedCounter;
  private final Counter splitResyncCounter;
  private final Counter insertedCounter;
  private final Counter updatedCounter;
  private final Counter dividendCounter;
  private final Counter notFoundCounter;
  private final Counter httpErrorCounter;
  private final Counter unreachableCounter;

  public CollectionStats(MeterRegistry registry) {
    okCounter = registry.counter("marketdata.collection.symbols", "result", "ok");
    emptyCounter = registry.counter("marketdata.collection.symbols", "result", "empty");
    failedCounter = registry.counter("marketdata.collection.symbols", "result", "failed");
    splitResyncCounter = registry.counter("marketdata.collection.symbols", "result", "split_resync");
    insertedCounter = registry.counter("marketdata.collection.candles", "change", "inserted");
    updatedCounter = registry.counter("marketdata.collection.candles", "change", "updated");
    dividendCounter = registry.counter("marketdata.collection.dividends");
    notFoundCounter = registry.counter("marketdata.collection.requests.failed", "reason", "not_found");
    httpErrorCounter = registry.counter("marketdata.collection.requests.failed", "reason", "http_error");
    unreachableCounter = registry.counter("marketdata.collection.requests.failed", "reason", "unreachable");
    Gauge.builder("marketdata.collection.last.completed", lastCompletedEpochSeconds, AtomicLong::get)
      .baseUnit("seconds")
      .description("마지막으로 끝난 시세 수집 시각(epoch 초)")
      .register(registry);
  }

  // 보고 스레드만 읽고 씀
  private long lastReportedSymbols;

  public void recordSymbol(int inserted, int updated) {
    begin();
    symbolsOk.incrementAndGet();
    candlesInserted.addAndGet(inserted);
    candlesUpdated.addAndGet(updated);
    okCounter.increment();
    insertedCounter.increment(inserted);
    updatedCounter.increment(updated);
  }

  public void recordSymbolEmpty() {
    begin();
    symbolsEmpty.incrementAndGet();
    emptyCounter.increment();
  }

  public void recordSymbolFailed() {
    begin();
    symbolsFailed.incrementAndGet();
    failedCounter.increment();
  }

  public void recordSplitResync() {
    begin();
    splitResyncs.incrementAndGet();
    splitResyncCounter.increment();
  }

  public void recordDividends(int saved) {
    dividendsSaved.addAndGet(saved);
    dividendCounter.increment(saved);
  }

  public void recordRequestNotFound() {
    requestsNotFound.incrementAndGet();
    notFoundCounter.increment();
  }

  public void recordRequestHttpError() {
    requestsHttpError.incrementAndGet();
    httpErrorCounter.increment();
  }

  public void recordRequestUnreachable() {
    requestsUnreachable.incrementAndGet();
    unreachableCounter.increment();
  }

  /**
   * 1분 동안 종목 수가 늘면 진행, 그대로면 완료로 보고 초기화
   */
  @Scheduled(fixedDelay = REPORT_INTERVAL_MILLIS)
  void report() {
    long symbols = symbolsOk.get() + symbolsEmpty.get() + symbolsFailed.get() + splitResyncs.get();
    if (symbols == 0) {
      return;
    }

    if (symbols > lastReportedSymbols) {
      log.info("{}", summary("진행", symbols));
      lastReportedSymbols = symbols;
      return;
    }

    log.info("{}", summary("완료", symbols));
    lastCompletedEpochSeconds.set(System.currentTimeMillis() / 1000);
    reset();
  }

  // 0 인 항목은 빼고 그때 일어난 것만
  private String summary(String phase, long symbols) {
    StringBuilder line = new StringBuilder("수집 ").append(phase);
    long elapsed = System.currentTimeMillis() - startedAt.get();
    append(line, "", Duration.ofMillis(elapsed).toMinutes() + "분");
    append(line, "종목", symbols);
    append(line, "성공", symbolsOk.get());
    append(line, "빈값", symbolsEmpty.get());
    append(line, "실패", symbolsFailed.get());
    append(line, "분할재수집", splitResyncs.get());
    append(line, "신규", candlesInserted.get());
    append(line, "갱신", candlesUpdated.get());
    append(line, "배당", dividendsSaved.get());
    append(line, "404", requestsNotFound.get());
    append(line, "HTTP오류", requestsHttpError.get());
    append(line, "연결실패", requestsUnreachable.get());
    return line.toString();
  }

  private static void append(StringBuilder line, String label, long value) {
    if (value > 0) {
      append(line, label, String.valueOf(value));
    }
  }

  private static void append(StringBuilder line, String label, String value) {
    line.append("  ");
    if (!label.isEmpty()) {
      line.append(label).append(' ');
    }
    line.append(value);
  }

  private void begin() {
    startedAt.compareAndSet(0, System.currentTimeMillis());
  }

  private void reset() {
    symbolsOk.set(0);
    symbolsEmpty.set(0);
    symbolsFailed.set(0);
    splitResyncs.set(0);
    candlesInserted.set(0);
    candlesUpdated.set(0);
    dividendsSaved.set(0);
    requestsNotFound.set(0);
    requestsHttpError.set(0);
    requestsUnreachable.set(0);
    startedAt.set(0);
    lastReportedSymbols = 0;
  }
}
