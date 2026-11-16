package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.StockSplitEvent;
import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.service.StockSplitService;
import com.muscat.trade.domain.service.StockSplitService.Outcome;
import com.muscat.trade.domain.service.StockSplitService.Split;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
@DisplayName("분할 이벤트 컨슈머")
class StockSplitEventConsumerTest {

  private static final LocalDate SPLIT_DATE = LocalDate.of(2024, 6, 10);

  @Mock
  private StockSplitService stockSplitService;

  @Mock
  private Acknowledgment acknowledgment;

  @InjectMocks
  private StockSplitEventConsumer consumer;

  private static StockSplitEvent event() {
    return StockSplitEvent.builder().symbol("NVDA").splitDate(SPLIT_DATE).ratio(BigDecimal.TEN).build();
  }

  @Test
  @DisplayName("분할 이벤트의 종목 · 분할일 · 비율로 반영한 뒤 오프셋 커밋")
  void appliesSplitAndAcknowledges() {
    given(stockSplitService.apply("NVDA", List.of(new Split(SPLIT_DATE, BigDecimal.TEN))))
      .willReturn(new Outcome(3, 0));

    consumer.handle(event(), acknowledgment);

    verify(stockSplitService).apply("NVDA", List.of(new Split(SPLIT_DATE, BigDecimal.TEN)));
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("반영이 실패하면 오프셋을 커밋하지 않고 예외")
  void failure_DoesNotAcknowledge() {
    given(stockSplitService.apply(any(), anyList()))
      .willThrow(new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR));

    assertThatThrownBy(() -> consumer.handle(event(), acknowledgment)).isInstanceOf(TradeException.class);

    verify(acknowledgment, never()).acknowledge();
  }
}
