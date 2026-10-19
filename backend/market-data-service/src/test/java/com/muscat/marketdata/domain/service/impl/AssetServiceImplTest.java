package com.muscat.marketdata.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import com.muscat.marketdata.common.exceptions.MarketDataException;
import com.muscat.marketdata.datasource.common.MarketDataProvider.AssetInfoSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.CandleSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.MarketCapSource;
import com.muscat.marketdata.domain.dto.AssetSummaryDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssetService  테스트")
class AssetServiceImplTest {

  @Mock
  private AssetRepository assetRepository;

  @Mock
  private CandleRepository candleRepository;

  @Mock
  private AssetInfoSource assetInfoSource;

  @Mock
  private MarketCapSource marketCapSource;

  @Mock
  private CandleSource candleSource;

  @Mock
  private AssetEventProducer assetEventProducer;

  private AssetServiceImpl assetService;

  private static final String TEST_SYMBOL = "AAPL";
  private static final String TEST_NAME = "Apple Inc.";
  private static final String TEST_COUNTRY = "US";
  private static final String TEST_CURRENCY = "USD";
  private static final String TEST_ASSET_TYPE = "Stock";

  private Asset testAsset;

  @BeforeEach
  void setUp() {
    testAsset = Asset.builder()
      .symbol(TEST_SYMBOL)
      .name(TEST_NAME)
      .country(TEST_COUNTRY)
      .currency(TEST_CURRENCY)
      .assetType(TEST_ASSET_TYPE)
      .build();

    assetService = new AssetServiceImpl(assetRepository, candleRepository, assetEventProducer);
    setField(assetService, "assetInfoSource", assetInfoSource);
    setField(assetService, "marketCapSource", marketCapSource);
    setField(assetService, "candleSource", candleSource);
  }

  @Nested
  @DisplayName("심볼 미리보기 테스트")
  class PreviewSymbolTests {

    @Test
    @DisplayName("SymbolSource가 활성화되어 있고 심볼을 찾으면 Asset을 반환한다")
    void previewSymbol_SymbolFound_Success() {
      // given
      given(assetInfoSource.getAsset(TEST_SYMBOL)).willReturn(testAsset);

      // when
      Optional<Asset> result = assetService.previewSymbol(TEST_SYMBOL);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getSymbol()).isEqualTo(TEST_SYMBOL);
      assertThat(result.get().getName()).isEqualTo(TEST_NAME);

      verify(assetInfoSource).getAsset(TEST_SYMBOL);
    }

    @Test
    @DisplayName("심볼을 대문자로 변환하여 조회한다")
    void previewSymbol_ConvertsToUpperCase_Success() {
      // given
      String lowerCaseSymbol = "aapl";
      given(assetInfoSource.getAsset("AAPL")).willReturn(testAsset);

      // when
      Optional<Asset> result = assetService.previewSymbol(lowerCaseSymbol);

      // then
      assertThat(result).isPresent();
      verify(assetInfoSource).getAsset("AAPL");
    }

    @Test
    @DisplayName("SymbolSource가 null이면 빈 Optional을 반환한다")
    void previewSymbol_SymbolSourceNull_ReturnsEmpty() {
      // given
      AssetServiceImpl serviceWithNullSource = new AssetServiceImpl(
        assetRepository, candleRepository, assetEventProducer);

      // when
      Optional<Asset> result = serviceWithNullSource.previewSymbol(TEST_SYMBOL);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("심볼을 찾지 못하면 빈 Optional을 반환한다")
    void previewSymbol_SymbolNotFound_ReturnsEmpty() {
      // given
      given(assetInfoSource.getAsset(TEST_SYMBOL)).willReturn(null);

      // when
      Optional<Asset> result = assetService.previewSymbol(TEST_SYMBOL);

      // then
      assertThat(result).isEmpty();
      verify(assetInfoSource).getAsset(TEST_SYMBOL);
    }
  }

  @Nested
  @DisplayName("심볼 검색 테스트")
  class SearchSymbolsTests {

