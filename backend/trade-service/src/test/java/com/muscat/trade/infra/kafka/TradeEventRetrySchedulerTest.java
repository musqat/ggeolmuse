package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.TradeRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("못 보낸 체결 이벤트 다시 보내기")
class TradeEventRetrySchedulerTest {

  @Mock
  private TradeRepository tradeRepository;

  @Mock
  private TradeEventProducer tradeEventProducer;

  @Mock
  private TradePublishRecorder tradePublishRecorder;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final Trade first = Trade.builder().id(1L).userId("user-1").build();
  private final Trade second = Trade.builder().id(2L).userId("user-1").build();

  private TradeEventRetryScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new TradeEventRetryScheduler(
      tradeRepository, tradeEventProducer, tradePublishRecorder, meterRegistry);
  }

  @Test
  @DisplayName("못 보낸 체결을 순서대로 보내고 기록한다")
  void resend_SendsAndMarksEach() {
    given(tradeRepository.findTop100ByEventPublishedAtIsNullAndCreatedAtBeforeOrderByIdAsc(any()))
      .willReturn(List.of(first, second));

    scheduler.resend();

    verify(tradePublishRecorder).recordPublished(1L);
    verify(tradePublishRecorder).recordPublished(2L);
  }

  @Test
  @DisplayName("하나가 실패하면 뒤의 체결은 다음 회차로 넘긴다")
  void resend_StopsAtFirstFailure() {
    given(tradeRepository.findTop100ByEventPublishedAtIsNullAndCreatedAtBeforeOrderByIdAsc(any()))
      .willReturn(List.of(first, second));
    willThrow(new IllegalStateException("broker down"))
      .given(tradeEventProducer).publishTradeCompleted(first);

    scheduler.resend();

    verify(tradeEventProducer, never()).publishTradeCompleted(second);
    verify(tradePublishRecorder, never()).recordPublished(any());
  }

  @Test
  @DisplayName("1분 넘게 못 보낸 체결 수를 지표로 낸다")
  void resend_ReportsUnpublishedCount() {
    given(tradeRepository.findTop100ByEventPublishedAtIsNullAndCreatedAtBeforeOrderByIdAsc(any()))
      .willReturn(List.of());
    given(tradeRepository.countByEventPublishedAtIsNullAndCreatedAtBefore(any())).willReturn(3L);

    scheduler.resend();

    assertThat(meterRegistry.get("trade.events.unpublished").gauge().value()).isEqualTo(3.0);
  }
}
