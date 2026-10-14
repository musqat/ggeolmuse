package com.muscat.marketdata.datasource.alphavantage.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.datasource.alphavantage.provider.SymbolSource;
import com.muscat.marketdata.datasource.alphavantage.service.MarketCapCollectionService;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// LISTING_STATUS 로 신규 상장은 넣고, 목록에서 사라진 활성 종목은 상장폐지로 돌린다. 응답이 DB 의 절반도 안 되면 폐지 처리를 멈춘다
@DisplayName("AlphaVantage 종목 동기화")
class SymbolCollectorTest {

  private final SymbolSource symbolSource = mock(SymbolSource.class);
  private final AssetRepository assetRepository = mock(AssetRepository.class);
  private final AssetEventProducer producer = mock(AssetEventProducer.class);
  private final MarketCapCollectionService marketCapService = mock(MarketCapCollectionService.class);
  private final SymbolCollector collector = new SymbolCollector(symbolSource, assetRepository, producer, marketCapService);

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(collector, "enabled", true);
    ReflectionTestUtils.setField(collector, "collectData", true);
    ReflectionTestUtils.setField(collector, "includeDividends", true);
  }

  private static Asset asset(String symbol, boolean active) {
    return Asset.builder().symbol(symbol).active(active).build();
  }

  @Test
  @DisplayName("신규 상장은 저장하고, 목록에서 빠진 활성 종목은 오늘 날짜로 폐지 처리한 뒤 활성 종목 수집 · 시총 수집을 시작한다")
  void syncs() {
    Asset aapl = asset("AAPL", true);
    Asset gone = asset("GONE", true);
    Asset dead = asset("DEAD", false);
    Asset newco = asset("NEWCO", true);
    given(symbolSource.fetchSymbols()).willReturn(List.of(asset("AAPL", true), newco));
    given(assetRepository.findAll()).willReturn(List.of(aapl, gone, dead));
    given(assetRepository.findByActiveTrue()).willReturn(List.of(aapl, newco));

    collector.collectSymbols();

    verify(assetRepository).save(newco);
    assertThat(gone.getActive()).isFalse();
    assertThat(gone.getDelistedDate()).isEqualTo(LocalDate.now());
    verify(assetRepository).save(gone);
    verify(assetRepository, never()).save(dead);
    verify(producer).publishAssetCreated(aapl, true, LocalDate.of(1900, 1, 1), LocalDate.now(), true);
    verify(producer).publishAssetCreated(newco, true, LocalDate.of(1900, 1, 1), LocalDate.now(), true);
    verify(marketCapService).collectMarketCaps();
  }

  @Test
  @DisplayName("API 응답이 DB 종목의 절반도 안 되면 폐지 처리 없이 멈춘다")
  void stopsOnSuspiciousResponse() {
    given(symbolSource.fetchSymbols()).willReturn(List.of(asset("AAPL", true)));
    given(assetRepository.findAll()).willReturn(List.of(asset("AAPL", true), asset("MSFT", true), asset("NVDA", true)));

    collector.collectSymbols();

    verify(assetRepository, never()).save(any());
    verify(marketCapService, never()).collectMarketCaps();
  }

  @Test
  @DisplayName("꺼져 있거나 목록을 못 받으면 아무것도 하지 않는다")
  void disabledOrEmpty() {
    given(symbolSource.fetchSymbols()).willReturn(List.of());

    collector.collectSymbols();
    ReflectionTestUtils.setField(collector, "enabled", false);
    collector.collectSymbols();

    verify(symbolSource).fetchSymbols();
    verify(assetRepository, never()).findAll();
  }

  @Test
  @DisplayName("한 종목 저장 · 폐지 · 발행이 실패해도 나머지를 계속하고, 수집을 끄면 발행 없이 시총만 모은다")
  void continuesOnFailures() {
    Asset gone = asset("GONE", true);
    Asset broken = asset("BROKEN", true);
    Asset aapl = asset("AAPL", true);
    given(symbolSource.fetchSymbols()).willReturn(List.of(aapl, broken));
    given(assetRepository.findAll()).willReturn(List.of(asset("AAPL", true), gone));
    willThrow(new IllegalStateException("duplicate")).given(assetRepository).save(broken);
    willThrow(new IllegalStateException("locked")).given(assetRepository).save(gone);
    given(assetRepository.findByActiveTrue()).willReturn(List.of(aapl, broken));
    willThrow(new IllegalStateException("broker down")).given(producer)
      .publishAssetCreated(eq(aapl), anyBoolean(), any(), any(), anyBoolean());

    collector.collectSymbols();

    verify(producer).publishAssetCreated(argThat(a -> a.getSymbol().equals("BROKEN")), anyBoolean(), any(), any(), anyBoolean());
    verify(marketCapService).collectMarketCaps();

    ReflectionTestUtils.setField(collector, "collectData", false);
    collector.collectSymbols();
    verify(assetRepository, org.mockito.Mockito.times(1)).findByActiveTrue();
  }
}
