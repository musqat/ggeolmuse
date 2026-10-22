package com.muscat.marketdata.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.messaging.event.AssetCreatedEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.PageRequest;

// 종목 생성 이벤트 하나를 받아 캔들 · 배당을 수집. 실패는 다시 올려 ack 하지 않게 함
@DisplayName("종목 하나 수집")
class DataCollectionServiceTest {

  private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
  private static final LocalDate TO = LocalDate.of(2024, 3, 31);

  private final CandleUpdateService updateService = mock(CandleUpdateService.class);
  private final AssetRepository assetRepository = mock(AssetRepository.class);
  private final CandleRepository candleRepository = mock(CandleRepository.class);
  private final MarketDataProperties properties = new MarketDataProperties();
  private final CandleService candleService = mock(CandleService.class);
  private final DataCollectionService service =
    new DataCollectionService(updateService, assetRepository, candleRepository, properties, candleService);

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

  private static Asset asset(String type) {
    return Asset.builder().symbol("AAPL").name("AAPL").country("US").currency("USD").assetType(type).build();
  }

  private static List<Candle> candles(long volume) {
    return List.of(Candle.builder().symbol("AAPL").volume(volume).build(), Candle.builder().symbol("AAPL").volume(volume).build());
  }

  @Test
  @DisplayName("캔들을 처음 받은 ETF 의 최근 평균 거래량이 기준 아래면 비활성으로 돌리고, 돌린 뒤에 캔들 캐시를 비운다")
  void thinEtfDeactivated() {
    Asset etf = asset("ETF");
    given(candleRepository.findFirstBySymbolOrderByDateDesc("AAPL")).willReturn(Optional.empty());
    given(updateService.saveCandles("AAPL", FROM, TO)).willReturn(2);
    given(assetRepository.findById("AAPL")).willReturn(Optional.of(etf));
    given(candleRepository.findBySymbolOrderByDateDesc("AAPL", PageRequest.of(0, 20))).willReturn(candles(100L));

    service.collect(event(false));

    assertThat(etf.getActive()).isFalse();
    InOrder order = inOrder(assetRepository, candleService);
    order.verify(assetRepository).save(etf);
    order.verify(candleService).evictCandleCaches();
  }

  @Test
  @DisplayName("기준 위 ETF 와 주식은 그대로 둔다")
  void activeEtfAndStockKept() {
    Asset etf = asset("ETF");
    Asset stock = asset("EQUITY");
    given(updateService.saveCandles("AAPL", FROM, TO)).willReturn(2);
    given(assetRepository.findById("AAPL")).willReturn(Optional.of(etf)).willReturn(Optional.of(stock));
    given(candleRepository.findBySymbolOrderByDateDesc("AAPL", PageRequest.of(0, 20))).willReturn(candles(50_000L)).willReturn(candles(100L));

    service.collect(event(false));
    service.collect(event(false));

    assertThat(etf.getActive()).isTrue();
    assertThat(stock.getActive()).isTrue();
    verify(assetRepository, never()).save(any());
  }

  @Test
  @DisplayName("캔들이 이미 있는 ETF 는 갱신 수집에서 거래량이 적어도 그대로 둔다")
  void refreshKeepsThinEtf() {
    Asset etf = asset("ETF");
    given(candleRepository.findFirstBySymbolOrderByDateDesc("AAPL"))
      .willReturn(Optional.of(Candle.builder().symbol("AAPL").volume(100L).build()));
    given(updateService.saveCandles("AAPL", FROM, TO)).willReturn(2);
    given(assetRepository.findById("AAPL")).willReturn(Optional.of(etf));
    given(candleRepository.findBySymbolOrderByDateDesc("AAPL", PageRequest.of(0, 20))).willReturn(candles(100L));

    service.collect(event(false));

    assertThat(etf.getActive()).isTrue();
    verify(assetRepository, never()).save(any());
  }

  @Test
  @DisplayName("거래량이 빈 봉은 평균에서 뺀다")
  void emptyVolumesSkipped() {
    Asset etf = asset("ETF");
    given(updateService.saveCandles("AAPL", FROM, TO)).willReturn(3);
    given(assetRepository.findById("AAPL")).willReturn(Optional.of(etf));
    given(candleRepository.findBySymbolOrderByDateDesc("AAPL", PageRequest.of(0, 20))).willReturn(List.of(
      Candle.builder().symbol("AAPL").volume(15_000L).build(),
      Candle.builder().symbol("AAPL").build(),
      Candle.builder().symbol("AAPL").build()));

    service.collect(event(false));

    assertThat(etf.getActive()).isTrue();
    verify(assetRepository, never()).save(any());
  }

  @Test
  @DisplayName("캔들을 저장했으면 캔들 캐시를 비우고, 저장한 게 없으면 그대로 둔다")
  void evictsCandleCachesWhenCandlesSaved() {
    given(updateService.saveCandles("AAPL", FROM, TO)).willReturn(2).willReturn(0);
    given(assetRepository.findById("AAPL")).willReturn(Optional.of(asset("EQUITY")));

    service.collect(event(false));
    service.collect(event(false));

    verify(candleService, times(1)).evictCandleCaches();
  }

  @Test
  @DisplayName("캔들은 그대로이고 배당만 저장해도 캔들 캐시를 비운다")
  void evictsCandleCachesWhenOnlyDividendsSaved() {
    given(updateService.saveCandles("AAPL", FROM, TO)).willReturn(0);
    given(updateService.saveDividends("AAPL", FROM, TO)).willReturn(3);

    service.collect(event(true));

    verify(candleService, times(1)).evictCandleCaches();
  }
}
