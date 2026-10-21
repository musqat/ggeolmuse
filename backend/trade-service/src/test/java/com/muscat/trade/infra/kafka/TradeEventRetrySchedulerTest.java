package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.TradeRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
  private final Trade stalePending = Trade.builder().id(3L).userId("user-1").tradeType(TradeType.BUY)
    .status(TradeStatus.PENDING).build();
  private final Trade otherPending = Trade.builder().id(4L).userId("user-1").tradeType(TradeType.BUY)
    .status(TradeStatus.PENDING).build();

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
    willThrow(new TradeException(TradeResponse.EVENT_PUBLISH_FAILED))
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

  @Test
  @DisplayName("발행한 지 5분 넘게 대기 중인 매수를 다시 보내고 발행 시각을 새로 남긴다")
  void resend_RepublishesStalePendingBuys() {
    given(tradeRepository.findTop100ByTradeTypeAndStatusAndEventPublishedAtBeforeOrderByIdAsc(
      eq(TradeType.BUY), eq(TradeStatus.PENDING), any())).willReturn(List.of(stalePending, otherPending));

    LocalDateTime before = LocalDateTime.now();
    scheduler.resend();

    verify(tradeEventProducer).publishTradeCompleted(stalePending);
    verify(tradeEventProducer).publishTradeCompleted(otherPending);
    verify(tradePublishRecorder).recordPublished(3L);
    verify(tradePublishRecorder).recordPublished(4L);
    ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(tradeRepository).findTop100ByTradeTypeAndStatusAndEventPublishedAtBeforeOrderByIdAsc(
      eq(TradeType.BUY), eq(TradeStatus.PENDING), cutoff.capture());
    assertThat(cutoff.getValue()).isBetween(before.minusMinutes(5).minusSeconds(5), before.minusMinutes(5).plusSeconds(5));
  }

  @Test
  @DisplayName("오래된 대기 매수 하나가 실패하면 뒤의 것은 다음 회차로 넘긴다")
  void resend_StalePendingFailure_StopsRound() {
    given(tradeRepository.findTop100ByTradeTypeAndStatusAndEventPublishedAtBeforeOrderByIdAsc(
      eq(TradeType.BUY), eq(TradeStatus.PENDING), any())).willReturn(List.of(stalePending, otherPending));
    willThrow(new TradeException(TradeResponse.EVENT_PUBLISH_FAILED))
      .given(tradeEventProducer).publishTradeCompleted(stalePending);

    scheduler.resend();

    verify(tradeEventProducer, never()).publishTradeCompleted(otherPending);
    verify(tradePublishRecorder, never()).recordPublished(any());
  }

  @Test
  @DisplayName("못 보낸 체결이 실패한 회차에는 대기 매수도 다시 보내지 않는다")
  void resend_UnpublishedFailure_SkipsStalePending() {
    given(tradeRepository.findTop100ByEventPublishedAtIsNullAndCreatedAtBeforeOrderByIdAsc(any()))
      .willReturn(List.of(first));
    willThrow(new TradeException(TradeResponse.EVENT_PUBLISH_FAILED))
      .given(tradeEventProducer).publishTradeCompleted(first);

    scheduler.resend();

    verify(tradeRepository, never())
      .findTop100ByTradeTypeAndStatusAndEventPublishedAtBeforeOrderByIdAsc(any(), any(), any());
  }

  @Test
  @DisplayName("5분 넘게 대기 중인 매수 수를 지표로 낸다")
  void resend_PendingGauge_CountsOldPendingBuys() {
    given(tradeRepository.countByTradeTypeAndStatusAndCreatedAtBefore(
      eq(TradeType.BUY), eq(TradeStatus.PENDING), any())).willReturn(3L);

    LocalDateTime before = LocalDateTime.now();
    scheduler.resend();

    assertThat(meterRegistry.get("trade.buys.pending").gauge().value()).isEqualTo(3.0);
    ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(tradeRepository).countByTradeTypeAndStatusAndCreatedAtBefore(
      eq(TradeType.BUY), eq(TradeStatus.PENDING), cutoff.capture());
    assertThat(cutoff.getValue()).isBetween(before.minusMinutes(5).minusSeconds(5), before.minusMinutes(5).plusSeconds(5));
  }
}
