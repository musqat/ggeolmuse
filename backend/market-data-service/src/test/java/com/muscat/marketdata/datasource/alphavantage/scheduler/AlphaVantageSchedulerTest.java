package com.muscat.marketdata.datasource.alphavantage.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.datasource.alphavantage.provider.AlphaVantageCandleSource;
import com.muscat.marketdata.datasource.alphavantage.provider.AlphaVantageFxSource;
import com.muscat.marketdata.datasource.alphavantage.provider.AlphaVantageSymbolSource;
import com.muscat.marketdata.datasource.alphavantage.service.MarketCapCollectionService;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// 초기 수집(캔들 있는 종목 95% 미만) 중에는 환율 · 캔들 · 시총 갱신을 건너뛴다
@DisplayName("AlphaVantage 스케줄러")
class AlphaVantageSchedulerTest {

  private final AlphaVantageFxSource fxSource = mock(AlphaVantageFxSource.class);
  private final AssetRepository assetRepository = mock(AssetRepository.class);
  private final FxRateRepository fxRateRepository = mock(FxRateRepository.class);
  private final AssetEventProducer producer = mock(AssetEventProducer.class);
  private final MarketCapCollectionService marketCapService = mock(MarketCapCollectionService.class);
  private final AlphaVantageScheduler scheduler = new AlphaVantageScheduler(fxSource, mock(AlphaVantageCandleSource.class),
    mock(AlphaVantageSymbolSource.class), assetRepository, fxRateRepository, producer, marketCapService);

  private void collected(long total, long withCandles) {
    given(assetRepository.count()).willReturn(total);
    given(assetRepository.countWithCandles()).willReturn(withCandles);
  }

  private static LocalDate previousBusinessDay(LocalDate date) {
    LocalDate prev = date.minusDays(1);
    while (prev.getDayOfWeek() == DayOfWeek.SATURDAY || prev.getDayOfWeek() == DayOfWeek.SUNDAY) {
      prev = prev.minusDays(1);
    }
    return prev;
  }

  @Nested
  @DisplayName("기동 시 환율 채우기")
  class InitialFxTests {

    @Test
    @DisplayName("환율이 1년치 미만이면 전 기간 환율을 소수 6자리로 맞춰 저장한다")
    void fillsWhenFew() {
      given(fxRateRepository.count()).willReturn(10L);
      given(fxSource.fetchAllFxRates()).willReturn(Map.of(LocalDate.of(2024, 3, 4), new BigDecimal("1331.1234567")));

      scheduler.collectInitialFxRatesIfNeeded();

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<FxRate>> saved = ArgumentCaptor.forClass(List.class);
      verify(fxRateRepository).saveAll(saved.capture());
      assertThat(saved.getValue()).singleElement().satisfies(rate -> {
        assertThat(rate.getRate()).isEqualByComparingTo("1331.123457");
        assertThat(rate.getCurrencyPair()).isEqualTo("USD/KRW");
      });
    }

    @Test
    @DisplayName("1년치 이상이거나 받은 환율이 없거나 호출이 실패하면 저장하지 않는다")
    void skips() {
      given(fxRateRepository.count()).willReturn(365L, 0L, 0L);
      given(fxSource.fetchAllFxRates()).willReturn(Map.of()).willThrow(new IllegalStateException("limit"));

      scheduler.collectInitialFxRatesIfNeeded();
      scheduler.collectInitialFxRatesIfNeeded();
      scheduler.collectInitialFxRatesIfNeeded();

      verify(fxRateRepository, never()).saveAll(any());
    }
  }

  @Nested
  @DisplayName("정기 갱신")
  class ScheduledTests {

    @BeforeEach
    void collectedEnough() {
      collected(100, 96);
    }

    @Test
    @DisplayName("환율은 오늘 값이 없으면 이전 영업일 값을 오늘 날짜로 저장한다")
    void fxFallsBack() {
      LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
      given(fxSource.fetchFx(any())).willReturn(Optional.empty());
      given(fxSource.fetchFx(previousBusinessDay(today))).willReturn(Optional.of(new BigDecimal("1330")));
      given(fxRateRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

      scheduler.updateFxRates();

      ArgumentCaptor<FxRate> saved = ArgumentCaptor.forClass(FxRate.class);
      verify(fxRateRepository).save(saved.capture());
      assertThat(saved.getValue().getDate()).isEqualTo(today);
      assertThat(saved.getValue().getRate()).isEqualByComparingTo("1330");
    }

    @Test
    @DisplayName("이레를 거슬러도 환율이 없으면 저장하지 않는다")
    void fxGivesUp() {
      given(fxSource.fetchFx(any())).willReturn(Optional.empty());

      scheduler.updateFxRates();

      verify(fxRateRepository, never()).save(any());
    }

    @Test
    @DisplayName("캔들은 활성 종목마다 1년 수집을 발행하고 한 종목 실패에 멈추지 않는다")
    void candles() {
      Asset fail = Asset.builder().symbol("FAIL").build();
      Asset aapl = Asset.builder().symbol("AAPL").build();
      given(assetRepository.findByActiveTrue()).willReturn(List.of(fail, aapl));
      willThrow(new IllegalStateException("broker down")).given(producer).publishAssetCreated(eq(fail), anyBoolean(), any(), any(), anyBoolean());
      LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));

      scheduler.updateCandles();

      verify(producer).publishAssetCreated(aapl, true, today.minusYears(1), today, false);
    }

    @Test
    @DisplayName("시총 갱신이 실패해도 예외를 올리지 않는다")
    void marketCaps() {
      willThrow(new IllegalStateException("limit")).given(marketCapService).updateAllMarketCaps();

      scheduler.updateMarketCaps();

      verify(marketCapService).updateAllMarketCaps();
    }
  }

  @Test
  @DisplayName("캔들 있는 종목이 95% 미만이면 환율 · 캔들 · 시총 갱신을 건너뛴다")
  void skipsDuringInitialCollection() {
    collected(100, 50);

    scheduler.updateFxRates();
    scheduler.updateCandles();
    scheduler.updateMarketCaps();

    verify(fxSource, never()).fetchFx(any());
    verify(assetRepository, never()).findByActiveTrue();
    verify(marketCapService, never()).updateAllMarketCaps();
  }

  @Test
  @DisplayName("종목이 없거나 진행률 확인이 실패하면 갱신을 돌린다")
  void runsWhenNoAssetsOrCheckFails() {
    given(assetRepository.count()).willReturn(0L).willThrow(new IllegalStateException("db down"));

    scheduler.updateMarketCaps();
    scheduler.updateMarketCaps();

    verify(marketCapService, org.mockito.Mockito.times(2)).updateAllMarketCaps();
  }
}
