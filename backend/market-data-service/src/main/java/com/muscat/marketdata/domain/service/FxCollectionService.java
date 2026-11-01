package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.datasource.common.FxDataCollector;
import com.muscat.marketdata.domain.entity.AdminJobRun;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.AdminJobRunRepository;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
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

// 관리자 환율 수집(빠진 날 채우기 · 전체 받기, 최근 날부터, 백그라운드로 하나만 실행 · 진행과 마지막 결과 보관)
@Slf4j
@Service
@RequiredArgsConstructor
public class FxCollectionService {

  static final String JOB_NAME = "fx-collect";
  // 원천이 이만큼 연속으로 값을 안 주면 멈춤(설 · 추석 연휴보다 길게)
  static final int MAX_CONSECUTIVE_FAILURES = 10;
  private static final int RECENT_MISSING = 10;
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final FxDataCollector collector;
  private final FxRateRepository fxRateRepository;
  private final AdminJobRunRepository adminJobRunRepository;
  private final MarketDataProperties properties;

  // self-injection(같은 빈에서 직접 부르면 @Async 프록시를 안 탐)
  @Lazy
  @Autowired
  private FxCollectionService self;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicReference<Progress> progress = new AtomicReference<>(null);

  public enum Mode { MISSING, ALL }

  // 진행 중이거나 마지막 실행(다시 띄운 뒤에는 기록 테이블의 끝난 시각 · 채운 수만)
  @Getter
  @Builder(toBuilder = true)
  public static class Progress {
    private final Mode mode;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final int total;
    private final int processed;
    private final int filled;
    private final int failed;
    private final LocalDate lastDate;
    private final String stoppedReason;
  }

  // 빈 날 수 · 최근 빈 날 · 진행
  public record Status(int missingCount, List<LocalDate> missingDates, boolean running, Progress progress) {
  }

  /** 이미 돌고 있으면 false */
  public boolean start(Mode mode) {
    if (!running.compareAndSet(false, true)) {
      return false;
    }
    self.collect(mode);
    return true;
  }

  @Async
  public void collect(Mode mode) {
    Progress current = Progress.builder().mode(mode).startedAt(LocalDateTime.now(KST)).build();
    progress.set(current);
    try {
      LocalDate today = LocalDate.now(KST);
      LocalDate start = properties.getFxIngest().getStartDate();
      List<LocalDate> dates = mode == Mode.MISSING
          ? collector.missingWeekdays(start, today)
          : FxDataCollector.weekdays(start, today);
      current = current.toBuilder().total(dates.size()).build();
      progress.set(current);
      log.info("[환율수집] 관리자 {} 시작: {}일", mode, dates.size());

      int consecutiveFailures = 0;
      for (LocalDate date : dates.reversed()) {
        boolean filled = fetchAndSave(date);
        consecutiveFailures = filled ? 0 : consecutiveFailures + 1;
        current = current.toBuilder()
            .processed(current.getProcessed() + 1)
            .filled(current.getFilled() + (filled ? 1 : 0))
            .failed(current.getFailed() + (filled ? 0 : 1))
            .lastDate(date)
            .build();
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
          current = current.toBuilder().stoppedReason(String.format(
              "원천이 연속 %d 영업일 환율을 주지 않아 멈춤(마지막 처리일 %s)", MAX_CONSECUTIVE_FAILURES, date)).build();
          log.warn("[환율수집] 관리자 {} 멈춤: {}", mode, current.getStoppedReason());
          break;
        }
        progress.set(current);
      }

      current = current.toBuilder().finishedAt(LocalDateTime.now(KST)).build();
      progress.set(current);
      adminJobRunRepository.save(new AdminJobRun(JOB_NAME, current.getFinishedAt(), current.getFilled()));
      log.info("[환율수집] 관리자 {} 끝: 처리 {} · 채움 {} · 실패 {}",
          mode, current.getProcessed(), current.getFilled(), current.getFailed());
    } catch (Exception e) {
      log.error("[환율수집] 관리자 {} 실패", mode, e);
      progress.set(current.toBuilder().finishedAt(LocalDateTime.now(KST)).stoppedReason(e.getMessage()).build());
    } finally {
      running.set(false);
    }
  }

  // 그날 환율을 받아 저장(받으면 true)
  private boolean fetchAndSave(LocalDate date) {
    try {
      Optional<FxRate> rate = collector.collectSingleDate(date, true);
      rate.ifPresent(fxRateRepository::save);
      return rate.isPresent();
    } catch (Exception e) {
      log.debug("[환율수집] 못 받음: date={}, error={}", date, e.getMessage());
      return false;
    }
  }

  public boolean isRunning() {
    return running.get();
  }

  public Progress lastProgress() {
    return progress.get();
  }

  public Status status() {
    List<LocalDate> missing = collector.missingWeekdays(properties.getFxIngest().getStartDate(), LocalDate.now(KST));
    Progress last = progress.get();
    if (last == null) {
      last = adminJobRunRepository.findById(JOB_NAME)
          .map(run -> Progress.builder().finishedAt(run.getLastRunAt()).filled(run.getPublished()).build())
          .orElse(null);
    }
    return new Status(missing.size(), missing.reversed().stream().limit(RECENT_MISSING).toList(), running.get(), last);
  }
}
