package com.muscat.marketdata.domain.service.impl;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.repository.AssetQueryRepository;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleQueryRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.repository.DividendQueryRepository;
import com.muscat.marketdata.domain.repository.DividendRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketService 단위 테스트")
class MarketServiceImplTest {

    @Mock
    private CandleRepository candleRepository;

    @Mock
    private CandleQueryRepository candleQueryRepository;

    @Mock
    private DividendRepository dividendRepository;

    @Mock
    private DividendQueryRepository dividendQueryRepository;

    @Mock
    private AssetQueryRepository assetQueryRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private MarketDataProperties properties;

    @InjectMocks
    private MarketServiceImpl marketService;

    private String symbol;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        symbol = "AAPL";
        testDate = LocalDate.of(2024, 1, 15);
    }

    @Nested
    @DisplayName("OHLC 가격 조회 테스트")
    class GetOHLCPriceTests {

        @Test
        @DisplayName("정상적으로 OHLC 데이터를 조회한다")
        void getOHLCPrice_Success() {
            // given
            Candle candle = Candle.builder()
                    .symbol(symbol)
                    .date(testDate)
                    .open(new BigDecimal("150.00"))
                    .high(new BigDecimal("155.00"))
                    .low(new BigDecimal("149.00"))
                    .close(new BigDecimal("154.00"))
                    .adjustedClose(new BigDecimal("154.00"))
                    .volume(10000000L)
                    .currency("USD")
                    .build();

            given(candleRepository.findBySymbolAndDate(symbol, testDate))
                    .willReturn(Optional.of(candle));

            // when
            OHLCPriceDto result = marketService.getOHLCPrice(symbol, testDate);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getSymbol()).isEqualTo(symbol);
            assertThat(result.getDate()).isEqualTo(testDate);
            assertThat(result.getOpenPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(result.getClosePrice()).isEqualByComparingTo(new BigDecimal("154.00"));
            assertThat(result.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("데이터가 없으면 available=false로 반환한다")
        void getOHLCPrice_NotFound_ReturnsUnavailable() {
            // given
            given(candleRepository.findBySymbolAndDate(symbol, testDate))
                    .willReturn(Optional.empty());

            // when
            OHLCPriceDto result = marketService.getOHLCPrice(symbol, testDate);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getSymbol()).isEqualTo(symbol);
            assertThat(result.getDate()).isEqualTo(testDate);
            assertThat(result.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("소문자 심볼도 대문자로 변환하여 조회한다")
        void getOHLCPrice_LowercaseSymbol_ConvertsToUppercase() {
            // given
            String lowercaseSymbol = "aapl";
            Candle candle = Candle.builder()
                    .symbol("AAPL")
                    .date(testDate)
                    .close(new BigDecimal("154.00"))
                    .build();

            given(candleRepository.findBySymbolAndDate("AAPL", testDate))
                    .willReturn(Optional.of(candle));

            // when
            OHLCPriceDto result = marketService.getOHLCPrice(lowercaseSymbol, testDate);

            // then
            assertThat(result.getSymbol()).isEqualTo("AAPL");
        }
    }

    @Nested
    @DisplayName("OHLC 범위 조회 테스트")
    class GetOHLCPriceRangeTests {

        @Test
        @DisplayName("기간 내 OHLC 데이터를 조회한다")
        void getOHLCPriceRange_ReturnsMultipleCandles() {
            // given
            LocalDate startDate = LocalDate.of(2024, 1, 1);
            LocalDate endDate = LocalDate.of(2024, 1, 5);

            List<Candle> candles = Arrays.asList(
                    createCandle(symbol, LocalDate.of(2024, 1, 1), new BigDecimal("150.00")),
                    createCandle(symbol, LocalDate.of(2024, 1, 2), new BigDecimal("151.00")),
                    createCandle(symbol, LocalDate.of(2024, 1, 3), new BigDecimal("152.00"))
            );

            given(candleRepository.findBySymbolAndDateBetweenOrderByDateAsc(
                    symbol, startDate, endDate))
                    .willReturn(candles);

            // when
            List<OHLCPriceDto> results = marketService.getOHLCPriceRange(symbol, startDate, endDate);

            // then
            assertThat(results).hasSize(3);
            assertThat(results.get(0).getDate()).isEqualTo(LocalDate.of(2024, 1, 1));
            assertThat(results.get(1).getDate()).isEqualTo(LocalDate.of(2024, 1, 2));
            assertThat(results.get(2).getDate()).isEqualTo(LocalDate.of(2024, 1, 3));
        }

        @Test
        @DisplayName("데이터가 없으면 빈 리스트를 반환한다")
        void getOHLCPriceRange_NoData_ReturnsEmptyList() {
            // given
            LocalDate startDate = LocalDate.of(2024, 1, 1);
            LocalDate endDate = LocalDate.of(2024, 1, 5);

            given(candleRepository.findBySymbolAndDateBetweenOrderByDateAsc(
                    symbol, startDate, endDate))
                    .willReturn(List.of());

            // when
            List<OHLCPriceDto> results = marketService.getOHLCPriceRange(symbol, startDate, endDate);

            // then
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("현재가 조회 테스트")
    class GetCurrentPriceTests {

        @Test
        @DisplayName("최신 캔들 데이터로 현재가를 조회한다")
        void getCurrentPrice_Success() {
            // given
            // Properties 설정
            MarketDataProperties.Calculation calcConfig = new MarketDataProperties.Calculation();
            calcConfig.setPercentScale(4);
            calcConfig.setPercentageMultiplier(new BigDecimal("100"));
            given(properties.getCalculation()).willReturn(calcConfig);

            Candle latestCandle = createCandle(symbol, LocalDate.now(), new BigDecimal("155.00"));
            Candle previousCandle = createCandle(symbol, LocalDate.now().minusDays(1), new BigDecimal("150.00"));

            given(candleQueryRepository.findLatestBySymbol(symbol))
                    .willReturn(Optional.of(latestCandle));
            given(candleQueryRepository.findLatestBySymbolBeforeDate(symbol, latestCandle.getDate()))
                    .willReturn(Optional.of(previousCandle));

            // when
            StockPriceDto result = marketService.getCurrentPrice(symbol);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getSymbol()).isEqualTo(symbol);
            assertThat(result.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("155.00"));
            assertThat(result.getPreviousClose()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(result.isAvailable()).isTrue();

            // 변화율 계산 확인: (155 - 150) / 150 * 100 = 3.33%
            assertThat(result.getChangePercent()).isNotNull();
            assertThat(result.getChangePercent()).isGreaterThan(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("데이터가 없으면 available=false로 반환한다")
        void getCurrentPrice_NoData_ReturnsUnavailable() {
            // given
            given(candleQueryRepository.findLatestBySymbol(symbol))
                    .willReturn(Optional.empty());

            // when
            StockPriceDto result = marketService.getCurrentPrice(symbol);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getSymbol()).isEqualTo(symbol);
            assertThat(result.isAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("배당 이력 조회 테스트")
    class GetDividendHistoryTests {

        @Test
        @DisplayName("기간 내 배당 이력을 조회한다")
        void getDividendHistory_ReturnsMultipleDividends() {
            // given
            LocalDate startDate = LocalDate.of(2024, 1, 1);
            LocalDate endDate = LocalDate.of(2024, 12, 31);

            List<Dividend> dividends = Arrays.asList(
                    createDividend(symbol, LocalDate.of(2024, 3, 15), new BigDecimal("0.25")),
                    createDividend(symbol, LocalDate.of(2024, 6, 15), new BigDecimal("0.25")),
                    createDividend(symbol, LocalDate.of(2024, 9, 15), new BigDecimal("0.25"))
            );

            given(dividendQueryRepository.findBySymbolsAndDateRange(
                    List.of(symbol), startDate, endDate))
                    .willReturn(dividends);

            // when
            List<DividendDto> results = marketService.getDividendHistory(symbol, startDate, endDate);

            // then
            assertThat(results).hasSize(3);
            assertThat(results.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("0.25"));
        }

        @Test
        @DisplayName("배당이 없으면 빈 리스트를 반환한다")
        void getDividendHistory_NoData_ReturnsEmptyList() {
            // given
            LocalDate startDate = LocalDate.of(2024, 1, 1);
            LocalDate endDate = LocalDate.of(2024, 12, 31);

            given(dividendQueryRepository.findBySymbolsAndDateRange(
                    List.of(symbol), startDate, endDate))
                    .willReturn(List.of());

            // when
            List<DividendDto> results = marketService.getDividendHistory(symbol, startDate, endDate);

            // then
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("자산 조회 테스트")
    class GetAssetsTests {

        @Test
        @DisplayName("전체 심볼 목록을 조회한다")
        void getAllSymbols_ReturnsAllSymbols() {
            // given
            List<String> symbols = Arrays.asList("AAPL", "GOOGL", "MSFT");
            given(assetQueryRepository.findAllSymbols()).willReturn(symbols);

            // when
            List<String> results = marketService.getAllSymbols();

            // then
            assertThat(results).hasSize(3);
            assertThat(results).contains("AAPL", "GOOGL", "MSFT");
        }

        @Test
        @DisplayName("국가별 자산을 조회한다")
        void getAssetsByCountry_ReturnsFilteredAssets() {
            // given
            String country = "US";
            List<Asset> assets = Arrays.asList(
                    createAsset("AAPL", "US", "USD"),
                    createAsset("GOOGL", "US", "USD")
            );

            given(assetQueryRepository.findByCountry(country)).willReturn(assets);

            // when
            List<Asset> results = marketService.getAssetsByCountry(country);

            // then
            assertThat(results).hasSize(2);
            assertThat(results).extracting(Asset::getCountry)
                    .containsOnly("US");
        }

        @Test
        @DisplayName("통화별 자산을 조회한다")
        void getAssetsByCurrency_ReturnsFilteredAssets() {
            // given
            String currency = "USD";
            List<Asset> assets = Arrays.asList(
                    createAsset("AAPL", "US", "USD"),
                    createAsset("MSFT", "US", "USD")
            );

            given(assetQueryRepository.findByCurrency(currency)).willReturn(assets);

            // when
            List<Asset> results = marketService.getAssetsByCurrency(currency);

            // then
            assertThat(results).hasSize(2);
            assertThat(results).extracting(Asset::getCurrency)
                    .containsOnly("USD");
        }
    }

    // Helper methods
    private Candle createCandle(String symbol, LocalDate date, BigDecimal closePrice) {
        return Candle.builder()
                .symbol(symbol)
                .date(date)
                .open(closePrice.subtract(new BigDecimal("1.00")))
                .high(closePrice.add(new BigDecimal("2.00")))
                .low(closePrice.subtract(new BigDecimal("2.00")))
                .close(closePrice)
                .adjustedClose(closePrice)
                .volume(1000000L)
                .currency("USD")
                .build();
    }

    private Dividend createDividend(String symbol, LocalDate exDate, BigDecimal amount) {
        return Dividend.builder()
                .symbol(symbol)
                .exDate(exDate)
                .amount(amount)
                .currency("USD")
                .build();
    }

    private Asset createAsset(String symbol, String country, String currency) {
        return Asset.builder()
                .symbol(symbol)
                .name(symbol + " Inc.")
                .country(country)
                .currency(currency)
                .assetType("Stock")
                .build();
    }
}