    @Test
    @DisplayName("키워드로 심볼을 검색하여 결과를 반환한다")
    void searchSymbols_WithKeyword_Success() {
      // given
      String keyword = "Apple";
      List<Asset> expectedAssets = List.of(
        testAsset,
        Asset.builder()
          .symbol("AAPL.L")
          .name("Apple Inc. London")
          .country("UK")
          .currency("GBP")
          .assetType("Stock")
          .build()
      );

      given(assetRepository.searchByKeyword(keyword, 20)).willReturn(expectedAssets);

      // when
      List<Asset> results = assetService.searchSymbols(keyword);

      // then
      assertThat(results).hasSize(2);
      assertThat(results).containsExactlyElementsOf(expectedAssets);

      verify(assetRepository).searchByKeyword(keyword, 20);
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 리스트를 반환한다")
    void searchSymbols_NoMatches_ReturnsEmpty() {
      // given
      String keyword = "NonExistent";
      given(assetRepository.searchByKeyword(keyword, 20)).willReturn(new ArrayList<>());

      // when
      List<Asset> results = assetService.searchSymbols(keyword);

      // then
      assertThat(results).isEmpty();
      verify(assetRepository).searchByKeyword(keyword, 20);
    }

    @Test
    @DisplayName("검색 결과는 최대 20개로 제한된다")
    void searchSymbols_LimitsTo20_Success() {
      // given
      String keyword = "Tech";
      List<Asset> expectedAssets = new ArrayList<>();
      for (int i = 0; i < 20; i++) {
        expectedAssets.add(Asset.builder()
          .symbol("TECH" + i)
          .name("Tech Company " + i)
          .country("US")
          .currency("USD")
          .assetType("Stock")
          .build());
      }

      given(assetRepository.searchByKeyword(keyword, 20)).willReturn(expectedAssets);

      // when
      List<Asset> results = assetService.searchSymbols(keyword);

      // then
      assertThat(results).hasSize(20);
      verify(assetRepository).searchByKeyword(keyword, 20);
    }
  }

  @Nested
  @DisplayName("종목 추가 테스트")
  class CreateAssetTests {

    @Test
    @DisplayName("모든 정보가 제공되면 종목을 추가하고 Kafka 이벤트를 발행한다")
    void createAsset_WithFullInfo_Success() {
      // given
      LocalDate fromDate = LocalDate.of(2024, 1, 1);
      LocalDate toDate = LocalDate.of(2024, 12, 31);

      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.empty());
      given(assetRepository.save(any(Asset.class))).willReturn(testAsset);

      // when
      Asset result = assetService.createAsset(
        TEST_SYMBOL, TEST_NAME, TEST_COUNTRY, TEST_CURRENCY,
        TEST_ASSET_TYPE, true, fromDate, toDate, true);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getSymbol()).isEqualTo(TEST_SYMBOL);
      assertThat(result.getName()).isEqualTo(TEST_NAME);

      verify(assetRepository).findById(TEST_SYMBOL);
      verify(assetRepository).save(any(Asset.class));
      verify(assetEventProducer).publishAssetCreated(testAsset, true, fromDate, toDate, true);
    }

    @Test
    @DisplayName("정보가 부족하면 SymbolSource에서 조회하여 추가한다")
    void createAsset_WithPartialInfo_FetchesFromSource() {
      // given
      String partialSymbol = "googl";
      Asset fetchedAsset = Asset.builder()
        .symbol("GOOGL")
        .name("Alphabet Inc.")
        .country("US")
        .currency("USD")
        .assetType("Stock")
        .build();

      given(assetRepository.findById("GOOGL")).willReturn(Optional.empty());
      given(assetInfoSource.getAsset("GOOGL")).willReturn(fetchedAsset);
      given(assetRepository.save(any(Asset.class))).willReturn(fetchedAsset);

      // when
      Asset result = assetService.createAsset(
        partialSymbol, null, null, null, null,
        false, null, null, false);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getSymbol()).isEqualTo("GOOGL");
      assertThat(result.getName()).isEqualTo("Alphabet Inc.");

