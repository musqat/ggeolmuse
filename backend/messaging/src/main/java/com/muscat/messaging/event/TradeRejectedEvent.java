package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

// 잔액에 반영하지 못한 체결
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TradeRejectedEvent extends BaseEvent {

    // 체결한 사용자 ID (Keycloak UUID)
    private String userId;

    // 반영하지 못한 체결 ID
    private Long tradeId;

    // 이벤트에 실린 계좌 ID
    private Long accountId;

    // 반영 실패 사유 코드 (AccountResponse 이름)
    private String reasonCode;

    // 반영 실패 사유 메시지
    private String reasonMessage;

    // 반영하지 못한 TradeCompletedEvent 의 eventId
    private String originalEventId;
}
