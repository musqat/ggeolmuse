package com.muscat.trade.infra.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.event.TradeSavedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("커밋 뒤 체결 이벤트 발행")
class TradeSavedEventListenerTest {

  @Mock
  private TradeEventProducer tradeEventProducer;

  @Mock
  private TradePublishRecorder tradePublishRecorder;

  @InjectMocks
  private TradeSavedEventListener listener;

  private final Trade trade = Trade.builder().id(7L).userId("user-1").build();

  @Test
  @DisplayName("보내면 보낸 시각을 남긴다")
  void onTradeSaved_Sent_RecordsPublished() {
    listener.onTradeSaved(new TradeSavedEvent(trade));

    verify(tradeEventProducer).publishTradeCompleted(trade);
    verify(tradePublishRecorder).recordPublished(7L);
  }

  @Test
  @DisplayName("못 보내면 기록하지 않고 예외도 내지 않는다")
  void onTradeSaved_SendFails_LeavesUnpublished() {
    willThrow(new IllegalStateException("broker down"))
      .given(tradeEventProducer).publishTradeCompleted(trade);

    listener.onTradeSaved(new TradeSavedEvent(trade));

    verify(tradePublishRecorder, never()).recordPublished(any());
  }
}
