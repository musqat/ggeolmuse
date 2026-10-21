package com.muscat.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.BaseEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DisplayName("이벤트 머리 채우기와 Kafka 전송")
class EventPublishingTest {

    private static final String TOPIC = "user.account.deleted";
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, SampleEvent> template = mock(KafkaTemplate.class);

    private final SampleEvent event = new SampleEvent();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        Thread.interrupted();
    }

    @Test
    @DisplayName("머리 여섯 칸을 채우고, 추적 중이 아니면 추적 id 를 비운다")
    void stamp_FillsHeader() {
        SampleEvent stamped = EventPublishing.stamp(event, "ACCOUNT_DELETED", "user-service");

        assertThat(stamped).isSameAs(event);
        assertThat(event.getEventId()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(event.getEventType()).isEqualTo("ACCOUNT_DELETED");
        assertThat(event.getTimestamp()).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
        assertThat(event.getVersion()).isEqualTo("1.0");
        assertThat(event.getSource()).isEqualTo("user-service");
        assertThat(event.getTraceId()).isNull();
    }

    @Test
    @DisplayName("요청을 추적 중이면 그 추적 id 를 싣는다")
    void stamp_CarriesActiveTraceId() {
        SpanContext context =
                SpanContext.create(TRACE_ID, "00f067aa0ba902b7", TraceFlags.getSampled(), TraceState.getDefault());

        try (Scope ignored = Span.wrap(context).makeCurrent()) {
            EventPublishing.stamp(event, "ACCOUNT_DELETED", "user-service");
        }

        assertThat(event.getTraceId()).isEqualTo(TRACE_ID);
    }

    @Test
    @DisplayName("결과를 기다리지 않고 보내며, 브로커가 받지 못해도 예외를 올리지 않는다")
    void send_FailureOnlyLogged() {
        given(template.send(anyString(), anyString(), any())).willReturn(failed());

        assertThatCode(() -> EventPublishing.send(template, TOPIC, "7", event)).doesNotThrowAnyException();

        verify(template).send(TOPIC, "7", event);
    }

    @Test
    @DisplayName("트랜잭션 밖이면 바로 보낸다")
    void sendAfterCommit_NoTransaction_SendsNow() {
        given(template.send(anyString(), anyString(), any())).willReturn(sent());

        EventPublishing.sendAfterCommit(template, TOPIC, "7", event);

        verify(template).send(TOPIC, "7", event);
    }

    @Test
    @DisplayName("트랜잭션 안이면 커밋 뒤에 보낸다")
    void sendAfterCommit_InTransaction_SendsOnlyAfterCommit() {
        given(template.send(anyString(), anyString(), any())).willReturn(sent());
        TransactionSynchronizationManager.initSynchronization();

        EventPublishing.sendAfterCommit(template, TOPIC, "7", event);
        verify(template, never()).send(anyString(), anyString(), any());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(template).send(TOPIC, "7", event);
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 보내지 않는다")
    void sendAfterCommit_RolledBack_NeverSends() {
        TransactionSynchronizationManager.initSynchronization();

        EventPublishing.sendAfterCommit(template, TOPIC, "7", event);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(template, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("커밋 뒤 전송이 바로 실패해도 예외를 올리지 않는다")
    void sendAfterCommit_SendThrows_DoesNotThrow() {
        given(template.send(anyString(), anyString(), any())).willThrow(new IllegalStateException("metadata timeout"));
        TransactionSynchronizationManager.initSynchronization();

        EventPublishing.sendAfterCommit(template, TOPIC, "7", event);

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("브로커 확인을 받으면 전송 결과를 돌려준다")
    void sendAndWait_ReturnsResult() {
        CompletableFuture<SendResult<String, SampleEvent>> future = sent();
        given(template.send(anyString(), anyString(), any())).willReturn(future);

        SendResult<String, SampleEvent> result =
                EventPublishing.sendAndWait(template, TOPIC, "7", event, Duration.ofSeconds(5), PublishFailed::new);

        assertThat(result).isSameAs(future.join());
    }

    @Test
    @DisplayName("브로커가 받지 못하면 넘긴 예외로 바꿔 올린다")
    void sendAndWait_Failure_ThrowsMapped() {
        given(template.send(anyString(), anyString(), any())).willReturn(failed());

        assertThatThrownBy(() ->
                EventPublishing.sendAndWait(template, TOPIC, "7", event, Duration.ofSeconds(5), PublishFailed::new))
                .isInstanceOf(PublishFailed.class)
                .hasRootCauseMessage("broker down");
    }

    @Test
    @DisplayName("시간 안에 확인이 없으면 넘긴 예외로 바꿔 올린다")
    void sendAndWait_Timeout_ThrowsMapped() {
        given(template.send(anyString(), anyString(), any())).willReturn(new CompletableFuture<>());

        assertThatThrownBy(() ->
                EventPublishing.sendAndWait(template, TOPIC, "7", event, Duration.ofMillis(20), PublishFailed::new))
                .isInstanceOf(PublishFailed.class)
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    @DisplayName("기다리다 인터럽트되면 인터럽트 표시를 되살리고 넘긴 예외로 올린다")
    void sendAndWait_Interrupted_RestoresFlag() {
        given(template.send(anyString(), anyString(), any())).willReturn(interrupted());

        assertThatThrownBy(() ->
                EventPublishing.sendAndWait(template, TOPIC, "7", event, Duration.ofSeconds(5), PublishFailed::new))
                .isInstanceOf(PublishFailed.class)
                .hasCauseInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    private static CompletableFuture<SendResult<String, SampleEvent>> sent() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(TOPIC, 0), 0L, 0, 0L, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(null, metadata));
    }

    private static CompletableFuture<SendResult<String, SampleEvent>> failed() {
        return CompletableFuture.failedFuture(new RuntimeException("broker down"));
    }

    private static CompletableFuture<SendResult<String, SampleEvent>> interrupted() {
        return new CompletableFuture<>() {
            @Override
            public SendResult<String, SampleEvent> get(long timeout, TimeUnit unit) throws InterruptedException {
                throw new InterruptedException();
            }
        };
    }

    // 머리 칸만 있는 이벤트
    static class SampleEvent extends BaseEvent {
    }

    static class PublishFailed extends RuntimeException {
        PublishFailed(Throwable cause) {
            super(cause);
        }
    }
}
