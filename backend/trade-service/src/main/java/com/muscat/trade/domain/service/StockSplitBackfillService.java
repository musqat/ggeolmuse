package com.muscat.trade.domain.service;

import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.StockSplitService.Outcome;
import com.muscat.trade.domain.service.StockSplitService.Split;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

// 체결이 있는 종목마다 첫 거래일 뒤에 난 지난 분할을 다시 판정해 반영하는 백필(평일 하루 한 번 · 관리자 시작, 백그라운드)
@Slf4j
@Service
public class StockSplitBackfillService {

  static final String LOCK_NAME = "stock-split-backfill";

  private final TradeRepository tradeRepository;
  private final MarketServiceClientWrapper marketServiceClientWrapper;
  private final StockSplitService stockSplitService;
  private final Executor executor;
  private final Clock clock;
  private final MeterRegistry meterRegistry;
  private final AtomicBoolean running = new AtomicBoolean();
  private volatile Result last;

  // 관리자 시작도 @SchedulerLock 잠금을 거치는 자기 참조
  @Lazy
  @Autowired
  private StockSplitBackfillService self;

  public StockSplitBackfillService(TradeRepository tradeRepository,
                                   MarketServiceClientWrapper marketServiceClientWrapper, StockSplitService stockSplitService,
                                   @Qualifier("applicationTaskExecutor") Executor executor, Clock clock, MeterRegistry meterRegistry) {
    this.tradeRepository = tradeRepository;
    this.marketServiceClientWrapper = marketServiceClientWrapper;
    this.stockSplitService = stockSplitService;
    this.executor = executor;
    this.clock = clock;
    this.meterRegistry = meterRegistry;
  }

  // changed 는 맞춘 체결 수, failed 는 분할 목록을 못 받았거나 반영이 실패한 종목 수, reconcileFailed 는 보내지 못한 배당 몫 재계산 요청 수
  public record Result(int changed, int failed, int reconcileFailed, LocalDateTime finishedAt) {
  }

  // 현재 파드에서 진행 중인지와 마지막 결과(아직 없으면 빈 값)
  public record Status(boolean running, Result last) {
  }

  // 실행기로 시작(현재 파드에서 이미 진행 중이면 새로 넘기지 않음)
  public Status start() {
    if (!running.get()) {
      executor.execute(self::run);
    }
    return new Status(true, last);
  }

  public Status status() {
    return new Status(running.get(), last);
  }

  // 평일 정오(서울)에 한 번, 잠금을 먼저 잡은 파드가 있으면 건너뜀
  @Scheduled(cron = "${trade.split-backfill.cron:0 0 12 * * MON-FRI}", zone = "Asia/Seoul")
  @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "30m", lockAtLeastFor = "1m")
  public void run() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    try {
      last = applyAll();
    } finally {
      running.set(false);
    }
  }

  // 종목마다 따로 반영(한 종목이 실패해도 다음 종목으로)
  Result applyAll() {
    int changed = 0;
    int failed = 0;
    int reconcileFailed = 0;
    Map<String, LocalDate> firstTradeDates = tradeRepository.findFirstTradeDateBySymbol();
    for (Map.Entry<String, LocalDate> entry : firstTradeDates.entrySet()) {
      String symbol = entry.getKey();
      try {
        List<Split> splits = marketServiceClientWrapper.getSplits(symbol, entry.getValue().plusDays(1).toString())
          .stream()
          .map(split -> new Split(LocalDate.parse(split.date()), split.ratio()))
          .toList();
        if (!splits.isEmpty()) {
          Outcome outcome = stockSplitService.apply(symbol, splits);
          changed += outcome.adjusted();
          reconcileFailed += outcome.reconcileFailed();
        }
      } catch (RuntimeException e) {
        failed++;
        log.warn("지난 분할 백필 실패: symbol={}, error={}", symbol, e.getMessage());
      }
    }
    log.info("지난 분할 백필: {} 종목, 맞춘 체결 {}, 실패 종목 {}, 재계산 요청 실패 {}", firstTradeDates.size(), changed,
      failed, reconcileFailed);
    meterRegistry.counter("trade.split.backfill.trades").increment(changed);
    meterRegistry.counter("trade.split.backfill.failures", "kind", "symbol").increment(failed);
    meterRegistry.counter("trade.split.backfill.failures", "kind", "reconcile").increment(reconcileFailed);
    return new Result(changed, failed, reconcileFailed, LocalDateTime.now(clock));
  }
}
