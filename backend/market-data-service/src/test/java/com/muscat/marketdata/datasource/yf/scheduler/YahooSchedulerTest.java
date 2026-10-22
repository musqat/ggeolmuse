package com.muscat.marketdata.datasource.yf.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.datasource.common.FxDataCollector;
import com.muscat.marketdata.datasource.common.InitialCollectionCheck;
import com.muscat.marketdata.datasource.yf.service.YahooMarketCapService;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 초기 수집(캔들 있는 종목이 95% 미만) 중에는 캔들 · 시총 갱신을 건너뛴다. 환율은 항상 돈다
@DisplayName("야후 스케줄러")
class YahooSchedulerTest {

  private final AssetRepository assetRepository = mock(AssetRepository.class);
  private final FxRateRepository fxRateRepository = mock(FxRateRepository.class);
  private final AssetEventProducer producer = mock(AssetEventProducer.class);
  private final YahooMarketCapService marketCapService = mock(YahooMarketCapService.class);
  private final FxDataCollector fxDataCollector = mock(FxDataCollector.class);
  private final YahooScheduler scheduler = new YahooScheduler(assetRepository, fxRateRepository, producer,
    marketCapService, fxDataCollector, new InitialCollectionCheck(assetRepository));

  {
    ReflectionTestUtils.setField(scheduler, "candleLookbackDays", 30);
  }

  private void collected(long total, long withCandles) {
    given(assetRepository.count()).willReturn(total);
    given(assetRepository.countWithCandles()).willReturn(withCandles);
  }

  @Test
  @DisplayName("캔들 있는 종목이 95% 미만이면 캔들 · 시총 갱신을 건너뛴다")
  void skipsDuringInitialCollection() {
    collected(100, 94);

    scheduler.updateCandles();
    scheduler.updateMarketCaps();

    verify(assetRepository, never()).findByActiveTrue();
    verify(marketCapService, never()).updateAllMarketCaps(any());
  }

  @Test
  @DisplayName("캔들 갱신은 활성 종목마다 최근 기간 수집을 배당까지 발행하고, 한 종목이 실패해도 다음으로 간다")
  void publishesCandleCollection() {
    collected(100, 95);
    Asset fail = Asset.builder().symbol("FAIL").build();
    Asset aapl = Asset.builder().symbol("AAPL").build();
    given(assetRepository.findByActiveTrue()).willReturn(List.of(fail, aapl));
    willThrow(new IllegalStateException("broker down")).given(producer).publishAssetCreated(eq(fail), anyBoolean(), any(), any(), anyBoolean());
    LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));

    scheduler.updateCandles();

    verify(producer).publishAssetCreated(aapl, true, today.minusDays(30), today, true);
  }

  @Test
  @DisplayName("종목이 하나도 없거나 진행률 확인이 실패하면 갱신을 돌린다")
  void runsWhenNoAssetsOrCheckFails() {
    given(assetRepository.count()).willReturn(0L).willThrow(new IllegalStateException("db down"));
    given(assetRepository.findByActiveTrue()).willReturn(List.of());

    scheduler.updateMarketCaps();
    scheduler.updateMarketCaps();

    verify(marketCapService, org.mockito.Mockito.times(2)).updateAllMarketCaps(List.of());
  }

  @Test
  @DisplayName("환율은 오늘(서울) 값을 영업일 보정해 받아 저장하고, 못 받으면 저장하지 않는다")
  void updatesFxRate() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    FxRate rate = FxRate.builder().date(today).rate(new BigDecimal("1380")).build();
    given(fxDataCollector.collectSingleDate(today, true)).willReturn(Optional.of(rate), Optional.empty());

    scheduler.updateFxRates();
    scheduler.updateFxRates();

    verify(fxRateRepository).save(rate);
  }
}
