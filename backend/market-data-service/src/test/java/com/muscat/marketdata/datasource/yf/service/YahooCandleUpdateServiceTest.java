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

  @Test
  @DisplayName("야후의 긴 소수를 8자리로 반올림하면 저장값과 같다 → 다시 쓰지 않는다")
  void 반올림하면_같은_값은_그대로() {
    Candle storedD1 = candle(D1, "124.80750275");
    stub(FROM, List.of(storedD1), List.of(candle(D1, "124.80750274658203")));

    service.saveCandles(SYMBOL, FROM, TO);

    verify(collectionStats).recordSymbol(0, 0);
    assertThat(storedD1.getClose()).isEqualTo(new BigDecimal("124.80750275"));
  }

  @Test
  @DisplayName("8자리로 반올림해도 다르면 고친다")
  void 반올림해도_다르면_고치기() {
    Candle storedD1 = candle(D1, "124.80750275");
    stub(FROM, List.of(storedD1), List.of(candle(D1, "124.81")));

    service.saveCandles(SYMBOL, FROM, TO);

    verify(collectionStats).recordSymbol(0, 1);
    assertThat(storedD1.getClose()).isEqualByComparingTo("124.81");
  }

  private static Candle candleWithAdjustedClose(LocalDate date, String close, String adjustedClose) {
    Candle candle = candle(date, close);
    candle.setAdjustedClose(new BigDecimal(adjustedClose));
    return candle;
  }

  @Test
  @DisplayName("adjclose 가 응답 서버마다 백만분의 1 안팎으로 달라도 다시 쓰지 않는다")
  void adjclose_잡음은_그대로() {
    // APH 2002-06-27 을 query1 · query2 로 받은 두 값. 상대 차이 1.4e-6
    Candle storedD1 = candleWithAdjustedClose(D1, "0.62", "0.50810856");
    stub(FROM, List.of(storedD1), List.of(candleWithAdjustedClose(D1, "0.62", "0.5081092715263367")));

    service.saveCandles(SYMBOL, FROM, TO);

    verify(collectionStats).recordSymbol(0, 0);
    assertThat(storedD1.getAdjustedClose()).isEqualByComparingTo("0.50810856");
  }

  @Test
  @DisplayName("배당이 반영돼 adjclose 가 바뀌면 고친다")
  void adjclose_배당_반영은_고치기() {
    // 상대 차이 2.7e-4. 84종목 배당 360건 중 한 번에 가장 작게 바뀐 비율과 같은 크기다
    Candle storedD1 = candleWithAdjustedClose(D1, "50", "49.00000000");
    stub(FROM, List.of(storedD1), List.of(candleWithAdjustedClose(D1, "50", "48.987")));

    service.saveCandles(SYMBOL, FROM, TO);

    verify(collectionStats).recordSymbol(0, 1);
    assertThat(storedD1.getAdjustedClose()).isEqualByComparingTo("48.987");
  }

  @Test
  @DisplayName("아주 작은 adjclose 는 소수 8자리로 반올림해 같으면 다시 쓰지 않는다")
  void 작은_adjclose는_반올림으로_비교() {
    // 저장 칸이 소수 8자리라 1e-4 크기 값은 반올림만으로도 상대 차이가 1e-5 를 넘는다
    Candle storedD1 = candleWithAdjustedClose(D1, "0.0002", "0.00012345");
    stub(FROM, List.of(storedD1), List.of(candleWithAdjustedClose(D1, "0.0002", "0.000123454")));

    service.saveCandles(SYMBOL, FROM, TO);

    verify(collectionStats).recordSymbol(0, 0);
  }

  @Test
  @DisplayName("1970 부터 받기가 성공하면 받은 봉 중 가장 최근 날짜를 asset 에 남긴다")
  void 전_기간_받기면_최신_봉_날짜를_기록() {
    stub(FULL_FROM, List.of(), List.of(candle(D1, "100"), candle(D2, "101")));

    service.saveCandles(SYMBOL, FULL_FROM, TO);

    assertThat(asset.getFullHistoryThrough()).isEqualTo(D2);
  }

  @Test
  @DisplayName("저장된 최신 날짜가 받은 봉보다 뒤여도 전 기간 기록은 남긴다")
  void 최신_날짜가_더_뒤여도_기록() {
    asset.setLatestDate(LocalDate.of(2026, 9, 11));
    stub(FULL_FROM, List.of(), List.of(candle(D1, "100"), candle(D2, "101")));

    service.saveCandles(SYMBOL, FULL_FROM, TO);

    assertThat(asset.getFullHistoryThrough()).isEqualTo(D2);
    assertThat(asset.getLatestDate()).isEqualTo(LocalDate.of(2026, 9, 11));
  }

  @Test
  @DisplayName("일부 기간 받기는 전 기간 기록을 바꾸지 않는다")
  void 일부_기간은_기록하지_않는다() {
    stub(FROM, List.of(), List.of(candle(D1, "100")));

    service.saveCandles(SYMBOL, FROM, TO);

    assertThat(asset.getFullHistoryThrough()).isNull();
  }

  @Test
  @DisplayName("빈 응답은 전 기간 기록을 바꾸지 않는다")
  void 빈_응답은_기록하지_않는다() {
    stub(FULL_FROM, List.of(), List.of());

    service.saveCandles(SYMBOL, FULL_FROM, TO);

    assertThat(asset.getFullHistoryThrough()).isNull();
  }
}
