package com.muscat.marketdata.domain.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.infra.kafka.DataCollectionEventProducer;
import com.muscat.messaging.event.AssetCreatedEvent;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 종목 생성 이벤트 하나를 받아 수집하고 결과를 수집 결과 토픽으로 알린다. 실패는 다시 올려 ack 하지 않게 한다
@DisplayName("종목 하나 수집")
class DataCollectionServiceTest {

  private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
  private static final LocalDate TO = LocalDate.of(2024, 3, 31);

  private final CandleUpdateService updateService = mock(CandleUpdateService.class);
  private final DataCollectionEventProducer producer = mock(DataCollectionEventProducer.class);
  private final DataCollectionService service = new DataCollectionService(updateService, producer);

  private static AssetCreatedEvent event(boolean includeDividends) {
    return AssetCreatedEvent.builder().symbol("AAPL").fromDate(FROM).toDate(TO).collectData(true)
      .includeDividends(includeDividends).build();
  }

  @Test
  @DisplayName("배당을 넣으면 캔들 · 배당 건수를 따로, 빼면 캔들만 받아 완료를 알린다")
  void collects() {
    given(updateService.saveCandles("AAPL", FROM, TO)).willReturn(10);
    given(updateService.saveDividends("AAPL", FROM, TO)).willReturn(2);

    service.collect(event(true));
    service.collect(event(false));

    verify(producer).publishCompleted(eq("AAPL"), eq(FROM), eq(TO), eq(10), eq(2), anyLong());
    verify(producer).publishCompleted(eq("AAPL"), eq(FROM), eq(TO), eq(10), eq(0), anyLong());
    verify(updateService).saveDividends("AAPL", FROM, TO);
  }

  @Test
  @DisplayName("수집이 실패하면 실패를 알리고 같은 예외를 다시 올린다")
  void failure() {
    given(updateService.saveCandles("AAPL", FROM, TO)).willThrow(new IllegalStateException("yahoo down"));

    assertThatThrownBy(() -> service.collect(event(false))).isInstanceOf(IllegalStateException.class).hasMessage("yahoo down");

    verify(producer).publishFailed(eq("AAPL"), eq(FROM), eq(TO), eq("yahoo down"), anyLong());
    verify(producer, never()).publishCompleted(any(), any(), any(), anyInt(), anyInt(), anyLong());
  }
}
