package com.muscat.marketdata.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.datasource.common.SymbolCatalog;
import com.muscat.marketdata.datasource.yf.client.NasdaqScreenerClient;
import com.muscat.marketdata.datasource.yf.client.NasdaqScreenerParser;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// 로컬에서 빈 DB 에 종목을 처음 채우는 도구(NASDAQ 스크리너 먼저, 안 되면 저장소에 묶인 CSV)
@DisplayName("종목 파일 로더")
class SymbolFileLoaderTest {

  private final AssetRepository assetRepository = mock(AssetRepository.class);
  private final AssetEventProducer producer = mock(AssetEventProducer.class);
  private final NasdaqScreenerClient nasdaqClient = mock(NasdaqScreenerClient.class);
  private final NasdaqScreenerParser nasdaqParser = mock(NasdaqScreenerParser.class);
  private final SymbolFileLoader loader = new SymbolFileLoader(assetRepository, producer, nasdaqClient, nasdaqParser);

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(loader, "useApi", true);
    ReflectionTestUtils.setField(loader, "marketCapFilter", "mega|large");
    ReflectionTestUtils.setField(loader, "collectDataEnabled", false);
    ReflectionTestUtils.setField(loader, "lookbackDays", 30);
    ReflectionTestUtils.setField(loader, "includeDividends", true);
    ReflectionTestUtils.setField(loader, "excludeNamePatterns", SymbolCatalog.DEFAULT_EXCLUDE_NAME_PATTERNS);
  }

  @SuppressWarnings("unchecked")
  private List<Asset> saved() {
    ArgumentCaptor<List<Asset>> captor = ArgumentCaptor.forClass(List.class);
    verify(assetRepository).saveAll(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("종목이 이미 있으면 아무것도 하지 않는다")
  void skipsWhenAssetsExist() throws Exception {
    given(assetRepository.count()).willReturn(3L);

    loader.run();

    verify(nasdaqClient, never()).getAllStocks(anyString(), anyString());
    verify(assetRepository, never()).saveAll(any());
  }

  @Test
  @DisplayName("스크리너에서 NYSE · NASDAQ 종목을 받아 저장하고, 수집을 끄면 발행하지 않는다")
  void loadsFromScreener() throws Exception {
    given(nasdaqClient.getAllStocks("nyse", "mega|large")).willReturn("nyse");
    given(nasdaqClient.getAllStocks("nasdaq", "mega|large")).willReturn("nasdaq");
    given(nasdaqParser.parseStocks("nyse")).willReturn(List.of(Asset.builder().symbol("JPM").build(),
      Asset.builder().symbol("XYZW").name("XYZ Acquisition Corp. Warrants").build()));
    given(nasdaqParser.parseStocks("nasdaq")).willReturn(List.of(Asset.builder().symbol("AAPL").build()));

    loader.run();

    // 워런트 제외
    assertThat(saved()).extracting(Asset::getSymbol).containsExactly("JPM", "AAPL");
    verify(producer, never()).publishAssetCreated(any(), anyBoolean(), any(), any(), anyBoolean());
  }

  @Test
  @DisplayName("스크리너가 실패하면 묶인 CSV 를 읽어 시총 · 국가 · 유형을 채운다")
  void fallsBackToCsv() throws Exception {
    given(nasdaqClient.getAllStocks(anyString(), anyString())).willThrow(new IllegalStateException("blocked"));

    loader.run();

    List<Asset> assets = saved();
    assertThat(assets).hasSizeGreaterThan(100);
    assertThat(assets).filteredOn(asset -> asset.getSymbol().equals("A")).singleElement().satisfies(agilent -> {
      assertThat(agilent.getMarketCap()).isEqualTo(41_300_342_205L);
      assertThat(agilent.getCountry()).isEqualTo("US");
      assertThat(agilent.getAssetType()).isEqualTo("EQUITY");
    });
    // 이름에 Trust 인 은행은 주식, 산업 칸 빈 ETF 행도 적재
    assertThat(assets).filteredOn(asset -> asset.getSymbol().equals("NTRS")).singleElement()
      .extracting(Asset::getAssetType).isEqualTo("EQUITY");
    assertThat(assets).filteredOn(asset -> asset.getSymbol().equals("IBIT")).singleElement()
      .extracting(Asset::getAssetType).isEqualTo("ETF");
    // 워런트 · 우선주 예탁증서 제외
    assertThat(assets).extracting(Asset::getSymbol).doesNotContain("EXEEL", "AGNCL");
  }

  @Test
  @DisplayName("API 를 끄면 바로 CSV 를 읽고, 수집을 켜면 종목마다 기간 수집을 발행하며 한 종목 실패에 멈추지 않는다")
  void csvWithCollection() throws Exception {
    ReflectionTestUtils.setField(loader, "useApi", false);
    ReflectionTestUtils.setField(loader, "collectDataEnabled", true);
    willThrow(new IllegalStateException("broker down")).given(producer)
      .publishAssetCreated(org.mockito.ArgumentMatchers.argThat(a -> a.getSymbol().equals("A")), anyBoolean(), any(), any(), anyBoolean());

    loader.run();

    verify(nasdaqClient, never()).getAllStocks(anyString(), anyString());
    verify(producer).publishAssetCreated(org.mockito.ArgumentMatchers.argThat(a -> a.getSymbol().equals("ABBV")), eq(true),
      eq(LocalDate.now().minusDays(30)), eq(LocalDate.now()), eq(true));
  }
}
