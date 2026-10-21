package com.muscat.marketdata.infra.kafka;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.messaging.event.AssetCreatedEvent;
import com.muscat.messaging.kafka.EventPublishing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// Asset 생성 이벤트 발행. 수집은 컨슈머가 비동기로
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetEventProducer {

    private static final String ASSET_CREATED_TOPIC = "market.asset.created";

    private final KafkaTemplate<String, AssetCreatedEvent> assetCreatedKafkaTemplate;

    /**
     * 심볼 생성 이벤트 발행
     */
    public void publishAssetCreated(Asset asset, boolean collectData,
                                     LocalDate fromDate, LocalDate toDate,
                                     boolean includeDividends) {
        AssetCreatedEvent event = AssetCreatedEvent.builder()
                .symbol(asset.getSymbol())
                .name(asset.getName())
                .country(asset.getCountry())
                .currency(asset.getCurrency())
                .assetType(asset.getAssetType())
                .marketCap(asset.getMarketCap())
                // 수집 옵션
                .collectData(collectData)
                .fromDate(fromDate)
                .toDate(toDate)
                .includeDividends(includeDividends)
                .build();
        EventPublishing.stamp(event, "ASSET_CREATED", "market-data-service");

        log.debug("종목 생성 이벤트 발행 중: symbol={}, collectData={}",
                asset.getSymbol(), collectData);

        // 트랜잭션 안이면 커밋 뒤에 전송. 먼저 보내면 수집 컨슈머가 빈 종목을 봄
        EventPublishing.sendAfterCommit(assetCreatedKafkaTemplate, ASSET_CREATED_TOPIC, event.getSymbol(), event);
    }
}
