package com.muscat.trade.infra.kafka;

import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.TradeRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 발행 시각이 빈 체결을 trading.trade.completed 로 다시 발행
@Slf4j
@Component
public class TradeEventRetryScheduler {

  // 커밋 직후 발행과 겹치지 않게 이만큼 지난 체결만 다시 보냄
  private static final Duration RESEND_AFTER = Duration.ofSeconds(30);
  // 이보다 오래 못 보낸 체결 수를 trade.events.unpublished 로 노출
  private static final Duration REPORT_AFTER = Duration.ofMinutes(1);

  private final TradeRepository tradeRepository;
  private final TradeEventProducer tradeEventProducer;
  private final TradePublishRecorder tradePublishRecorder;
  private final AtomicLong unpublished;

  public TradeEventRetryScheduler(TradeRepository tradeRepository,
    TradeEventProducer tradeEventProducer, TradePublishRecorder tradePublishRecorder,
    MeterRegistry meterRegistry) {
    this.tradeRepository = tradeRepository;
    this.tradeEventProducer = tradeEventProducer;
    this.tradePublishRecorder = tradePublishRecorder;
    this.unpublished = meterRegistry.gauge("trade.events.unpublished", new AtomicLong());
  }

  // 하나라도 실패하면 이번 회차 중단. 브로커가 끊겼을 때 건마다 5초씩 기다리지 않게
  @Scheduled(fixedDelay = 30_000)
  public void resend() {
    LocalDateTime now = LocalDateTime.now();
    for (Trade trade : tradeRepository
      .findTop100ByEventPublishedAtIsNullAndCreatedAtBeforeOrderByIdAsc(now.minus(RESEND_AFTER))) {
      try {
        tradeEventProducer.publishTradeCompleted(trade);
        tradePublishRecorder.recordPublished(trade.getId());
        log.info("체결 이벤트 다시 보냄: tradeId={}", trade.getId());
      } catch (RuntimeException e) {
        log.warn("체결 이벤트 다시 보내기 실패: tradeId={}, error={}", trade.getId(), e.getMessage());
        break;
      }
    }
    unpublished.set(
      tradeRepository.countByEventPublishedAtIsNullAndCreatedAtBefore(now.minus(REPORT_AFTER)));
  }
}
