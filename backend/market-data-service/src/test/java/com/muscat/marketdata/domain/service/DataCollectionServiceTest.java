package com.muscat.marketdata.domain.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.AssetCreatedEvent;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 종목 생성 이벤트 하나를 받아 캔들 · 배당을 수집. 실패는 다시 올려 ack 하지 않게 함
@DisplayName("종목 하나 수집")
class DataCollectionServiceTest {

  private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
  private static final LocalDate TO = LocalDate.of(2024, 3, 31);

  private final CandleUpdateService updateService = mock(CandleUpdateService.class);
  private final DataCollectionService service = new DataCollectionService(updateService);

  private static AssetCreatedEvent event(boolean includeDividends) {
    return AssetCreatedEvent.builder().symbol("AAPL").fromDate(FROM).toDate(TO).collectData(true)
      .includeDividends(includeDividends).build();
  }

  @Test
  @DisplayName("배당을 넣으면 캔들 · 배당을, 빼면 캔들만 받는다")
  void collects() {
    service.collect(event(true));
    service.collect(event(false));

    verify(updateService, times(2)).saveCandles("AAPL", FROM, TO);
    verify(updateService).saveDividends("AAPL", FROM, TO);
  }

  @Test
  @DisplayName("수집이 실패하면 같은 예외를 다시 올린다")
  void failure() {
    given(updateService.saveCandles("AAPL", FROM, TO)).willThrow(new IllegalStateException("yahoo down"));

    assertThatThrownBy(() -> service.collect(event(false))).isInstanceOf(IllegalStateException.class).hasMessage("yahoo down");
  }
}
