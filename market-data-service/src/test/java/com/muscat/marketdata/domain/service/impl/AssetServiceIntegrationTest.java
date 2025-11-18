package com.muscat.marketdata.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.marketdata.datasource.alphavantage.scheduler.AlphaVantageScheduler;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AssetService 통합 테스트")
class AssetServiceIntegrationTest {

  @Autowired
  private AssetServiceImpl assetService;

  @Autowired
  private AssetRepository assetRepository;

  @MockBean
  private AssetEventProducer assetEventProducer;

  @MockBean
  private AlphaVantageScheduler alphaVantageScheduler;

  private static final String TEST_SYMBOL = "AAPL";
  private static final String TEST_NAME = "Apple Inc.";
  private static final String TEST_COUNTRY = "US";
  private static final String TEST_CURRENCY = "USD";
  private static final String TEST_ASSET_TYPE = "EQUITY";

  @BeforeEach
  void setUp() {
    // Clean database before each test
    assetRepository.deleteAll();
  }

  @AfterEach
  void tearDown() {
    assetRepository.deleteAll();
  }

  @Nested
  @DisplayName("자산 생성 통합 테스트")
  class CreateAssetIntegrationTests {

    @Test
    @DisplayName("자산 생성 시 DB에 저장되고 조회할 수 있다")
    void createAsset_SavesAssetToDatabase() {
      // when
      Asset createdAsset = assetService.createAsset(
        TEST_SYMBOL, TEST_NAME, TEST_COUNTRY, TEST_CURRENCY, TEST_ASSET_TYPE,
        false, null, null, false);

      // then
      assertThat(createdAsset).isNotNull();
      assertThat(createdAsset.getSymbol()).isEqualTo(TEST_SYMBOL);
      assertThat(createdAsset.getName()).isEqualTo(TEST_NAME);
      assertThat(createdAsset.getCountry()).isEqualTo(TEST_COUNTRY);
      assertThat(createdAsset.getCurrency()).isEqualTo(TEST_CURRENCY);
      assertThat(createdAsset.getAssetType()).isEqualTo(TEST_ASSET_TYPE);
      assertThat(createdAsset.getActive()).isTrue();

      // DB에 저장되었는지 확인
      Optional<Asset> savedAsset = assetRepository.findById(TEST_SYMBOL);
      assertThat(savedAsset).isPresent();
      assertThat(savedAsset.get().getSymbol()).isEqualTo(TEST_SYMBOL);
      assertThat(savedAsset.get().getName()).isEqualTo(TEST_NAME);
    }

    @Test
    @DisplayName("심볼이 대문자로 저장된다")
    void createAsset_SymbolConvertedToUpperCase() {
      // when
      Asset createdAsset = assetService.createAsset(
        "aapl", TEST_NAME, TEST_COUNTRY, TEST_CURRENCY, TEST_ASSET_TYPE,
        false, null, null, false);

      // then
      assertThat(createdAsset.getSymbol()).isEqualTo("AAPL");

      // DB 확인
      Optional<Asset> savedAsset = assetRepository.findById("AAPL");
      assertThat(savedAsset).isPresent();
    }

