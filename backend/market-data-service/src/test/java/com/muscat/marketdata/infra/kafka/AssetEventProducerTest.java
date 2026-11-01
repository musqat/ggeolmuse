package com.muscat.marketdata.infra.kafka;

import static com.muscat.marketdata.infra.kafka.KafkaSends.failed;
import static com.muscat.marketdata.infra.kafka.KafkaSends.sent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.messaging.event.AssetCreatedEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 같은 서비스의 AssetEventConsumer 가 이 토픽을 읽어 캔들 · 배당 수집
@DisplayName("종목 생성 이벤트 발행")
class AssetEventProducerTest {

  private static final String TOPIC = "market.asset.created";
  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, AssetCreatedEvent> template = mock(KafkaTemplate.class);

  private final AssetEventProducer producer = new AssetEventProducer(template);

  private final Asset asset = Asset.builder().symbol("AAPL").name("Apple Inc.").country("US").currency("USD")
    .assetType("EQUITY").marketCap(3_000_000_000_000L).build();

  private AssetCreatedEvent published() {
    ArgumentCaptor<AssetCreatedEvent> event = ArgumentCaptor.forClass(AssetCreatedEvent.class);
    verify(template).send(eq(TOPIC), eq("AAPL"), event.capture());
    return event.getValue();
  }

  @Test
  @DisplayName("종목 코드를 키로 종목 정보와 수집 범위를 보내고, 추적 중이 아니면 추적 id 를 비운다")
  void publishesAssetWithCollectRange() {
    given(template.send(anyString(), anyString(), any())).willReturn(sent(TOPIC));

    producer.publishAssetCreated(asset, true, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), true);

    AssetCreatedEvent event = published();
    assertThat(event.getEventType()).isEqualTo("ASSET_CREATED");
    assertThat(event.getSource()).isEqualTo("market-data-service");
    assertThat(event.getTraceId()).isNull();
    assertThat(event.getSymbol()).isEqualTo("AAPL");
    assertThat(event.getName()).isEqualTo("Apple Inc.");
    assertThat(event.getCountry()).isEqualTo("US");
    assertThat(event.getCurrency()).isEqualTo("USD");
    assertThat(event.getAssetType()).isEqualTo("EQUITY");
    assertThat(event.getMarketCap()).isEqualTo(3_000_000_000_000L);
    assertThat(event.isCollectData()).isTrue();
    assertThat(event.getFromDate()).isEqualTo(LocalDate.of(2024, 1, 1));
    assertThat(event.getToDate()).isEqualTo(LocalDate.of(2024, 12, 31));
    assertThat(event.isIncludeDividends()).isTrue();
    assertThat(event.getEventId()).isNotBlank();
  }

  @Test
  @DisplayName("요청을 추적 중이면 그 추적 id 를 싣는다")
  void carriesActiveTraceId() {
    given(template.send(anyString(), anyString(), any())).willReturn(sent(TOPIC));
    SpanContext context = SpanContext.create(TRACE_ID, "00f067aa0ba902b7", TraceFlags.getSampled(), TraceState.getDefault());

    try (Scope ignored = Span.wrap(context).makeCurrent()) {
      producer.publishAssetCreated(asset, false, null, null, false);
    }

    assertThat(published().getTraceId()).isEqualTo(TRACE_ID);
  }

  @Test
  @DisplayName("트랜잭션 안이면 커밋 뒤에 보낸다. 수집 컨슈머가 커밋 전에 받으면 종목이 비어 보인다")
  void inTransaction_SendsOnlyAfterCommit() {
    given(template.send(anyString(), anyString(), any())).willReturn(sent(TOPIC));
    TransactionSynchronizationManager.initSynchronization();
    try {
      producer.publishAssetCreated(asset, true, null, null, false);
      verify(template, never()).send(anyString(), anyString(), any());

      TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

      assertThat(published().getSymbol()).isEqualTo("AAPL");
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("트랜잭션이 롤백되면 보내지 않는다")
  void rolledBack_NeverSends() {
    given(template.send(anyString(), anyString(), any())).willReturn(sent(TOPIC));
    TransactionSynchronizationManager.initSynchronization();
    try {
      producer.publishAssetCreated(asset, true, null, null, false);

      TransactionSynchronizationManager.getSynchronizations()
        .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

      verify(template, never()).send(anyString(), anyString(), any());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("커밋 뒤 전송이 바로 실패해도 이미 끝난 종목 등록으로 예외를 올리지 않는다")
  void sendThrowsAfterCommit_DoesNotThrow() {
    given(template.send(anyString(), anyString(), any())).willThrow(new IllegalStateException("metadata timeout"));
    TransactionSynchronizationManager.initSynchronization();
    try {
      producer.publishAssetCreated(asset, true, null, null, false);

      assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("브로커가 받지 못해도 종목 등록 쪽으로 예외를 올리지 않는다")
  void sendFails_DoesNotThrow() {
    given(template.send(anyString(), anyString(), any())).willReturn(failed());

    assertThatCode(() -> producer.publishAssetCreated(asset, true, null, null, false)).doesNotThrowAnyException();
  }
}
