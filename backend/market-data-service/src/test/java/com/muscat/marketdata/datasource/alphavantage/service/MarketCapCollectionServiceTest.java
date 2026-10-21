package com.muscat.marketdata.datasource.alphavantage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.marketdata.common.exception.AlphaVantageException;
import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageClient;
import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageRateLimiter;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// OVERVIEW 의 MarketCapitalization 으로 시총을 채운다. 시총이 빈 종목을 배치 크기만큼씩 채운다
@DisplayName("AlphaVantage 시총 수집")
class MarketCapCollectionServiceTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final AlphaVantageClient client = mock(AlphaVantageClient.class);
  private final AssetRepository assetRepository = mock(AssetRepository.class);
  private final MarketCapCollectionService service = new MarketCapCollectionService(client, mock(AlphaVantageRateLimiter.class),
    assetRepository);

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "enabled", true);
    ReflectionTestUtils.setField(service, "batchSize", 3);
    ReflectionTestUtils.setField(service, "logInterval", 1);
  }

  private static Asset asset(String symbol) {
    return Asset.builder().symbol(symbol).build();
  }

  private void overview(String symbol, String body) throws Exception {
    given(client.get("OVERVIEW", Map.of("symbol", symbol))).willReturn(JSON.readTree(body));
  }

  // AAPL 시총 있음 · NONE 은 종목 정보 없음 · BAD 는 숫자 아님 · ZERO 는 0 · DOWN 은 호출 실패
  private void overviews() throws Exception {
    overview("AAPL", "{\"Symbol\":\"AAPL\",\"MarketCapitalization\":\"3000000000000\"}");
    overview("NONE", "{}");
    overview("BAD", "{\"Symbol\":\"BAD\",\"MarketCapitalization\":\"None\"}");
    overview("ZERO", "{\"Symbol\":\"ZERO\",\"MarketCapitalization\":\"0\"}");
    overview("NOFIELD", "{\"Symbol\":\"NOFIELD\"}");
    given(client.get("OVERVIEW", Map.of("symbol", "DOWN"))).willThrow(new AlphaVantageException("API rate limit exceeded"));
  }

  @Test
  @DisplayName("시총이 빈 종목을 배치 크기만큼만 처리하고, 받은 시총만 저장한다")
  void collectsBatch() throws Exception {
    overviews();
    Asset aapl = asset("AAPL");
    given(assetRepository.findByMarketCapIsNull()).willReturn(List.of(aapl, asset("NONE"), asset("DOWN"), asset("BAD")));

    service.collectMarketCaps();

    assertThat(aapl.getMarketCap()).isEqualTo(3_000_000_000_000L);
    verify(assetRepository).save(aapl);
    verify(assetRepository, times(1)).save(any());
    verify(client, never()).get("OVERVIEW", Map.of("symbol", "BAD"));
  }

  @Test
  @DisplayName("꺼져 있거나 시총이 빈 종목이 없으면 부르지 않는다")
  void disabledOrNothing() {
    given(assetRepository.findByMarketCapIsNull()).willReturn(List.of());

    service.collectMarketCaps();
    ReflectionTestUtils.setField(service, "enabled", false);
    service.collectMarketCaps();

    verify(assetRepository, times(1)).findByMarketCapIsNull();
    verify(client, never()).get(any(), any());
  }

  @Test
  @DisplayName("한 종목 갱신은 시총을 받으면 true, 종목이 없거나 시총이 없거나 호출이 실패하면 false")
  void updateOne() throws Exception {
    overviews();
    given(assetRepository.findById("AAPL")).willReturn(Optional.of(asset("AAPL")));
    given(assetRepository.findById("BAD")).willReturn(Optional.of(asset("BAD")));
    given(assetRepository.findById("NOFIELD")).willReturn(Optional.of(asset("NOFIELD")));
    given(assetRepository.findById("DOWN")).willReturn(Optional.of(asset("DOWN")));
    given(assetRepository.findById("GONE")).willReturn(Optional.empty());

    assertThat(service.updateMarketCap("AAPL")).isTrue();
    assertThat(service.updateMarketCap("BAD")).isFalse();
    assertThat(service.updateMarketCap("NOFIELD")).isFalse();
    assertThat(service.updateMarketCap("DOWN")).isFalse();
    assertThat(service.updateMarketCap("GONE")).isFalse();
  }

  @Test
  @DisplayName("목록 갱신은 0 보다 큰 시총만 저장하고 실패한 종목은 건너뛴다")
  void updateList() throws Exception {
    overviews();

    int updated = service.updateAllMarketCaps(List.of(asset("AAPL"), asset("ZERO"), asset("DOWN"), asset("NONE")));

    assertThat(updated).isEqualTo(1);
  }

  @Test
  @DisplayName("활성 종목 전체 갱신은 받은 것만 저장하고, 활성 종목이 없으면 부르지 않는다")
  void updateAllActive() throws Exception {
    overviews();
    given(assetRepository.findByActiveTrue()).willReturn(List.of(asset("AAPL"), asset("ZERO"), asset("DOWN")), List.of());

    service.updateAllMarketCaps();
    service.updateAllMarketCaps();

    verify(assetRepository, times(1)).save(any());
  }
}
