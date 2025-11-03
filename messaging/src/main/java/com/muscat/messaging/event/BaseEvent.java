package com.muscat.messaging.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 *  Kafka 이벤트의 기본 클래스
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent implements Serializable {

    /**
     * 이벤트 인스턴스 고유 식별자
     */
    private String eventId;

    /**
     * 이벤트 타입 (예: "TRADE_COMPLETED", "PRICE_UPDATED")
     */
    private String eventType;

    /**
     * 이벤트 생성 시간
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * 스키마 버전
     */
    private String version;

    /**
     * 분산 추적을 위한 추적 ID (선택 사항)
     */
    private String traceId;

    /**
     * 이벤트를 생성한 소스 서비스
     */
    private String source;
}
