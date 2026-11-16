package com.muscat.trade.infra.kafka;

import com.muscat.messaging.event.StockSplitEvent;
import com.muscat.trade.domain.service.StockSplitService;
import com.muscat.trade.domain.service.StockSplitService.Outcome;
import com.muscat.trade.domain.service.StockSplitService.Split;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// 분할 이벤트를 받아 분할 전 체결 · 보유를 분할 기준으로 맞춤(실패는 재시도 뒤 DLT)
@Slf4j
@Component
@RequiredArgsConstructor
public class StockSplitEventConsumer {

  private final StockSplitService stockSplitService;

  @KafkaListener(
    topics = "market.split.detected",
    groupId = "${spring.application.name}-split-consumer",
    containerFactory = "stockSplitEventKafkaListenerContainerFactory"
  )
  public void handle(@Payload StockSplitEvent event, Acknowledgment acknowledgment) {
    log.info("분할 이벤트 수신: symbol={}, splitDate={}, ratio={}", event.getSymbol(), event.getSplitDate(),
      event.getRatio());
    Outcome outcome = stockSplitService.apply(event.getSymbol(),
      List.of(new Split(event.getSplitDate(), event.getRatio())));
    log.info("분할 이벤트 처리: symbol={}, splitDate={}, 맞춘 체결={}", event.getSymbol(), event.getSplitDate(),
      outcome.adjusted());
    acknowledgment.acknowledge();
  }
}