      verify(assetInfoSource).getAsset("GOOGL");
      verify(assetRepository).save(any(Asset.class));
    }

    @Test
    @DisplayName("심볼을 대문자로 변환하여 저장한다")
    void createAsset_ConvertsToUpperCase_Success() {
      // given
      String lowerCaseSymbol = "msft";

      given(assetRepository.findById("MSFT")).willReturn(Optional.empty());
      given(assetRepository.save(any(Asset.class))).willAnswer(
        invocation -> invocation.getArgument(0));

      // when
      Asset result = assetService.createAsset(
        lowerCaseSymbol, "Microsoft Corp.", "US", "USD", "Stock",
        false, null, null, false);

      // then
      assertThat(result.getSymbol()).isEqualTo("MSFT");
      verify(assetRepository).findById("MSFT");
    }

    @Test
    @DisplayName("이미 존재하는 종목이면 예외를 발생시킨다")
    void createAsset_AlreadyExists_ThrowsException() {
      // given
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.of(testAsset));

      // when & then
      assertThatThrownBy(() -> assetService.createAsset(
        TEST_SYMBOL, TEST_NAME, TEST_COUNTRY, TEST_CURRENCY,
        TEST_ASSET_TYPE, false, null, null, false))
        .isInstanceOf(MarketDataException.class)
        .hasFieldOrPropertyWithValue("errorCode", "ASSET_ALREADY_EXISTS")
        .hasMessage("이미 등록된 종목입니다: AAPL");

      verify(assetRepository).findById(TEST_SYMBOL);
      verify(assetRepository, never()).save(any(Asset.class));
    }

    @Test
    @DisplayName("SymbolSource가 null이고 정보가 부족하면 예외를 발생시킨다")
    void createAsset_SymbolSourceNullAndMissingInfo_ThrowsException() {
      // given
      AssetServiceImpl serviceWithNullSource = new AssetServiceImpl(
        assetRepository, candleRepository, assetEventProducer);

      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> serviceWithNullSource.createAsset(
        TEST_SYMBOL, null, null, null, null,
        false, null, null, false))
        .isInstanceOf(MarketDataException.class)
        .hasFieldOrPropertyWithValue("errorCode", "DATA_SOURCE_UNAVAILABLE");

      verify(assetRepository).findById(TEST_SYMBOL);
    }

    @Test
    @DisplayName("SymbolSource에서 심볼을 찾지 못하면 예외를 발생시킨다")
    void createAsset_SymbolNotFoundInSource_ThrowsException() {
      // given
      String unknownSymbol = "UNKNOWN";

      given(assetRepository.findById("UNKNOWN")).willReturn(Optional.empty());
      given(assetInfoSource.getAsset("UNKNOWN")).willReturn(null);

      // when & then
      assertThatThrownBy(() -> assetService.createAsset(
        unknownSymbol, null, null, null, null,
        false, null, null, false))
        .isInstanceOf(MarketDataException.class)
        .hasFieldOrPropertyWithValue("errorCode", "SYMBOL_NOT_FOUND")
        .hasMessageEndingWith(": UNKNOWN");

      verify(assetInfoSource).getAsset("UNKNOWN");
    }

    @Test
    @DisplayName("부분 정보를 제공하면 나머지는 SymbolSource에서 채운다")
    void createAsset_PartialInfo_FillsFromSource() {
      // given
      Asset sourceAsset = Asset.builder()
        .symbol(TEST_SYMBOL)
        .name("From Source")
        .country("US")
        .currency("USD")
        .assetType("Stock")
        .build();

      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.empty());
      given(assetInfoSource.getAsset(TEST_SYMBOL)).willReturn(sourceAsset);
      given(assetRepository.save(any(Asset.class))).willAnswer(
        invocation -> invocation.getArgument(0));

      // when - name만 제공하고 나머지는 null
      Asset result = assetService.createAsset(
        TEST_SYMBOL, "User Provided Name", null, null, null,
        false, null, null, false);

      // then - 제공된 name은 유지되고, 나머지는 source에서 채워짐
      assertThat(result.getSymbol()).isEqualTo(TEST_SYMBOL);
      assertThat(result.getName()).isEqualTo("User Provided Name");
      assertThat(result.getCountry()).isEqualTo("US");
      assertThat(result.getCurrency()).isEqualTo("USD");
      assertThat(result.getAssetType()).isEqualTo("Stock");
    }
  }

  @Nested
  @DisplayName("종목 조회 테스트")
  class GetAssetTests {

    @Test
    @DisplayName("존재하는 종목을 조회하면 Asset을 반환한다")
    void getAsset_Exists_Success() {
      // given
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.of(testAsset));

      // when
      Optional<Asset> result = assetService.getAsset(TEST_SYMBOL);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getSymbol()).isEqualTo(TEST_SYMBOL);
      assertThat(result.get().getName()).isEqualTo(TEST_NAME);

      verify(assetRepository).findById(TEST_SYMBOL);
    }

    @Test
    @DisplayName("존재하지 않는 종목을 조회하면 빈 Optional을 반환한다")
    void getAsset_NotExists_ReturnsEmpty() {
      // given
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.empty());

      // when
      Optional<Asset> result = assetService.getAsset(TEST_SYMBOL);

      // then
      assertThat(result).isEmpty();
      verify(assetRepository).findById(TEST_SYMBOL);
    }

    @Test
    @DisplayName("심볼을 대문자로 변환하여 조회한다")
    void getAsset_ConvertsToUpperCase_Success() {
      // given
      String lowerCaseSymbol = "aapl";
      given(assetRepository.findById("AAPL")).willReturn(Optional.of(testAsset));

      // when
      Optional<Asset> result = assetService.getAsset(lowerCaseSymbol);

      // then
      assertThat(result).isPresent();
      verify(assetRepository).findById("AAPL");
    }
  }

  @Nested
  @DisplayName("전체 종목 조회 테스트")
  class GetAllAssetsTests {

    @Test
    @DisplayName("전체 종목 목록을 조회한다")
    void getAllAssets_ReturnsAllAssets() {
      // given
      List<Asset> allAssets = List.of(
        testAsset,
        Asset.builder()
          .symbol("GOOGL")
          .name("Alphabet Inc.")
          .country("US")
          .currency("USD")
          .assetType("Stock")
          .build(),
        Asset.builder()
          .symbol("MSFT")
          .name("Microsoft Corp.")
          .country("US")
          .currency("USD")
          .assetType("Stock")
          .build()
      );

      given(assetRepository.findAll()).willReturn(allAssets);

      // when
      List<Asset> results = assetService.getAllAssets();

      // then
      assertThat(results).hasSize(3);
      assertThat(results).containsExactlyElementsOf(allAssets);

      verify(assetRepository).findAll();
    }

    @Test
    @DisplayName("종목이 없으면 빈 리스트를 반환한다")
    void getAllAssets_NoAssets_ReturnsEmpty() {
      // given
      given(assetRepository.findAll()).willReturn(new ArrayList<>());

      // when
      List<Asset> results = assetService.getAllAssets();

      // then
      assertThat(results).isEmpty();
      verify(assetRepository).findAll();
    }
  }

  @Nested
  @DisplayName("종목 삭제 테스트")
  class DeleteAssetTests {

    @Test
    @DisplayName("존재하는 종목을 삭제한다")
    void deleteAsset_Exists_Success() {
      // given
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.of(testAsset));
      given(assetRepository.save(any(Asset.class))).willReturn(testAsset);

      // when
      assetService.deleteAsset(TEST_SYMBOL);

      // then
      verify(assetRepository).findById(TEST_SYMBOL);
      verify(assetRepository).save(any(Asset.class));
    }

    @Test
    @DisplayName("심볼을 대문자로 변환하여 삭제한다")
    void deleteAsset_ConvertsToUpperCase_Success() {
      // given
      String lowerCaseSymbol = "aapl";
      given(assetRepository.findById("AAPL")).willReturn(Optional.of(testAsset));
      given(assetRepository.save(any(Asset.class))).willReturn(testAsset);

      // when
      assetService.deleteAsset(lowerCaseSymbol);

      // then
      verify(assetRepository).findById("AAPL");
      verify(assetRepository).save(any(Asset.class));
    }

    @Test
    @DisplayName("존재하지 않는 종목을 삭제하려 하면 예외를 발생시킨다")
    void deleteAsset_NotExists_ThrowsException() {
      // given
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> assetService.deleteAsset(TEST_SYMBOL))
        .isInstanceOf(MarketDataException.class)
        .hasFieldOrPropertyWithValue("errorCode", "SYMBOL_NOT_FOUND")
        .hasMessageEndingWith(": AAPL");

      verify(assetRepository).findById(TEST_SYMBOL);
      verify(assetRepository, never()).save(any(Asset.class));
    }
  }

  @Nested
  @DisplayName("전체 종목 요약 정보 조회 테스트")
  class GetAllAssetSummariesTests {

    @Test
    @DisplayName("활성 종목의 요약 정보를 페이징하여 조회한다")
    void getAllAssetSummaries_WithPaging_Success() {
      // given: 최신가/날짜는 asset에 비정규화되어 candle 조회 없이 DB 페이징
      Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
      Page<Asset> assetPage =
        new org.springframework.data.domain.PageImpl<>(List.of(testAsset), pageable, 1);
      given(assetRepository.findSorted(any(Pageable.class), anyBoolean())).willReturn(assetPage);

      // when
      Page<AssetSummaryDto> result =
        assetService.getAllAssetSummaries(pageable, true);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getTotalElements()).isEqualTo(1);

      verify(assetRepository).findSorted(any(Pageable.class), anyBoolean());
    }
  }

  @Nested
  @DisplayName("종목 가격 업데이트 테스트")
  class UpdateAssetPriceTests {

    @Test
    @DisplayName("존재하는 종목의 가격을 업데이트한다")
    void updateAssetPrice_ExistingAsset_Success() {
      // given
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.of(testAsset));

      Candle candle =
        Candle.builder()
          .symbol(TEST_SYMBOL)
          .date(LocalDate.now())
          .close(java.math.BigDecimal.valueOf(150.0))
          .build();

      given(candleSource.fetchDailyAdjusted(eq(TEST_SYMBOL), any(LocalDate.class),
        any(LocalDate.class)))
        .willReturn(List.of(candle));
      given(candleRepository.existsBySymbolAndDate(TEST_SYMBOL, candle.getDate()))
        .willReturn(false);

      // when
      assetService.updateAssetPrice(TEST_SYMBOL);

      // then
      verify(assetRepository).findById(TEST_SYMBOL);
      verify(candleSource).fetchDailyAdjusted(eq(TEST_SYMBOL), any(LocalDate.class),
        any(LocalDate.class));
      verify(candleRepository).save(candle);
    }

    @Test
    @DisplayName("존재하지 않는 종목이면 예외를 발생시킨다")
    void updateAssetPrice_NotExists_ThrowsException() {
      // given
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> assetService.updateAssetPrice(TEST_SYMBOL))
        .isInstanceOf(MarketDataException.class)
        .hasFieldOrPropertyWithValue("errorCode", "SYMBOL_NOT_FOUND")
        .hasMessageEndingWith(": AAPL");

      verify(assetRepository).findById(TEST_SYMBOL);
    }

    @Test
    @DisplayName("받은 봉이 없으면 가격 데이터 없음(404) 예외를 던지고 봉을 저장하지 않는다")
    void updateAssetPrice_NoCandles_ThrowsPriceDataNotFound() {
      // given
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.of(testAsset));
      given(candleSource.fetchDailyAdjusted(eq(TEST_SYMBOL), any(LocalDate.class),
        any(LocalDate.class)))
        .willReturn(List.of());

      // when & then
      assertThatThrownBy(() -> assetService.updateAssetPrice(TEST_SYMBOL))
        .isInstanceOf(MarketDataException.class)
        .hasFieldOrPropertyWithValue("errorCode", "PRICE_DATA_NOT_FOUND")
        .hasMessage("가격 데이터를 찾을 수 없습니다: AAPL");

      verify(candleRepository, never()).save(any());
    }

    @Test
    @DisplayName("비활성 종목도 받은 최신 봉으로 최신 종가와 날짜를 바꾼다")
    void updateAssetPrice_InactiveAsset_UpdatesLatest() {
      // given
      testAsset.setActive(false);
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.of(testAsset));

      Candle latest =
        Candle.builder()
          .symbol(TEST_SYMBOL)
          .date(LocalDate.of(2026, 9, 14))
          .close(java.math.BigDecimal.valueOf(41.5))
          .build();
      Candle older =
        Candle.builder()
          .symbol(TEST_SYMBOL)
          .date(LocalDate.of(2026, 9, 11))
          .close(java.math.BigDecimal.valueOf(40.0))
          .build();

      given(candleSource.fetchDailyAdjusted(eq(TEST_SYMBOL), any(LocalDate.class),
        any(LocalDate.class)))
        .willReturn(List.of(latest, older));
      given(candleRepository.existsBySymbolAndDate(eq(TEST_SYMBOL), any(LocalDate.class)))
        .willReturn(false);

      // when
      assetService.updateAssetPrice(TEST_SYMBOL);

      // then
      assertThat(testAsset.getLatestDate()).isEqualTo(LocalDate.of(2026, 9, 14));
      assertThat(testAsset.getLatestClose()).isEqualByComparingTo("41.5");
      verify(assetRepository).save(testAsset);
    }

    @Test
    @DisplayName("받은 최신 봉이 저장된 최신 날짜보다 오래되면 최신 값을 덮어쓰지 않는다")
    void updateAssetPrice_OlderCandle_KeepsLatest() {
      // given
      testAsset.setLatestDate(LocalDate.of(2026, 9, 14));
      testAsset.setLatestClose(java.math.BigDecimal.valueOf(41.5));
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.of(testAsset));

      Candle older =
        Candle.builder()
          .symbol(TEST_SYMBOL)
          .date(LocalDate.of(2026, 9, 11))
          .close(java.math.BigDecimal.valueOf(40.0))
          .build();

      given(candleSource.fetchDailyAdjusted(eq(TEST_SYMBOL), any(LocalDate.class),
        any(LocalDate.class)))
        .willReturn(List.of(older));
      given(candleRepository.existsBySymbolAndDate(TEST_SYMBOL, older.getDate()))
        .willReturn(true);

      // when
      assetService.updateAssetPrice(TEST_SYMBOL);

      // then
      assertThat(testAsset.getLatestDate()).isEqualTo(LocalDate.of(2026, 9, 14));
      assertThat(testAsset.getLatestClose()).isEqualByComparingTo("41.5");
      verify(assetRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("종목 이름 수정 테스트")
  class UpdateAssetNameTests {

    @Test
    @DisplayName("존재하는 종목의 이름을 수정하고 저장한다")
    void updateAssetName_ExistingAsset_Success() {
      // given
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.of(testAsset));
      given(assetRepository.save(any(Asset.class))).willAnswer(
        invocation -> invocation.getArgument(0));

      // when
      Asset result = assetService.updateAssetName(TEST_SYMBOL, "SpaceX");

      // then
      assertThat(result.getName()).isEqualTo("SpaceX");
      verify(assetRepository).findById(TEST_SYMBOL);
      verify(assetRepository).save(testAsset);
    }

    @Test
    @DisplayName("이름 앞뒤 공백은 제거하여 저장한다")
    void updateAssetName_TrimsWhitespace_Success() {
      // given
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.of(testAsset));
      given(assetRepository.save(any(Asset.class))).willAnswer(
        invocation -> invocation.getArgument(0));

      // when
      Asset result = assetService.updateAssetName(TEST_SYMBOL, "  SpaceX  ");

      // then
      assertThat(result.getName()).isEqualTo("SpaceX");
    }

    @Test
    @DisplayName("심볼을 대문자로 변환하여 조회한다")
    void updateAssetName_ConvertsToUpperCase_Success() {
      // given
      given(assetRepository.findById("AAPL")).willReturn(Optional.of(testAsset));
      given(assetRepository.save(any(Asset.class))).willAnswer(
        invocation -> invocation.getArgument(0));

      // when
      assetService.updateAssetName("aapl", "SpaceX");

      // then
      verify(assetRepository).findById("AAPL");
    }

    @Test
    @DisplayName("존재하지 않는 종목이면 예외를 발생시킨다")
    void updateAssetName_NotExists_ThrowsException() {
      // given
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> assetService.updateAssetName(TEST_SYMBOL, "SpaceX"))
        .isInstanceOf(MarketDataException.class)
        .hasFieldOrPropertyWithValue("errorCode", "SYMBOL_NOT_FOUND");

      verify(assetRepository).findById(TEST_SYMBOL);
      verify(assetRepository, never()).save(any(Asset.class));
    }

    @Test
    @DisplayName("이름이 비어있으면 예외를 발생시킨다")
    void updateAssetName_BlankName_ThrowsException() {
      // when & then
      assertThatThrownBy(() -> assetService.updateAssetName(TEST_SYMBOL, "   "))
        .isInstanceOf(MarketDataException.class)
        .hasFieldOrPropertyWithValue("errorCode", "ASSET_NAME_REQUIRED");

      verify(assetRepository, never()).save(any(Asset.class));
    }

    @Test
    @DisplayName("이름이 null이면 예외를 발생시킨다")
    void updateAssetName_NullName_ThrowsException() {
      // when & then
      assertThatThrownBy(() -> assetService.updateAssetName(TEST_SYMBOL, null))
        .isInstanceOf(MarketDataException.class)
        .hasFieldOrPropertyWithValue("errorCode", "ASSET_NAME_REQUIRED");

      verify(assetRepository, never()).save(any(Asset.class));
    }
  }

  @Nested
  @DisplayName("종목 시가총액 업데이트 테스트")
  class UpdateAssetMarketCapTests {

    @Test
    @DisplayName("존재하는 종목의 시가총액을 업데이트한다")
    void updateAssetMarketCap_ExistingAsset_Success() {
      // given
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.of(testAsset));
      given(marketCapSource.updateMarketCap(TEST_SYMBOL)).willReturn(true);

      // when
      assetService.updateAssetMarketCap(TEST_SYMBOL);

      // then: 존재 확인 후 활성 provider에 위임
      verify(assetRepository).findById(TEST_SYMBOL);
      verify(marketCapSource).updateMarketCap(TEST_SYMBOL);
    }

    @Test
    @DisplayName("존재하지 않는 종목이면 예외를 발생시킨다")
    void updateAssetMarketCap_NotExists_ThrowsException() {
      // given
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> assetService.updateAssetMarketCap(TEST_SYMBOL))
        .isInstanceOf(MarketDataException.class)
        .hasFieldOrPropertyWithValue("errorCode", "SYMBOL_NOT_FOUND");

      verify(assetRepository).findById(TEST_SYMBOL);
    }
  }

  @Nested
  @DisplayName("활성 티커 목록 테스트")
  class ActiveSymbolsTests {

    @Test
    @DisplayName("리포지토리가 준 티커를 그대로 돌려준다")
    void getActiveSymbols_ReturnsRepositoryResult() {
      // given
      given(assetRepository.findActiveSymbols()).willReturn(List.of("AAPL", "MSFT"));

      // when
      List<String> result = assetService.getActiveSymbols();

      // then
      assertThat(result).containsExactly("AAPL", "MSFT");
      verify(assetRepository).findActiveSymbols();
    }
  }

  @Nested
  @DisplayName("관리자 작업 · 소스 없음")
  class AdminOperationTests {

    @Test
    @DisplayName("요약 정렬의 화면 필드 이름을 저장 필드 이름으로 바꿔 조회하고, 정렬이 없으면 그대로 넘긴다")
    void remapsSummarySort() {
      given(assetRepository.findSorted(any(), anyBoolean())).willReturn(new PageImpl<>(List.of(testAsset)));
      Pageable byPrice = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("currentPrice"), Sort.Order.asc("latestDataDate"),
        Sort.Order.asc("name")));
      Pageable unsorted = PageRequest.of(1, 5);

      Page<AssetSummaryDto> page = assetService.getAllAssetSummaries(byPrice, true);
      assetService.getAllAssetSummaries(unsorted, false);

      assertThat(page.getContent()).extracting(AssetSummaryDto::getSymbol).containsExactly(TEST_SYMBOL);
      verify(assetRepository).findSorted(PageRequest.of(0, 20,
        Sort.by(Sort.Order.desc("latestClose"), Sort.Order.asc("latestDate"), Sort.Order.asc("name"))), true);
      verify(assetRepository).findSorted(unsorted, false);
    }

    @Test
    @DisplayName("일봉 · 시총 소스가 없으면 갱신을 막는다")
    void sourcesMissing() {
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.of(testAsset));
      setField(assetService, "candleSource", null);
      setField(assetService, "marketCapSource", null);

      assertThatThrownBy(() -> assetService.updateAssetPrice("aapl")).isInstanceOf(MarketDataException.class)
        .hasFieldOrPropertyWithValue("errorCode", "DATA_SOURCE_UNAVAILABLE");
      assertThatThrownBy(() -> assetService.updateAssetMarketCap("aapl")).isInstanceOf(MarketDataException.class)
        .hasFieldOrPropertyWithValue("errorCode", "DATA_SOURCE_UNAVAILABLE");
      assertThatThrownBy(() -> assetService.updateAllMarketCaps()).isInstanceOf(MarketDataException.class)
        .hasFieldOrPropertyWithValue("errorCode", "DATA_SOURCE_UNAVAILABLE");
    }

    @Test
    @DisplayName("한 종목 시총 갱신은 소스가 못 받으면 막고, 없는 종목이면 종목 없음 코드를 던진다")
    void marketCapOne() {
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.of(testAsset));
      given(assetRepository.findById("NOPE")).willReturn(Optional.empty());
      given(marketCapSource.updateMarketCap(TEST_SYMBOL)).willReturn(true, false);

      assetService.updateAssetMarketCap("aapl");
      assertThatThrownBy(() -> assetService.updateAssetMarketCap("aapl")).isInstanceOf(MarketDataException.class)
        .hasFieldOrPropertyWithValue("errorCode", "MARKET_CAP_UNAVAILABLE")
        .hasMessage("시가총액을 가져오지 못했습니다: AAPL");
      assertThatThrownBy(() -> assetService.updateAssetMarketCap("nope")).isInstanceOf(MarketDataException.class)
        .hasFieldOrPropertyWithValue("errorCode", "SYMBOL_NOT_FOUND")
        .hasMessageEndingWith(": NOPE");
    }

    @Test
    @DisplayName("전체 시총 갱신은 활성 종목을 소스에 넘기고 바꾼 수를 돌려준다")
    void marketCapAll() {
      given(assetRepository.findByActiveTrue()).willReturn(List.of(testAsset));
      given(marketCapSource.updateAllMarketCaps(List.of(testAsset))).willReturn(1);

      assertThat(assetService.updateAllMarketCaps()).isEqualTo(1);
    }

    @Test
    @DisplayName("복구는 활성으로 돌리고 폐지일을 지우며, 없는 종목이면 종목 없음 코드를 던진다")
    void restore() {
      testAsset.setActive(false);
      testAsset.setDelistedDate(LocalDate.of(2024, 1, 1));
      given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.of(testAsset));
      given(assetRepository.findById("NOPE")).willReturn(Optional.empty());

      assetService.restoreAsset("aapl");

      assertThat(testAsset.getActive()).isTrue();
      assertThat(testAsset.getDelistedDate()).isNull();
      verify(assetRepository).save(testAsset);
      assertThatThrownBy(() -> assetService.restoreAsset("nope")).isInstanceOf(MarketDataException.class)
        .hasFieldOrPropertyWithValue("errorCode", "SYMBOL_NOT_FOUND");
    }

    @Test
    @DisplayName("일괄 삭제는 빈 값 · 중복을 빼고 대문자로 찾아 오늘 날짜로 폐지 처리하며, 목록이 비면 0")
    void bulkDelete() {
      Asset msft = Asset.builder().symbol("MSFT").build();
      given(assetRepository.findAllById(List.of("AAPL", "MSFT", "NOPE"))).willReturn(List.of(testAsset, msft));

      int deleted = assetService.deleteAssets(java.util.Arrays.asList("aapl", " ", null, "AAPL", "msft", "nope"));

      assertThat(deleted).isEqualTo(2);
      assertThat(testAsset.getActive()).isFalse();
      assertThat(msft.getDelistedDate()).isEqualTo(LocalDate.now());
      verify(assetRepository).saveAll(List.of(testAsset, msft));
      assertThat(assetService.deleteAssets(null)).isZero();
      assertThat(assetService.deleteAssets(List.of())).isZero();
    }
  }
}
