package com.muscat.marketdata.datasource.alphavantage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.datasource.common.MarketDataProvider.CandleSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.DividendSource;
import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.repository.DividendRepository;
import com.muscat.marketdata.infra.kafka.DividendEventProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// 저장된 마지막 날 다음부터 받고, 이미 있는 날짜 · 배당은 다시 저장하지 않는다
@DisplayName("AlphaVantage 캔들 · 배당 저장")
class AlphaVantageCandleUpdateServiceTest {

  private static final String SYMBOL = "AAPL";
  private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
  // 2024-03-10 은 일요일이라 기대하는 마지막 거래일은 3/8(금)
  private static final LocalDate SUNDAY = LocalDate.of(2024, 3, 10);
  private static final LocalDate FRIDAY = LocalDate.of(2024, 3, 8);

  private final CandleSource candleSource = mock(CandleSource.class);
  private final DividendSource dividendSource = mock(DividendSource.class);
  private final CandleRepository candleRepository = mock(CandleRepository.class);
  private final DividendRepository dividendRepository = mock(DividendRepository.class);
  private final DividendEventProducer producer = mock(DividendEventProducer.class);
  private final AlphaVantageCandleUpdateService service = new AlphaVantageCandleUpdateService(candleSource, dividendSource,
    candleRepository, dividendRepository, producer);

  private static Candle candle(LocalDate date) {
    return Candle.builder().symbol(SYMBOL).date(date).close(BigDecimal.TEN).build();
  }

  private static DividendDto dividend(LocalDate exDate) {
    return DividendDto.builder().symbol(SYMBOL).exDate(exDate).amount(new BigDecimal("0.24")).currency("USD").build();
  }

  @Test
  @DisplayName("처음이면 요청 기간 전체를 받아 저장된 날짜를 빼고 저장한다")
  void firstCollection() {
    given(candleRepository.findFirstBySymbolOrderByDateDesc(SYMBOL)).willReturn(Optional.empty());
    given(candleSource.fetchDailyAdjusted(SYMBOL, FROM, SUNDAY)).willReturn(List.of(candle(FRIDAY.minusDays(1)), candle(FRIDAY)));
    given(candleRepository.findBySymbol(SYMBOL)).willReturn(List.of(candle(FRIDAY.minusDays(1))));

    assertThat(service.saveCandles(SYMBOL, FROM, SUNDAY)).isEqualTo(1);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Candle>> saved = ArgumentCaptor.forClass(List.class);
    verify(candleRepository).saveAll(saved.capture());
    assertThat(saved.getValue()).extracting(Candle::getDate).containsExactly(FRIDAY);
  }

  @Test
  @DisplayName("마지막 저장일이 주말을 뺀 마지막 거래일이면 받지 않는다")
  void upToDate() {
    given(candleRepository.findFirstBySymbolOrderByDateDesc(SYMBOL)).willReturn(Optional.of(candle(FRIDAY)));

    assertThat(service.saveCandles(SYMBOL, FROM, SUNDAY)).isZero();
    verify(candleSource, never()).fetchDailyAdjusted(any(), any(), any());
  }

  @Test
  @DisplayName("마지막 저장일이 그 전이면 다음 날부터 받는다")
  void incremental() {
    given(candleRepository.findFirstBySymbolOrderByDateDesc(SYMBOL)).willReturn(Optional.of(candle(LocalDate.of(2024, 3, 5))));
    given(candleSource.fetchDailyAdjusted(SYMBOL, LocalDate.of(2024, 3, 6), SUNDAY)).willReturn(List.of(candle(FRIDAY)));
    given(candleRepository.findBySymbol(SYMBOL)).willReturn(List.of());

    assertThat(service.saveCandles(SYMBOL, FROM, SUNDAY)).isEqualTo(1);
  }

  @Test
  @DisplayName("받은 것이 없거나 모두 저장된 날짜면 저장하지 않는다")
  void nothingNew() {
    given(candleRepository.findFirstBySymbolOrderByDateDesc(SYMBOL)).willReturn(Optional.empty());
    given(candleSource.fetchDailyAdjusted(SYMBOL, FROM, SUNDAY)).willReturn(List.of(), List.of(candle(FRIDAY)));
    given(candleRepository.findBySymbol(SYMBOL)).willReturn(List.of(candle(FRIDAY)));

    assertThat(service.saveCandles(SYMBOL, FROM, SUNDAY)).isZero();
    assertThat(service.saveCandles(SYMBOL, FROM, SUNDAY)).isZero();
    verify(candleRepository, never()).saveAll(any());
  }

  @Test
  @DisplayName("새 배당은 넣고 주당 금액이 바뀐 배당은 고쳐 발행하고, 없거나 모두 같으면 0")
  void dividends() {
    given(dividendSource.fetchDividends(SYMBOL, FROM, SUNDAY))
      .willReturn(List.of(dividend(FRIDAY), dividend(FROM.plusDays(10))), List.of(), List.of(dividend(FRIDAY)));
    Dividend storedFriday = Dividend.builder().symbol(SYMBOL).exDate(FRIDAY).amount(new BigDecimal("0.12")).currency("USD")
      .build();
    given(dividendRepository.findBySymbolAndExDate(SYMBOL, FRIDAY)).willReturn(Optional.of(storedFriday));
    given(dividendRepository.findBySymbolAndExDate(SYMBOL, FROM.plusDays(10))).willReturn(Optional.empty());

    assertThat(service.saveDividends(SYMBOL, FROM, SUNDAY)).isEqualTo(2);
    assertThat(service.saveDividends(SYMBOL, FROM, SUNDAY)).isZero();
    // 첫 번째에서 0.24 로 고쳐져 세 번째는 같은 금액
    assertThat(service.saveDividends(SYMBOL, FROM, SUNDAY)).isZero();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Dividend>> saved = ArgumentCaptor.forClass(List.class);
    verify(dividendRepository).saveAll(saved.capture());
    assertThat(saved.getValue()).extracting(Dividend::getExDate).containsExactly(FRIDAY, FROM.plusDays(10));
    assertThat(storedFriday.getAmount()).isEqualByComparingTo("0.24");
    verify(producer).publishBatch(saved.getValue());
  }

  @Test
  @DisplayName("캔들 · 배당을 같이 받으면 두 건수를 더한다")
  void both() {
    given(candleRepository.findFirstBySymbolOrderByDateDesc(SYMBOL)).willReturn(Optional.empty());
    given(candleSource.fetchDailyAdjusted(SYMBOL, FROM, SUNDAY)).willReturn(List.of(candle(FRIDAY)));
    given(candleRepository.findBySymbol(SYMBOL)).willReturn(List.of());
    given(dividendSource.fetchDividends(SYMBOL, FROM, SUNDAY)).willReturn(List.of(dividend(FRIDAY)));

    assertThat(service.saveBoth(SYMBOL, FROM, SUNDAY)).isEqualTo(2);
  }
}
