package com.muscat.marketdata.domain.service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 수집 결과를 모아 1분마다 한 줄로 남긴다.
 * 종목마다 로그를 찍으면 초당 수십 줄이 되어 긴 수집에서 앞부분이 회전으로 사라진다.
 */
@Slf4j
@Component
public class CollectionStats {

  private static final long REPORT_INTERVAL_MILLIS = 60_000;

  private final AtomicLong symbolsOk = new AtomicLong();
  private final AtomicLong symbolsEmpty = new AtomicLong();
  private final AtomicLong symbolsFailed = new AtomicLong();

  private final AtomicLong candlesInserted = new AtomicLong();
  private final AtomicLong candlesUpdated = new AtomicLong();
  private final AtomicLong dividendsSaved = new AtomicLong();

  private final AtomicLong requestsNotFound = new AtomicLong();
  private final AtomicLong requestsHttpError = new AtomicLong();
  private final AtomicLong requestsUnreachable = new AtomicLong();

  private final AtomicLong startedAt = new AtomicLong();

  // 보고 스레드만 읽고 쓴다
  private long lastReportedSymbols;

  public void recordSymbol(int inserted, int updated) {
    begin();
    symbolsOk.incrementAndGet();
    candlesInserted.addAndGet(inserted);
    candlesUpdated.addAndGet(updated);
  }

  public void recordSymbolEmpty() {
    begin();
    symbolsEmpty.incrementAndGet();
  }

  public void recordSymbolFailed() {
    begin();
    symbolsFailed.incrementAndGet();
  }

  public void recordDividends(int saved) {
    dividendsSaved.addAndGet(saved);
  }

  public void recordRequestNotFound() {
    requestsNotFound.incrementAndGet();
  }

  public void recordRequestHttpError() {
    requestsHttpError.incrementAndGet();
  }

  public void recordRequestUnreachable() {
    requestsUnreachable.incrementAndGet();
  }

  /**
   * 1분 동안 종목 수가 늘면 진행, 그대로면 완료로 보고 초기화한다.
   */
  @Scheduled(fixedDelay = REPORT_INTERVAL_MILLIS)
  void report() {
    long symbols = symbolsOk.get() + symbolsEmpty.get() + symbolsFailed.get();
    if (symbols == 0) {
      return;
    }

    if (symbols > lastReportedSymbols) {
      log.info("{}", summary("진행", symbols));
      lastReportedSymbols = symbols;
      return;
    }

    log.info("{}", summary("완료", symbols));
    reset();
  }

  // 0 인 항목은 빼서 그때 일어난 것만 남긴다
  private String summary(String phase, long symbols) {
    StringBuilder line = new StringBuilder("수집 ").append(phase);
    long elapsed = System.currentTimeMillis() - startedAt.get();
    append(line, "", Duration.ofMillis(elapsed).toMinutes() + "분");
    append(line, "종목", symbols);
    append(line, "성공", symbolsOk.get());
    append(line, "빈값", symbolsEmpty.get());
    append(line, "실패", symbolsFailed.get());
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
