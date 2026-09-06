package com.muscat.marketdata.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.springframework.test.util.ReflectionTestUtils.setField;

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
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Already exists");

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
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AssetInfoSource not available");

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
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Symbol not found");

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
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Not found");

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
      given(assetRepository.findByActiveTrue(any(Pageable.class))).willReturn(assetPage);

      // when
      Page<AssetSummaryDto> result =
        assetService.getAllAssetSummaries(pageable);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getTotalElements()).isEqualTo(1);

      verify(assetRepository).findByActiveTrue(any(Pageable.class));
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
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Not found");

      verify(assetRepository).findById(TEST_SYMBOL);
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
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Not found");

      verify(assetRepository).findById(TEST_SYMBOL);
      verify(assetRepository, never()).save(any(Asset.class));
    }

    @Test
    @DisplayName("이름이 비어있으면 예외를 발생시킨다")
    void updateAssetName_BlankName_ThrowsException() {
      // when & then
      assertThatThrownBy(() -> assetService.updateAssetName(TEST_SYMBOL, "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");

      verify(assetRepository, never()).save(any(Asset.class));
    }

    @Test
    @DisplayName("이름이 null이면 예외를 발생시킨다")
    void updateAssetName_NullName_ThrowsException() {
      // when & then
      assertThatThrownBy(() -> assetService.updateAssetName(TEST_SYMBOL, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");

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
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Not found");

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
}
