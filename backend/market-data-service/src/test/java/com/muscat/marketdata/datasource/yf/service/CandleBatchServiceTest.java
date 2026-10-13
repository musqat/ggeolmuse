package com.muscat.marketdata.datasource.yf.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.domain.dto.BatchResult;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 종목마다 최대 3번 재시도하고 실패한 종목은 세고 넘어간다. 대기 시간은 0 으로 두고 본다
@DisplayName("야후 일괄 수집")
class CandleBatchServiceTest {

  private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
  private static final LocalDate TO = LocalDate.of(2024, 3, 31);

  private final AssetRepository assetRepository = mock(AssetRepository.class);
  private final YahooCandleUpdateService updateService = mock(YahooCandleUpdateService.class);
  private final MarketDataProperties properties = new MarketDataProperties();
  private final CandleBatchService service = new CandleBatchService(assetRepository, updateService, properties);

  @BeforeEach
  void setUp() {
    properties.getBatch().setMillisPerMinute(0);
    ReflectionTestUtils.setField(service, "requestsPerMinute", 5);
    ReflectionTestUtils.setField(service, "maxRetryAttempts", 3);
    ReflectionTestUtils.setField(service, "initialBackoffMs", 0L);
    given(assetRepository.findAll()).willReturn(List.of(asset("MSFT"), asset("AAPL"), asset("AAPL")));
  }

  private static Asset asset(String symbol) {
    return Asset.builder().symbol(symbol).build();
  }

  @Test
  @DisplayName("종목 코드는 중복을 빼고 정렬한다")
  void loadSymbols() {
    assertThat(service.loadSymbols()).containsExactly("AAPL", "MSFT");
  }

  @Test
  @DisplayName("배당을 넣으면 캔들과 배당을 같이, 빼면 캔들만 받고 저장 건수를 더한다")
  void collectsAll() {
    given(updateService.saveBoth(anyString(), any(), any())).willReturn(10);
    given(updateService.saveCandles(anyString(), any(), any())).willReturn(7);

    BatchResult both = service.collectAll(FROM, TO, true);
    BatchResult candlesOnly = service.collectAll(FROM, TO, false);

    assertThat(both).isEqualTo(new BatchResult(2, 2, 0, 20, FROM, TO));
    assertThat(candlesOnly).isEqualTo(new BatchResult(2, 2, 0, 14, FROM, TO));
  }

  @Test
  @DisplayName("두 번 실패해도 세 번째에 받으면 성공이고, 세 번 다 실패하면 실패로 세고 다음 종목으로 간다")
  void retriesThenCountsFailure() {
    given(updateService.saveCandles("AAPL", FROM, TO))
      .willThrow(new IllegalStateException("429")).willThrow(new IllegalStateException("429")).willReturn(5);
    given(updateService.saveCandles("MSFT", FROM, TO)).willThrow(new IllegalStateException("down"));

    BatchResult result = service.collectAll(FROM, TO, false);

    assertThat(result).isEqualTo(new BatchResult(2, 1, 1, 5, FROM, TO));
    verify(updateService, times(3)).saveCandles("AAPL", FROM, TO);
    verify(updateService, times(3)).saveCandles("MSFT", FROM, TO);
    verify(updateService, never()).saveBoth(anyString(), any(), any());
  }
}
