package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.DividendReceivedEvent;
import com.muscat.user.domain.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// 배당금 수령 이벤트를 받아 사용자 계좌에 배당금 입금
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendReceivedEventConsumer {

    private final AccountService accountService;

    @KafkaListener(
            topics = "trading.dividend.received",
            groupId = "${spring.application.name}-dividend-received-consumer",
            containerFactory = "dividendReceivedEventKafkaListenerContainerFactory"
    )
    public void handleDividendReceived(
            @Payload DividendReceivedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        log.info("배당금 수령 이벤트 수신: userId={}, accountId={}, symbol={}, amount={}, partition={}, offset={}",
                event.getUserId(), event.getAccountId(), event.getSymbol(),
                event.getTotalAmount(), partition, offset);

        try {
            // 계좌에 배당금 입금
            accountService.processDividendReceivedEvent(event);

            // 수동 커밋 (처리 성공시에만)
            acknowledgment.acknowledge();

            log.info("배당금 입금 완료: userId={}, accountId={}, symbol={}, amount={}",
                    event.getUserId(), event.getAccountId(), event.getSymbol(), event.getTotalAmount());

        } catch (Exception ex) {
            log.error("배당금 입금 실패: userId={}, accountId={}, symbol={}, amount={}, error={}",
                    event.getUserId(), event.getAccountId(), event.getSymbol(),
                    event.getTotalAmount(), ex.getMessage(), ex);

            // 재시도 실패시 DLQ (Dead Letter Queue)로 이동
            throw ex;
        }
    }
}