    @Test
    @DisplayName("중복된 심볼로 자산 생성 시 예외 발생")
    void createAsset_DuplicateSymbol_ThrowsException() {
      // given - 첫 번째 자산 생성
      assetService.createAsset(
        TEST_SYMBOL, TEST_NAME, TEST_COUNTRY, TEST_CURRENCY, TEST_ASSET_TYPE,
        false, null, null, false);

      // when & then - 동일 심볼로 두 번째 자산 생성 시도
      assertThatThrownBy(() ->
        assetService.createAsset(
          TEST_SYMBOL, "Different Name", TEST_COUNTRY, TEST_CURRENCY, TEST_ASSET_TYPE,
          false, null, null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Already exists");

      // DB에 하나만 존재하는지 확인
      List<Asset> allAssets = assetRepository.findAll();
      assertThat(allAssets).hasSize(1);
    }

    @Test
    @DisplayName("다양한 자산 유형(EQUITY, ETF) 생성 가능")
    void createAsset_DifferentAssetTypes_AllSaved() {
      // given & when
      Asset equityAsset = assetService.createAsset(
        "AAPL", "Apple Inc.", "US", "USD", "EQUITY",
        false, null, null, false);

      Asset etfAsset = assetService.createAsset(
        "SPY", "SPDR S&P 500 ETF", "US", "USD", "ETF",
        false, null, null, false);

      // then
      assertThat(equityAsset.getAssetType()).isEqualTo("EQUITY");
      assertThat(etfAsset.getAssetType()).isEqualTo("ETF");

      // DB 확인
      List<Asset> allAssets = assetRepository.findAll();
      assertThat(allAssets).hasSize(2);
    }
  }

  @Nested
  @DisplayName("자산 조회 통합 테스트")
  class GetAssetIntegrationTests {

    @Test
    @DisplayName("저장된 자산을 심볼로 조회할 수 있다")
    void getAsset_ReturnsExistingAsset() {
      // given - 자산 직접 저장
      Asset asset = Asset.builder()
        .symbol(TEST_SYMBOL)
        .name(TEST_NAME)
        .country(TEST_COUNTRY)
        .currency(TEST_CURRENCY)
        .assetType(TEST_ASSET_TYPE)
        .active(true)
        .build();
      assetRepository.save(asset);

      // when
      Optional<Asset> foundAsset = assetService.getAsset(TEST_SYMBOL);

      // then
      assertThat(foundAsset).isPresent();
      assertThat(foundAsset.get().getSymbol()).isEqualTo(TEST_SYMBOL);
      assertThat(foundAsset.get().getName()).isEqualTo(TEST_NAME);
    }

    @Test
    @DisplayName("소문자 심볼로 조회해도 대문자로 변환되어 조회된다")
    void getAsset_LowerCaseSymbol_ConvertsAndFinds() {
      // given
      Asset asset = Asset.builder()
        .symbol(TEST_SYMBOL)
        .name(TEST_NAME)
        .country(TEST_COUNTRY)
        .currency(TEST_CURRENCY)
        .assetType(TEST_ASSET_TYPE)
        .active(true)
        .build();
      assetRepository.save(asset);

      // when - 소문자로 조회
      Optional<Asset> foundAsset = assetService.getAsset("aapl");

      // then
      assertThat(foundAsset).isPresent();
      assertThat(foundAsset.get().getSymbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("존재하지 않는 심볼 조회 시 Empty 반환")
    void getAsset_NonExistentSymbol_ReturnsEmpty() {
      // when
      Optional<Asset> foundAsset = assetService.getAsset("NONEXISTENT");

      // then
      assertThat(foundAsset).isEmpty();
    }
  }

  @Nested
  @DisplayName("자산 검색 통합 테스트")
  class SearchAssetsIntegrationTests {

    @BeforeEach
    void setUpMultipleAssets() {
      // 여러 자산 생성
      assetRepository.save(Asset.builder()
        .symbol("AAPL")
        .name("Apple Inc.")
        .country("US")
        .currency("USD")
        .assetType("EQUITY")
        .active(true)
        .build());

      assetRepository.save(Asset.builder()
        .symbol("MSFT")
        .name("Microsoft Corporation")
        .country("US")
        .currency("USD")
        .assetType("EQUITY")
        .active(true)
        .build());

      assetRepository.save(Asset.builder()
        .symbol("GOOGL")
        .name("Alphabet Inc.")
        .country("US")
        .currency("USD")
        .assetType("EQUITY")
        .active(true)
        .build());

      assetRepository.save(Asset.builder()
        .symbol("SPY")
        .name("SPDR S&P 500 ETF")
        .country("US")
        .currency("USD")
        .assetType("ETF")
        .active(true)
        .build());
    }

    @Test
    @DisplayName("키워드로 자산 검색이 가능하다")
    void searchSymbols_FindsMatchingAssets() {
      // when
      List<Asset> results = assetService.searchSymbols("Apple");

      // then
      assertThat(results).isNotEmpty();
      assertThat(results).anyMatch(asset -> asset.getSymbol().equals("AAPL"));
    }

    @Test
    @DisplayName("심볼로 검색 가능")
    void searchSymbols_BySymbol_FindsAsset() {
      // when
      List<Asset> results = assetService.searchSymbols("MSFT");

      // then
      assertThat(results).isNotEmpty();
      assertThat(results).anyMatch(asset -> asset.getSymbol().equals("MSFT"));
    }

    @Test
    @DisplayName("매칭되지 않는 키워드는 빈 결과 반환")
    void searchSymbols_NoMatch_ReturnsEmptyList() {
      // when
      List<Asset> results = assetService.searchSymbols("NONEXISTENT_KEYWORD");

      // then
      assertThat(results).isEmpty();
    }
  }

  @Nested
  @DisplayName("자산 목록 조회 통합 테스트")
  class ListAssetsIntegrationTests {

    @Test
    @DisplayName("모든 자산 목록 조회 시 DB의 모든 자산이 반환된다")
    void getAllAssets_ReturnsAllAssets() {
      // given
      assetRepository.save(Asset.builder()
        .symbol("AAPL")
        .name("Apple Inc.")
        .country("US")
        .currency("USD")
        .assetType("EQUITY")
        .active(true)
        .build());

      assetRepository.save(Asset.builder()
        .symbol("MSFT")
        .name("Microsoft Corporation")
        .country("US")
        .currency("USD")
        .assetType("EQUITY")
        .active(true)
        .build());

      // when
      List<Asset> assets = assetService.getAllAssets();

      // then
      assertThat(assets).isNotNull();
      assertThat(assets).hasSize(2);
      assertThat(assets).extracting("symbol")
        .containsExactlyInAnyOrder("AAPL", "MSFT");
    }

    @Test
    @DisplayName("빈 DB에서 조회 시 빈 리스트 반환")
    void getAllAssets_EmptyDatabase_ReturnsEmptyList() {
      // when
      List<Asset> assets = assetService.getAllAssets();

      // then
      assertThat(assets).isEmpty();
    }
  }

  @Nested
  @DisplayName("전체 플로우 통합 테스트")
  class EndToEndFlowTests {

    @Test
    @DisplayName("자산 생성 → 조회 → 검색 전체 플로우가 정상 동작한다")
    void createGetSearch_EndToEndFlow() {
      // 1. 자산 생성
      Asset createdAsset = assetService.createAsset(
        TEST_SYMBOL, TEST_NAME, TEST_COUNTRY, TEST_CURRENCY, TEST_ASSET_TYPE,
        false, null, null, false);
      assertThat(createdAsset.getSymbol()).isEqualTo(TEST_SYMBOL);

      // 2. 생성된 자산 조회
      Optional<Asset> retrievedAsset = assetService.getAsset(TEST_SYMBOL);
      assertThat(retrievedAsset).isPresent();
      assertThat(retrievedAsset.get().getName()).isEqualTo(TEST_NAME);

      // 3. 검색으로 자산 찾기
      List<Asset> searchResults = assetService.searchSymbols("Apple");
      assertThat(searchResults).isNotEmpty();
      assertThat(searchResults).anyMatch(asset -> asset.getSymbol().equals(TEST_SYMBOL));

      // 4. 전체 목록에서 확인
      List<Asset> allAssets = assetService.getAllAssets();
      assertThat(allAssets).hasSize(1);
      assertThat(allAssets.get(0).getSymbol()).isEqualTo(TEST_SYMBOL);
    }

    @Test
    @DisplayName("여러 자산 생성 및 관리 플로우")
    void multipleAssets_EndToEndFlow() {
      // 1. 여러 자산 생성
      assetService.createAsset("AAPL", "Apple Inc.", "US", "USD", "EQUITY",
        false, null, null, false);
      assetService.createAsset("MSFT", "Microsoft Corporation", "US", "USD", "EQUITY",
        false, null, null, false);
      assetService.createAsset("SPY", "SPDR S&P 500 ETF", "US", "USD", "ETF",
        false, null, null, false);

      // 2. 전체 목록 확인
      List<Asset> allAssets = assetService.getAllAssets();
      assertThat(allAssets).hasSize(3);

      // 3. 각 자산 개별 조회 확인
      assertThat(assetService.getAsset("AAPL")).isPresent();
      assertThat(assetService.getAsset("MSFT")).isPresent();
      assertThat(assetService.getAsset("SPY")).isPresent();

      // 4. 검색 기능 확인
      List<Asset> microsoftResults = assetService.searchSymbols("Microsoft");
      assertThat(microsoftResults).isNotEmpty();
      assertThat(microsoftResults).anyMatch(asset -> asset.getSymbol().equals("MSFT"));
    }
  }
}
