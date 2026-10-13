package com.muscat.marketdata.infra.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.domain.service.DataCollectionService;
import com.muscat.messaging.event.AssetCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.kafka.support.Acknowledgment;

// 수동 커밋이다. 수집이 끝나야 ack 하고, 실패하면 ack 하지 않아 다시 받는다
@DisplayName("종목 생성 이벤트 소비")
class AssetEventConsumerTest {

  private final DataCollectionService dataCollectionService = mock(DataCollectionService.class);
  private final Acknowledgment ack = mock(Acknowledgment.class);
  private final AssetEventConsumer consumer = new AssetEventConsumer(dataCollectionService);

  private static AssetCreatedEvent event(boolean collectData, String traceId) {
    return AssetCreatedEvent.builder().symbol("AAPL").collectData(collectData).traceId(traceId).build();
  }

  @Test
  @DisplayName("수집 요청이 있으면 수집한 뒤 ack 한다")
  void collectsThenAcks() {
    AssetCreatedEvent event = event(true, "4bf92f3577b34da6a3ce929d0e0e4736");

    consumer.handleAssetCreated(event, ack);

    InOrder order = inOrder(dataCollectionService, ack);
    order.verify(dataCollectionService).collect(event);
    order.verify(ack).acknowledge();
  }

  @Test
  @DisplayName("수집 요청이 없으면 수집하지 않고 ack 만 한다")
  void noCollectJustAcks() {
    consumer.handleAssetCreated(event(false, "BATCH_COLLECTION"), ack);

    verify(dataCollectionService, never()).collect(any());
    verify(ack).acknowledge();
  }

  @Test
  @DisplayName("수집이 실패하면 예외를 그대로 올리고 ack 하지 않는다")
  void failureDoesNotAck() {
    AssetCreatedEvent event = event(true, "BATCH_COLLECTION");
    willThrow(new IllegalStateException("yahoo down")).given(dataCollectionService).collect(event);

    assertThatThrownBy(() -> consumer.handleAssetCreated(event, ack)).isInstanceOf(IllegalStateException.class);
    verify(ack, never()).acknowledge();
  }
}
