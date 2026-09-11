package com.muscat.marketdata.datasource.yf.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.datasource.common.MarketDataProvider.CandleSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.DividendSource;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.repository.DividendRepository;
import com.muscat.marketdata.domain.service.CollectionStats;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import com.muscat.marketdata.infra.kafka.DividendEventProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("캔들 저장 - 과거 종가 비교")
class YahooCandleUpdateServiceTest {

  private static final String SYMBOL = "AAPL";
  private static final LocalDate FULL_FROM = LocalDate.of(1970, 1, 1);
  private static final LocalDate FROM = LocalDate.of(2025, 9, 11);
  private static final LocalDate TO = LocalDate.of(2026, 9, 11);
  private static final LocalDate D1 = LocalDate.of(2025, 9, 12);
  private static final LocalDate D2 = LocalDate.of(2025, 9, 15);

  @Mock private CandleSource candleSource;
  @Mock private DividendSource dividendSource;
  @Mock private CandleRepository candleRepository;
  @Mock private DividendRepository dividendRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private DividendEventProducer dividendEventProducer;
  @Mock private CollectionStats collectionStats;
  @Mock private AssetEventProducer assetEventProducer;

  @InjectMocks
  private YahooCandleUpdateService service;

  private final Asset asset = Asset.builder().symbol(SYMBOL).name("Apple Inc.").build();

  @BeforeEach
  void setUp() {
    given(assetRepository.findById(SYMBOL)).willReturn(Optional.of(asset));
  }

  private static Candle candle(LocalDate date, String close) {
    BigDecimal price = new BigDecimal(close);
    return Candle.builder()
      .symbol(SYMBOL).date(date).currency("USD")
      .open(price).high(price).low(price).close(price).adjustedClose(price)
      .volume(1000L).dividendAmount(BigDecimal.ZERO).splitCoefficient(BigDecimal.ONE)
      .build();
  }

  private void stub(LocalDate from, List<Candle> stored, List<Candle> fetched) {
    given(candleRepository.findBySymbolAndDateBetweenOrderByDateAsc(SYMBOL, from, TO)).willReturn(stored);
    given(candleSource.fetchDailyAdjusted(SYMBOL, from, TO)).willReturn(fetched);
  }

  @Test
  @DisplayName("받는 기간 일부 · 가장 이른 날 4배 차이 · 기간 밖 데이터 있음 → 덮어쓰지 않고 전 기간 재수집을 요청한다")
  void 종가가_바뀌면_재수집() {
    Candle storedD1 = candle(D1, "400");
    stub(FROM, List.of(storedD1, candle(D2, "404")), List.of(candle(D1, "100"), candle(D2, "101")));
    given(candleRepository.existsBySymbolAndDateBefore(SYMBOL, FROM)).willReturn(true);

    service.saveCandles(SYMBOL, FROM, TO);

    verify(assetEventProducer).publishAssetCreated(asset, true, FULL_FROM, TO, false);
    verify(collectionStats).recordSplitResync();
    verify(candleRepository, never()).saveAll(any());
    assertThat(storedD1.getClose()).isEqualByComparingTo("400");
  }

  @Test
  @DisplayName("역분할 0.1배도 전 기간 재수집을 요청한다")
  void 역분할도_재수집() {
    stub(FROM, List.of(candle(D1, "1")), List.of(candle(D1, "10")));
    given(candleRepository.existsBySymbolAndDateBefore(SYMBOL, FROM)).willReturn(true);

    service.saveCandles(SYMBOL, FROM, TO);

    verify(assetEventProducer).publishAssetCreated(asset, true, FULL_FROM, TO, false);
  }

  @Test
  @DisplayName("4배 차이여도 기간 밖 데이터가 없으면 덮어쓴다")
  void 기간_밖_데이터_없으면_덮어쓰기() {
    Candle storedD1 = candle(D1, "400");
    stub(FROM, List.of(storedD1), List.of(candle(D1, "100")));
    given(candleRepository.existsBySymbolAndDateBefore(SYMBOL, FROM)).willReturn(false);

    service.saveCandles(SYMBOL, FROM, TO);

    verify(assetEventProducer, never()).publishAssetCreated(any(), anyBoolean(), any(), any(), anyBoolean());
    assertThat(storedD1.getClose()).isEqualByComparingTo("100");
  }

  @Test
  @DisplayName("1.1배 차이는 분할로 보지 않고 덮어쓴다")
  void 작은_차이는_덮어쓰기() {
    Candle storedD1 = candle(D1, "110");
    stub(FROM, List.of(storedD1), List.of(candle(D1, "100")));

    service.saveCandles(SYMBOL, FROM, TO);

    verify(candleRepository, never()).existsBySymbolAndDateBefore(any(), any());
    verify(collectionStats, never()).recordSplitResync();
    assertThat(storedD1.getClose()).isEqualByComparingTo("100");
  }

  @Test
  @DisplayName("겹치는 날짜가 없으면 비교 없이 새 데이터를 넣는다")
  void 겹치는_날짜_없으면_넣기() {
    stub(FROM, List.of(), List.of(candle(D1, "100")));

    service.saveCandles(SYMBOL, FROM, TO);

    verify(candleRepository).saveAll(any());
    verify(collectionStats, never()).recordSplitResync();
  }

  @Test
  @DisplayName("1970-01-01 부터 받으면 4배 차이 · 기간 밖 데이터가 있어도 덮어쓴다")
  void 전_기간이면_덮어쓰기() {
    Candle storedD1 = candle(D1, "400");
    stub(FULL_FROM, List.of(storedD1), List.of(candle(D1, "100")));
    given(candleRepository.existsBySymbolAndDateBefore(SYMBOL, FULL_FROM)).willReturn(true);

    service.saveCandles(SYMBOL, FULL_FROM, TO);

    verify(candleRepository, never()).existsBySymbolAndDateBefore(any(), any());
    verify(collectionStats, never()).recordSplitResync();
    assertThat(storedD1.getClose()).isEqualByComparingTo("100");
  }
}
