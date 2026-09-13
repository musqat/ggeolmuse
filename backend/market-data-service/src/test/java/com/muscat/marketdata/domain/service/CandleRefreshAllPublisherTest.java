package com.muscat.marketdata.domain.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("전체 다시 받기 발행")
class CandleRefreshAllPublisherTest {

  private static final LocalDate FROM = LocalDate.of(1970, 1, 1);
  private static final LocalDate TO = LocalDate.of(2026, 9, 11);

  @Mock private AssetEventProducer assetEventProducer;

  @InjectMocks private CandleRefreshAllPublisher publisher;

  @Test
  @DisplayName("받은 종목마다 기간을 붙여 수집 이벤트를 발행한다")
  void 종목마다_발행() {
    Asset aapl = Asset.builder().symbol("AAPL").name("AAPL").build();
    Asset nvda = Asset.builder().symbol("NVDA").name("NVDA").build();

    publisher.publish(List.of(aapl, nvda), FROM, TO);

    verify(assetEventProducer).publishAssetCreated(aapl, true, FROM, TO, false);
    verify(assetEventProducer).publishAssetCreated(nvda, true, FROM, TO, false);
    verifyNoMoreInteractions(assetEventProducer);
  }
}
