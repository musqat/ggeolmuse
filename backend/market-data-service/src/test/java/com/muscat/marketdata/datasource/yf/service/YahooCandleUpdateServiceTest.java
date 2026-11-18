package com.muscat.marketdata.datasource.yf.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.datasource.common.MarketDataProvider.CandleSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.DividendSource;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.service.CandleService;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.repository.DividendRepository;
import com.muscat.marketdata.domain.service.CollectionStats;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import com.muscat.marketdata.infra.kafka.DividendEventProducer;
import com.muscat.marketdata.infra.kafka.SplitEventProducer;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.junit.jupiter.api.Nested;
import org.mockito.ArgumentCaptor;

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
  @Mock private CandleService candleService;
  @Mock private SplitEventProducer splitEventProducer;

  @InjectMocks
  private YahooCandleUpdateService service;

  private final Asset asset = Asset.builder().symbol(SYMBOL).name("Apple Inc.").build();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "self", service);
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
  @DisplayName("받는 기간 일부 · 가장 이른 날 4배 차이 · 기간 밖 데이터 있음 → 덮어쓰지 않고 배당까지 전 기간 재수집을 요청한다")
  void 종가가_바뀌면_재수집() {
    Candle storedD1 = candle(D1, "400");
    stub(FROM, List.of(storedD1, candle(D2, "404")), List.of(candle(D1, "100"), candle(D2, "101")));
    given(candleRepository.existsBySymbolAndDateBefore(SYMBOL, FROM)).willReturn(true);

    service.saveCandles(SYMBOL, FROM, TO);

    verify(assetEventProducer).publishAssetCreated(asset, true, FULL_FROM, TO, true);
    verify(collectionStats).recordSplitResync();
    verify(candleRepository, never()).saveAll(any());
    assertThat(storedD1.getClose()).isEqualByComparingTo("400");
  }

  @Test
  @DisplayName("역분할 0.1배도 배당까지 전 기간 재수집을 요청한다")
  void 역분할도_재수집() {
    stub(FROM, List.of(candle(D1, "1")), List.of(candle(D1, "10")));
    given(candleRepository.existsBySymbolAndDateBefore(SYMBOL, FROM)).willReturn(true);

    service.saveCandles(SYMBOL, FROM, TO);

    verify(assetEventProducer).publishAssetCreated(asset, true, FULL_FROM, TO, true);
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
    // APH 2002-06-27 을 query1 · query2 로 받은 두 값(상대 차이 1.4e-6)
    Candle storedD1 = candleWithAdjustedClose(D1, "0.62", "0.50810856");
    stub(FROM, List.of(storedD1), List.of(candleWithAdjustedClose(D1, "0.62", "0.5081092715263367")));

    service.saveCandles(SYMBOL, FROM, TO);

    verify(collectionStats).recordSymbol(0, 0);
    assertThat(storedD1.getAdjustedClose()).isEqualByComparingTo("0.50810856");
  }

  @Test
  @DisplayName("배당이 반영돼 adjclose 가 바뀌면 고친다")
  void adjclose_배당_반영은_고치기() {
    // 상대 차이 2.7e-4(84종목 배당 360건 중 한 번에 가장 작게 바뀐 비율과 같은 크기)
    Candle storedD1 = candleWithAdjustedClose(D1, "50", "49.00000000");
    stub(FROM, List.of(storedD1), List.of(candleWithAdjustedClose(D1, "50", "48.987")));

    service.saveCandles(SYMBOL, FROM, TO);

    verify(collectionStats).recordSymbol(0, 1);
    assertThat(storedD1.getAdjustedClose()).isEqualByComparingTo("48.987");
    verify(candleService).evictOhlc(SYMBOL, List.of(D1));
  }

  @Test
  @DisplayName("새 날짜만 넣었으면 캐시를 비우지 않는다(시세 없던 날은 캐시에 없음)")
  void 새_날짜만_넣으면_캐시_그대로() {
    stub(FROM, List.of(candle(D1, "100")), List.of(candle(D1, "100"), candle(D2, "101")));

    service.saveCandles(SYMBOL, FROM, TO);

    verify(collectionStats).recordSymbol(1, 0);
    verify(candleService, never()).evictOhlc(anyString(), anyCollection());
  }

  @Test
  @DisplayName("아주 작은 adjclose 는 소수 8자리로 반올림해 같으면 다시 쓰지 않는다")
  void 작은_adjclose는_반올림으로_비교() {
    // 저장 칸이 소수 8자리라 1e-4 크기 값은 반올림만으로도 상대 차이 1e-5 초과
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

  private static Candle split(LocalDate date, String close, String coefficient) {
    Candle candle = candle(date, close);
    candle.setSplitCoefficient(new BigDecimal(coefficient));
    return candle;
  }

  @Nested
  @DisplayName("분할 이벤트")
  class SplitEventTests {

    @Test
    @DisplayName("새로 들어온 분할 봉은 분할일 · 비율로 이벤트 발행")
    void 새_분할_봉은_이벤트() {
      stub(FULL_FROM, List.of(candle(D1, "100")), List.of(candle(D1, "100"), split(D2, "101", "4")));

      service.saveCandles(SYMBOL, FULL_FROM, TO);

      verify(splitEventProducer).publishSplitDetected(SYMBOL, D2, new BigDecimal("4"));
    }

    @Test
    @DisplayName("계수 1 로 저장된 날짜에 분할이 오면 이벤트 발행")
    void 계수_1_이던_날의_분할은_이벤트() {
      stub(FULL_FROM, List.of(candle(D2, "101")), List.of(split(D2, "101", "0.1")));

      service.saveCandles(SYMBOL, FULL_FROM, TO);

      verify(splitEventProducer).publishSplitDetected(SYMBOL, D2, new BigDecimal("0.1"));
    }

    @Test
    @DisplayName("이미 같은 계수로 저장된 분할 봉은 다시 발행하지 않음")
    void 저장된_분할은_다시_내지_않음() {
      stub(FULL_FROM, List.of(split(D2, "101", "4.00000000")), List.of(split(D2, "101", "4")));

      service.saveCandles(SYMBOL, FULL_FROM, TO);

      verify(splitEventProducer, never()).publishSplitDetected(any(), any(), any());
    }

    @Test
    @DisplayName("일부 기간 받기에서 새 분할이 보이면 1.25배 아래여도 저장 없이 전 기간 재수집 요청, 분할 이벤트 없음")
    void 일부_기간의_새_분할은_재수집() {
      stub(FROM, List.of(candle(D1, "100")), List.of(candle(D1, "100"), split(D2, "101", "1.2")));
      given(candleRepository.existsBySymbolAndDateBefore(SYMBOL, FROM)).willReturn(true);

      service.saveCandles(SYMBOL, FROM, TO);

      verify(assetEventProducer).publishAssetCreated(asset, true, FULL_FROM, TO, true);
      verify(candleRepository, never()).saveAll(any());
      verify(splitEventProducer, never()).publishSplitDetected(any(), any(), any());
    }

    @Test
    @DisplayName("저장이 실패하면 이벤트 없음")
    void 저장_실패면_이벤트_없음() {
      stub(FULL_FROM, List.of(), List.of(split(D2, "101", "4")));
      given(candleRepository.saveAll(any())).willThrow(new DataAccessResourceFailureException("db down"));

      assertThat(service.saveCandles(SYMBOL, FULL_FROM, TO)).isZero();

      verify(collectionStats).recordSymbolFailed();
      verify(splitEventProducer, never()).publishSplitDetected(any(), any(), any());
    }

    @Test
    @DisplayName("저장 중 DB · Kafka 밖의 오류는 실패로 세지 않고 그대로 던진다")
    void 저장_중_코드_오류는_던짐() {
      stub(FULL_FROM, List.of(), List.of(split(D2, "101", "4")));
      given(candleRepository.saveAll(any())).willThrow(new IllegalStateException("bug"));

      assertThatThrownBy(() -> service.saveCandles(SYMBOL, FULL_FROM, TO))
        .isInstanceOf(IllegalStateException.class);

      verify(collectionStats, never()).recordSymbolFailed();
      verify(splitEventProducer, never()).publishSplitDetected(any(), any(), any());
    }

    private com.muscat.marketdata.domain.dto.DividendDto dividend(LocalDate exDate, String amount) {
      return com.muscat.marketdata.domain.dto.DividendDto.builder().symbol(SYMBOL).exDate(exDate)
        .amount(new BigDecimal(amount)).currency("USD").build();
    }

    private void givenStoredDividend(LocalDate exDate, String amount) {
      given(dividendRepository.findBySymbolAndExDate(SYMBOL, exDate)).willReturn(Optional.of(
        com.muscat.marketdata.domain.entity.Dividend.builder().symbol(SYMBOL).exDate(exDate)
          .amount(new BigDecimal(amount)).currency("USD").build()));
    }

    @Test
    @DisplayName("새 분할이면 배당을 캔들과 같이 저장하고 배당 이벤트 뒤 분할 이벤트, 분할로만 주당 금액이 바뀐 지난 배당은 배당 이벤트에서 제외")
    void 새_분할이면_배당_저장_뒤_분할_이벤트() {
      LocalDate splitOnly = D1.minusDays(60);
      LocalDate corrected = D1.minusDays(30);
      LocalDate announced = D2.plusDays(10);
      stub(FULL_FROM, List.of(candle(D1, "100")), List.of(candle(D1, "100"), split(D2, "101", "4")));
      given(dividendSource.fetchDividends(SYMBOL, FULL_FROM, TO)).willReturn(List.of(
        dividend(splitOnly, "0.06"), dividend(corrected, "0.07"), dividend(announced, "0.25")));
      givenStoredDividend(splitOnly, "0.24000000");
      givenStoredDividend(corrected, "0.24000000");
      given(dividendRepository.findBySymbolAndExDate(SYMBOL, announced)).willReturn(Optional.empty());

      assertThat(service.saveCandles(SYMBOL, FULL_FROM, TO)).isEqualTo(2);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<com.muscat.marketdata.domain.entity.Dividend>> saved = ArgumentCaptor.forClass(List.class);
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<com.muscat.marketdata.domain.entity.Dividend>> published = ArgumentCaptor.forClass(List.class);
      org.mockito.InOrder order = org.mockito.Mockito.inOrder(dividendRepository, dividendEventProducer,
        splitEventProducer);
      order.verify(dividendRepository).saveAll(saved.capture());
      order.verify(dividendEventProducer).publishBatch(published.capture());
      order.verify(splitEventProducer).publishSplitDetected(SYMBOL, D2, new BigDecimal("4"));
      assertThat(saved.getValue()).extracting(com.muscat.marketdata.domain.entity.Dividend::getExDate)
        .containsExactly(splitOnly, corrected, announced);
      assertThat(published.getValue()).extracting(com.muscat.marketdata.domain.entity.Dividend::getExDate)
        .containsExactly(corrected, announced);
      verify(collectionStats).recordDividends(3);
    }

    @Test
    @DisplayName("병합(0.1)이면 지난 배당의 주당 금액이 10배로만 바뀐 것도 분할로만 바뀐 것")
    void 병합도_분할로만_바뀐_배당은_제외() {
      LocalDate exDate = D1.minusDays(30);
      stub(FULL_FROM, List.of(), List.of(split(D2, "101", "0.1")));
      given(dividendSource.fetchDividends(SYMBOL, FULL_FROM, TO)).willReturn(List.of(dividend(exDate, "2.5")));
      givenStoredDividend(exDate, "0.25000000");

      service.saveCandles(SYMBOL, FULL_FROM, TO);

      verify(dividendRepository).saveAll(any());
      verify(dividendEventProducer, never()).publishBatch(any());
      verify(splitEventProducer).publishSplitDetected(SYMBOL, D2, new BigDecimal("0.1"));
    }

    @Test
    @DisplayName("새 분할과 같이 받는 배당이 실패하면 0 과 실패 집계, 분할 이벤트 없음")
    void 배당_받기가_실패하면_분할_이벤트_없음() {
      stub(FULL_FROM, List.of(), List.of(split(D2, "101", "4")));
      given(dividendSource.fetchDividends(SYMBOL, FULL_FROM, TO)).willThrow(new ResourceAccessException("yahoo down"));

      assertThat(service.saveCandles(SYMBOL, FULL_FROM, TO)).isZero();

      verify(collectionStats).recordSymbolFailed();
      verify(splitEventProducer, never()).publishSplitDetected(any(), any(), any());
    }

    @Test
    @DisplayName("새 분할이 없으면 캔들 저장에서 배당을 받지 않음")
    void 새_분할이_없으면_배당_안_받음() {
      stub(FULL_FROM, List.of(split(D2, "101", "4")), List.of(split(D2, "101", "4")));

      service.saveCandles(SYMBOL, FULL_FROM, TO);

      verify(dividendSource, never()).fetchDividends(any(), any(), any());
    }
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

  @Nested
  @DisplayName("배당 저장 · 실패")
  class DividendAndFailureTests {

    private com.muscat.marketdata.domain.dto.DividendDto dividend(LocalDate exDate) {
      return com.muscat.marketdata.domain.dto.DividendDto.builder().symbol(SYMBOL).exDate(exDate)
        .amount(new BigDecimal("0.24")).currency("USD").build();
    }

    @Test
    @DisplayName("캔들 받기가 실패하면 0 을 돌려주고 실패로 센다")
    void candleFailure() {
      given(candleRepository.findBySymbolAndDateBetweenOrderByDateAsc(SYMBOL, FROM, TO)).willReturn(List.of());
      given(candleSource.fetchDailyAdjusted(SYMBOL, FROM, TO)).willThrow(new ResourceAccessException("yahoo down"));

      assertThat(service.saveCandles(SYMBOL, FROM, TO)).isZero();
      verify(collectionStats).recordSymbolFailed();
    }

    @Test
    @DisplayName("캔들 받기 중 외부 장애가 아닌 오류는 실패로 세지 않고 그대로 던진다")
    void candleBug() {
      given(candleSource.fetchDailyAdjusted(SYMBOL, FROM, TO)).willThrow(new IllegalStateException("bug"));

      assertThatThrownBy(() -> service.saveCandles(SYMBOL, FROM, TO)).isInstanceOf(IllegalStateException.class);
      verify(collectionStats, never()).recordSymbolFailed();
    }

    private void givenStored(LocalDate exDate, String amount) {
      given(dividendRepository.findBySymbolAndExDate(SYMBOL, exDate)).willReturn(Optional.of(
        com.muscat.marketdata.domain.entity.Dividend.builder().symbol(SYMBOL).exDate(exDate)
          .amount(new BigDecimal(amount)).currency("USD").build()));
    }

    @Test
    @DisplayName("새 배당은 넣고 주당 금액이 바뀐 배당은 고쳐 발행 · 집계하고, 같은 배당은 건너뛴다")
    void savesNewAndChangedDividends() {
      LocalDate d3 = D2.plusDays(1);
      given(dividendSource.fetchDividends(SYMBOL, FROM, TO))
        .willReturn(List.of(dividend(D1), dividend(D2), dividend(d3)));
      givenStored(D1, "0.24000000");
      givenStored(D2, "0.96");
      given(dividendRepository.findBySymbolAndExDate(SYMBOL, d3)).willReturn(Optional.empty());

      assertThat(service.saveDividends(SYMBOL, FROM, TO)).isEqualTo(2);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<com.muscat.marketdata.domain.entity.Dividend>> saved = ArgumentCaptor.forClass(List.class);
      verify(dividendRepository).saveAll(saved.capture());
      assertThat(saved.getValue())
        .extracting(com.muscat.marketdata.domain.entity.Dividend::getExDate, d -> d.getAmount().toPlainString())
        .containsExactly(org.assertj.core.groups.Tuple.tuple(D2, "0.24"), org.assertj.core.groups.Tuple.tuple(d3, "0.24"));
      verify(dividendEventProducer).publishBatch(saved.getValue());
      verify(collectionStats).recordDividends(2);
      verify(candleService).evictDividendYieldOrder();
    }

    @Test
    @DisplayName("배당이 없거나 모두 같거나 받기가 실패하면 저장하지 않고 0")
    void noNewDividends() {
      given(dividendSource.fetchDividends(SYMBOL, FROM, TO))
        .willReturn(List.of())
        .willReturn(List.of(dividend(D1)))
        .willThrow(new ResourceAccessException("yahoo down"));
      givenStored(D1, "0.24000000");

      for (int i = 0; i < 3; i++) {
        assertThat(service.saveDividends(SYMBOL, FROM, TO)).isZero();
      }
      verify(dividendRepository, never()).saveAll(any());
      verify(candleService, never()).evictDividendYieldOrder();
    }

    @Test
    @DisplayName("배당 받기 중 외부 장애가 아닌 오류는 그대로 던진다")
    void dividendBug() {
      given(dividendSource.fetchDividends(SYMBOL, FROM, TO)).willThrow(new IllegalStateException("bug"));

      assertThatThrownBy(() -> service.saveDividends(SYMBOL, FROM, TO)).isInstanceOf(IllegalStateException.class);
      verify(dividendRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("배당 저장 중 DB 장애면 0 이고 DB · Kafka 밖의 오류는 그대로 던진다")
    void dividendStoreFailures() {
      given(dividendSource.fetchDividends(SYMBOL, FROM, TO)).willReturn(List.of(dividend(D1)));
      given(dividendRepository.findBySymbolAndExDate(SYMBOL, D1)).willReturn(Optional.empty());
      given(dividendRepository.saveAll(any()))
        .willThrow(new DataAccessResourceFailureException("db down"))
        .willThrow(new IllegalStateException("bug"));

      assertThat(service.saveDividends(SYMBOL, FROM, TO)).isZero();
      assertThatThrownBy(() -> service.saveDividends(SYMBOL, FROM, TO)).isInstanceOf(IllegalStateException.class);
      verify(dividendEventProducer, never()).publishBatch(any());
    }

    @Test
    @DisplayName("캔들 · 배당을 같이 받으면 두 건수를 더한다")
    void savesBoth() {
      org.springframework.test.util.ReflectionTestUtils.setField(service, "self", service);
      stub(FROM, List.of(), List.of(candle(D1, "100"), candle(D2, "101")));
      given(dividendSource.fetchDividends(SYMBOL, FROM, TO)).willReturn(List.of(dividend(D2)));

      assertThat(service.saveBoth(SYMBOL, FROM, TO)).isEqualTo(3);
    }

    @Test
    @DisplayName("저장된 봉의 값이 비어 있고 새 값이 오면 고친다")
    void fillsMissingValue() {
      Candle stored = candle(D1, "100");
      stored.setDividendAmount(null);
      stub(FROM, List.of(stored), List.of(candle(D1, "100")));

      service.saveCandles(SYMBOL, FROM, TO);

      verify(collectionStats).recordSymbol(0, 1);
    }
  }
}
