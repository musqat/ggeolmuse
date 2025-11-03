package com.muscat.backtest.infra.kafka;

import com.muscat.messaging.event.BacktestCompletedEvent;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Backtest 이벤트를 Kafka에 발행하는 Producer
 *
 * 백테스트 완료시 BacktestCompletedEvent를 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BacktestEventProducer {

    private static final String BACKTEST_COMPLETED_TOPIC = "backtest.completed";

    private final KafkaTemplate<String, BacktestCompletedEvent> kafkaTemplate;

    /**
     * 백테스트 완료 이벤트 발행 (성공)
     *
     * @param userId 사용자 ID
     * @param backtestId 백테스트 ID
     * @param symbol 종목 심볼
     * @param startDate 시작일
     * @param endDate 종료일
     * @param initialInvestment 초기 투자금
     * @param finalValue 최종 가치
     * @param totalReturn 총 수익
     * @param returnPercentage 수익률
     * @param strategyType 전략 타입
     * @param investmentMode 투자 모드
     * @param numberOfTrades 거래 횟수
     * @param maxDrawdown 최대 낙폭
     * @param benchmarkComparison 벤치마크 비교
     * @param executionTimeMs 실행 시간 (ms)
     */
    public void publishBacktestCompleted(String userId, String backtestId, String symbol,
                                          LocalDate startDate, LocalDate endDate,
                                          BigDecimal initialInvestment, BigDecimal finalValue,
                                          BigDecimal totalReturn, BigDecimal returnPercentage,
                                          String strategyType, String investmentMode,
                                          Integer numberOfTrades, BigDecimal maxDrawdown,
                                          BigDecimal benchmarkComparison, Long executionTimeMs) {
        String eventId = UUID.randomUUID().toString();

        // OpenTelemetry trace ID 추출
        String traceId = null;
        try {
            traceId = Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            log.debug("TraceID 추출 실패: {}", e.getMessage());
        }

        BacktestCompletedEvent event = BacktestCompletedEvent.builder()
                .eventId(eventId)
                .eventType("BACKTEST_COMPLETED")
                .timestamp(LocalDateTime.now())
                .version("1.0")
                .traceId(traceId)
                .source("backtest-service")
                // Backtest 정보
                .userId(userId)
                .backtestId(backtestId)
                .symbol(symbol)
                .startDate(startDate)
                .endDate(endDate)
                .initialInvestment(initialInvestment)
                .finalValue(finalValue)
                .totalReturn(totalReturn)
                .returnPercentage(returnPercentage)
                .strategyType(strategyType)
                .investmentMode(investmentMode)
                .numberOfTrades(numberOfTrades)
                .maxDrawdown(maxDrawdown)
                .benchmarkComparison(benchmarkComparison)
                .successful(true)
                .errorMessage(null)
                .executionTimeMs(executionTimeMs)
                .build();

        log.info("백테스트 완료 이벤트 발행 중: backtestId={}, userId={}, symbol={}, returnPercentage={}",
                backtestId, userId, symbol, returnPercentage);

        // 비동기로 Kafka에 전송
        CompletableFuture<SendResult<String, BacktestCompletedEvent>> future =
                kafkaTemplate.send(BACKTEST_COMPLETED_TOPIC, userId, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("백테스트 완료 이벤트 발행 성공: topic={}, partition={}, offset={}, backtestId={}",
                        BACKTEST_COMPLETED_TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        backtestId);
            } else {
                log.error("백테스트 완료 이벤트 발행 실패: backtestId={}, error={}",
                        backtestId, ex.getMessage(), ex);
            }
        });
    }

    /**
     * 백테스트 실패 이벤트 발행
     *
     * @param userId 사용자 ID
     * @param backtestId 백테스트 ID
     * @param symbol 종목 심볼
     * @param startDate 시작일
     * @param endDate 종료일
     * @param errorMessage 에러 메시지
     * @param executionTimeMs 실행 시간 (ms)
     */
    public void publishBacktestFailed(String userId, String backtestId, String symbol,
                                       LocalDate startDate, LocalDate endDate,
                                       String errorMessage, Long executionTimeMs) {
        String eventId = UUID.randomUUID().toString();

        // OpenTelemetry trace ID 추출
        String traceId = null;
        try {
            traceId = Span.current().getSpanContext().getTraceId();
        } catch (Exception e) {
            log.debug("TraceID 추출 실패: {}", e.getMessage());
        }

        BacktestCompletedEvent event = BacktestCompletedEvent.builder()
                .eventId(eventId)
                .eventType("BACKTEST_FAILED")
                .timestamp(LocalDateTime.now())
                .version("1.0")
                .traceId(traceId)
                .source("backtest-service")
                // Backtest 정보
                .userId(userId)
                .backtestId(backtestId)
                .symbol(symbol)
                .startDate(startDate)
                .endDate(endDate)
                .successful(false)
                .errorMessage(errorMessage)
                .executionTimeMs(executionTimeMs)
                .build();

        log.info("백테스트 실패 이벤트 발행 중: backtestId={}, userId={}, symbol={}, errorMessage={}",
                backtestId, userId, symbol, errorMessage);

        // 비동기로 Kafka에 전송
        CompletableFuture<SendResult<String, BacktestCompletedEvent>> future =
                kafkaTemplate.send(BACKTEST_COMPLETED_TOPIC, userId, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("백테스트 실패 이벤트 발행 성공: topic={}, partition={}, offset={}, backtestId={}",
                        BACKTEST_COMPLETED_TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        backtestId);
            } else {
                log.error("백테스트 실패 이벤트 발행 실패: backtestId={}, error={}",
                        backtestId, ex.getMessage(), ex);
            }
        });
    }
}
