package com.muscat.messaging.kafka;

import com.muscat.messaging.event.BaseEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 이벤트 머리 채우기와 Kafka 전송 세 가지(기다리지 않음 · 커밋 뒤 · 확인까지 기다림)
@Slf4j
public final class EventPublishing {

    private static final String VERSION = "1.0";

    private EventPublishing() {
    }

    // eventId · eventType · timestamp · version · traceId · source 채움(추적 중이 아니면 traceId 없음)
    public static <E extends BaseEvent> E stamp(E event, String eventType, String source) {
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(eventType);
        event.setTimestamp(LocalDateTime.now());
        event.setVersion(VERSION);
        event.setTraceId(currentTraceId());
        event.setSource(source);
        return event;
    }

    // 브로커 확인을 기다리지 않음(실패는 로그만)
    public static <E extends BaseEvent> void send(KafkaTemplate<String, E> template, String topic, String key, E event) {
        template.send(topic, key, event).whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("{} 발행: topic={}, partition={}, offset={}, key={}", event.getEventType(), topic,
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset(), key);
            } else {
                log.error("{} 발행 실패: topic={}, key={}, eventId={}, error={}", event.getEventType(), topic, key,
                        event.getEventId(), ex.getMessage(), ex);
            }
        });
    }

    // 트랜잭션 안이면 커밋 뒤에 보냄(커밋이 끝난 뒤라 전송 실패는 로그만)
    public static <E extends BaseEvent> void sendAfterCommit(
            KafkaTemplate<String, E> template, String topic, String key, E event) {
        Runnable sending = () -> {
            try {
                send(template, topic, key, event);
            } catch (Exception e) {
                log.error("{} 발행 실패: topic={}, key={}, eventId={}, error={}", event.getEventType(), topic, key,
                        event.getEventId(), e.getMessage(), e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sending.run();
                }
            });
        } else {
            sending.run();
        }
    }

    // 브로커 확인을 timeout 까지 기다림(못 받으면 onFailure 가 만든 예외)
    public static <V> SendResult<String, V> sendAndWait(KafkaTemplate<String, V> template, String topic, String key,
            V value, Duration timeout, Function<Exception, ? extends RuntimeException> onFailure) {
        try {
            return template.send(topic, key, value).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw onFailure.apply(e);
        } catch (ExecutionException | TimeoutException e) {
            throw onFailure.apply(e);
        }
    }

    private static String currentTraceId() {
        SpanContext context = Span.current().getSpanContext();
        return context.isValid() ? context.getTraceId() : null;
    }
}
