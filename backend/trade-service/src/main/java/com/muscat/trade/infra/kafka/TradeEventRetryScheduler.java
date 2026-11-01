package com.muscat.trade.infra.kafka;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.TradeRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 못 보낸 체결과 오래 대기한 매수의 체결 이벤트를 trading.trade.completed 로 다시 발행
@Slf4j
@Component
public class TradeEventRetryScheduler {

  public static final String LOCK_NAME = "trade-event-retry";

  // 커밋 직후 발행과 겹치지 않게 이만큼 지난 체결만 다시 보냄
  private static final Duration RESEND_AFTER = Duration.ofSeconds(30);
  // 이보다 오래 못 보낸 체결 수를 trade.events.unpublished 로 노출
  private static final Duration REPORT_AFTER = Duration.ofMinutes(1);
  // 발행하고 이만큼 지나도 대기인 매수는 다시 보내고, 그 수를 trade.buys.pending 으로 노출
  private static final Duration PENDING_AFTER = Duration.ofMinutes(5);

  private final TradeRepository tradeRepository;
  private final TradeEventProducer tradeEventProducer;
  private final TradePublishRecorder tradePublishRecorder;
  private final AtomicLong unpublished;
  private final AtomicLong pendingBuys;

  public TradeEventRetryScheduler(TradeRepository tradeRepository,
    TradeEventProducer tradeEventProducer, TradePublishRecorder tradePublishRecorder,
    MeterRegistry meterRegistry) {
    this.tradeRepository = tradeRepository;
    this.tradeEventProducer = tradeEventProducer;
    this.tradePublishRecorder = tradePublishRecorder;
    this.unpublished = meterRegistry.gauge("trade.events.unpublished", new AtomicLong());
    this.pendingBuys = meterRegistry.gauge("trade.buys.pending", new AtomicLong());
  }

  // 하나라도 실패하면 이번 회차 중단(브로커가 끊겼을 때 건마다 5초씩 기다리지 않게)
  // 파드 하나만(회차 사이 20초는 잠금 유지)
  @Scheduled(fixedDelay = 30_000)
  @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "5m", lockAtLeastFor = "20s")
  public void resend() {
    LocalDateTime now = LocalDateTime.now();
    boolean sent = resendAll(tradeRepository
      .findTop100ByEventPublishedAtIsNullAndCreatedAtBeforeOrderByIdAsc(now.minus(RESEND_AFTER)));
    if (sent) {
      resendAll(tradeRepository.findTop100ByTradeTypeAndStatusAndEventPublishedAtBeforeOrderByIdAsc(
        TradeType.BUY, TradeStatus.PENDING, now.minus(PENDING_AFTER)));
    }
    unpublished.set(
      tradeRepository.countByEventPublishedAtIsNullAndCreatedAtBefore(now.minus(REPORT_AFTER)));
    pendingBuys.set(tradeRepository.countByTradeTypeAndStatusAndCreatedAtBefore(
      TradeType.BUY, TradeStatus.PENDING, now.minus(PENDING_AFTER)));
  }

  // 다 보냈으면 true
  private boolean resendAll(List<Trade> trades) {
    for (Trade trade : trades) {
      try {
        tradeEventProducer.publishTradeCompleted(trade);
        tradePublishRecorder.recordPublished(trade.getId());
        log.info("체결 이벤트 다시 보냄: tradeId={}", trade.getId());
      } catch (RuntimeException e) {
        log.warn("체결 이벤트 다시 보내기 실패: tradeId={}, error={}", trade.getId(), e.getMessage());
        return false;
      }
    }
    return true;
  }
}
