package com.muscat.marketdata.datasource.yf.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.muscat.marketdata.datasource.alphavantage.scheduler.AlphaVantageScheduler;
import com.muscat.marketdata.datasource.common.MarketDataProvider;
import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.repository.DividendRepository;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import com.muscat.marketdata.infra.kafka.DividendEventProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 실제 트랜잭션 · H2 저장으로 본 종목 하나의 캔들 · 배당 저장
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("야후 캔들 · 배당 저장(실제 트랜잭션)")
class YahooCandleUpdateServiceStoreTest {

  private static final LocalDate FROM = LocalDate.of(2012, 1, 1);
  private static final LocalDate TO = LocalDate.of(2026, 11, 2);
  // NUMERIC(24,8) 도 넘는 값(10^16 이상)
  private static final BigDecimal TOO_LARGE = new BigDecimal("100000000000000000");

  @Autowired
  private YahooCandleUpdateService service;

  @Autowired
  private DividendRepository dividendRepository;

  @Autowired
  private CandleRepository candleRepository;

  @MockitoBean
  private MarketDataProvider.DividendSource dividendSource;

  @MockitoBean
  private MarketDataProvider.CandleSource candleSource;

  @MockitoBean
  private DividendEventProducer dividendEventProducer;

  @MockitoBean
  private AssetEventProducer assetEventProducer;

  @MockitoBean
  private AlphaVantageScheduler alphaVantageScheduler;

  @AfterEach
  void tearDown() {
    dividendRepository.deleteAll();
    candleRepository.deleteAll();
  }

  @Test
  @DisplayName("배당 저장이 실패해도 예외를 밖으로 내지 않고 0 을 돌려준다")
  void dividendFailureStaysInside() {
    given(dividendSource.fetchDividends(eq("WHLR"), any(), any()))
      .willReturn(List.of(dividend(LocalDate.of(2012, 12, 27), TOO_LARGE)));

    assertThat(service.saveDividends("WHLR", FROM, TO)).isZero();
    assertThat(dividendRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("캔들 저장이 실패해도 예외를 밖으로 내지 않고 0 을 돌려준다")
  void candleFailureStaysInside() {
    given(candleSource.fetchDailyAdjusted(eq("WHLR"), any(), any()))
      .willReturn(List.of(candle(LocalDate.of(2012, 12, 27), TOO_LARGE)));

    assertThat(service.saveCandles("WHLR", FROM, TO)).isZero();
    assertThat(candleRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("역분할로 조정된 10^11 넘는 배당도 저장한다")
  void storesHugeAdjustedDividend() {
    BigDecimal adjusted = new BigDecimal("921773280000");
    given(dividendSource.fetchDividends(eq("WHLR"), any(), any()))
      .willReturn(List.of(dividend(LocalDate.of(2012, 12, 27), adjusted)));

    assertThat(service.saveDividends("WHLR", FROM, TO)).isEqualTo(1);
    assertThat(dividendRepository.findAll()).singleElement()
      .satisfies(d -> assertThat(d.getAmount()).isEqualByComparingTo(adjusted));
  }

  private static DividendDto dividend(LocalDate exDate, BigDecimal amount) {
    return DividendDto.builder().symbol("WHLR").exDate(exDate).amount(amount).build();
  }

  private static Candle candle(LocalDate date, BigDecimal close) {
    return Candle.builder().symbol("WHLR").date(date).currency("USD")
      .open(close).high(close).low(close).close(close).adjustedClose(close).volume(1L).build();
  }
}
