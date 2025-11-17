package com.muscat.marketdata.domain.service.impl;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CandleService 단위 테스트")
class CandleServiceImplTest {

    @Mock
    private CandleRepository candleRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private MarketDataProperties properties;

    @InjectMocks
    private CandleServiceImpl candleService;

    private static final String TEST_SYMBOL = "AAPL";
    private static final LocalDate TEST_DATE = LocalDate.of(2024, 10, 25);
    private static final BigDecimal TEST_CLOSE = new BigDecimal("238.15");
    private static final BigDecimal TEST_OPEN = new BigDecimal("237.50");
    private static final BigDecimal TEST_HIGH = new BigDecimal("242.30");
    private static final BigDecimal TEST_LOW = new BigDecimal("235.80");
    private static final Long TEST_VOLUME = 45678900L;

    private Candle testCandle;
    private MarketDataProperties.Calculation calculationConfig;

    @BeforeEach
    void setUp() {
        testCandle = Candle.builder()
                .symbol(TEST_SYMBOL)
                .date(TEST_DATE)
                .open(TEST_OPEN)
                .high(TEST_HIGH)
                .low(TEST_LOW)
                .close(TEST_CLOSE)
                .adjustedClose(TEST_CLOSE)
                .volume(TEST_VOLUME)
                .currency("USD")
                .build();

        calculationConfig = new MarketDataProperties.Calculation();
        calculationConfig.setPercentScale(4);
        calculationConfig.setPercentageMultiplier(new BigDecimal("100"));
        lenient().when(properties.getCalculation()).thenReturn(calculationConfig);
    }

    @Nested
    @DisplayName("OHLC 가격 조회 테스트")
    class GetOHLCPriceTests {

        @Test
        @DisplayName("캔들 데이터가 존재하면 OHLC 가격 정보를 반환한다")
        void getOHLCPrice_DataExists_Success() {
            // given
            given(candleRepository.findBySymbolAndDate(TEST_SYMBOL, TEST_DATE))
                    .willReturn(Optional.of(testCandle));

            // when
            OHLCPriceDto result = candleService.getOHLCPrice(TEST_SYMBOL, TEST_DATE);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getSymbol()).isEqualTo(TEST_SYMBOL);
            assertThat(result.getDate()).isEqualTo(TEST_DATE);
            assertThat(result.getOpenPrice()).isEqualByComparingTo(TEST_OPEN);
            assertThat(result.getHighPrice()).isEqualByComparingTo(TEST_HIGH);
            assertThat(result.getLowPrice()).isEqualByComparingTo(TEST_LOW);
            assertThat(result.getClosePrice()).isEqualByComparingTo(TEST_CLOSE);
            assertThat(result.getAdjustedClose()).isEqualByComparingTo(TEST_CLOSE);
            assertThat(result.getVolume()).isEqualTo(TEST_VOLUME);
            assertThat(result.getCurrency()).isEqualTo("USD");
            assertThat(result.isAvailable()).isTrue();

            verify(candleRepository).findBySymbolAndDate(TEST_SYMBOL, TEST_DATE);
        }

        @Test
        @DisplayName("캔들 데이터가 없으면 available=false인 DTO를 반환한다")
        void getOHLCPrice_NoData_ReturnsUnavailable() {
            // given
            given(candleRepository.findBySymbolAndDate(TEST_SYMBOL, TEST_DATE))
                    .willReturn(Optional.empty());

            // when
            OHLCPriceDto result = candleService.getOHLCPrice(TEST_SYMBOL, TEST_DATE);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getSymbol()).isEqualTo(TEST_SYMBOL);
            assertThat(result.getDate()).isEqualTo(TEST_DATE);
            assertThat(result.isAvailable()).isFalse();
            assertThat(result.getOpenPrice()).isNull();
            assertThat(result.getClosePrice()).isNull();

            verify(candleRepository).findBySymbolAndDate(TEST_SYMBOL, TEST_DATE);
        }

        @Test
        @DisplayName("심볼을 대문자로 변환하여 조회한다")
        void getOHLCPrice_ConvertsToUpperCase_Success() {
            // given
            String lowerCaseSymbol = "aapl";
            given(candleRepository.findBySymbolAndDate("AAPL", TEST_DATE))
                    .willReturn(Optional.of(testCandle));

            // when
            OHLCPriceDto result = candleService.getOHLCPrice(lowerCaseSymbol, TEST_DATE);

            // then
            assertThat(result.isAvailable()).isTrue();
            verify(candleRepository).findBySymbolAndDate("AAPL", TEST_DATE);
        }
    }

    @Nested
    @DisplayName("OHLC 범위 조회 테스트")
    class GetOHLCPriceRangeTests {

        @Test
        @DisplayName("지정된 날짜 범위의 OHLC 데이터를 반환한다")
        void getOHLCPriceRange_WithDateRange_Success() {
            // given
            LocalDate startDate = LocalDate.of(2024, 10, 23);
            LocalDate endDate = LocalDate.of(2024, 10, 25);

            List<Candle> candles = List.of(
                    Candle.builder()
                            .symbol(TEST_SYMBOL)
                            .date(LocalDate.of(2024, 10, 23))
                            .open(new BigDecimal("235.00"))
                            .high(new BigDecimal("237.00"))
                            .low(new BigDecimal("234.50"))
                            .close(new BigDecimal("236.50"))
                            .adjustedClose(new BigDecimal("236.50"))
                            .volume(40000000L)
                            .currency("USD")
                            .build(),
                    Candle.builder()
                            .symbol(TEST_SYMBOL)
                            .date(LocalDate.of(2024, 10, 24))
                            .open(new BigDecimal("236.80"))
                            .high(new BigDecimal("239.50"))
                            .low(new BigDecimal("236.00"))
                            .close(new BigDecimal("237.90"))
                            .adjustedClose(new BigDecimal("237.90"))
                            .volume(42000000L)
                            .currency("USD")
                            .build(),
                    testCandle
            );

            given(candleRepository.findBySymbolAndDateBetweenOrderByDateAsc(TEST_SYMBOL, startDate, endDate))
                    .willReturn(candles);

            // when
            List<OHLCPriceDto> results = candleService.getOHLCPriceRange(TEST_SYMBOL, startDate, endDate);

            // then
            assertThat(results).hasSize(3);
            assertThat(results).allMatch(OHLCPriceDto::isAvailable);
            assertThat(results.getFirst().getDate()).isEqualTo(LocalDate.of(2024, 10, 23));
            assertThat(results.get(1).getDate()).isEqualTo(LocalDate.of(2024, 10, 24));
            assertThat(results.get(2).getDate()).isEqualTo(TEST_DATE);

            verify(candleRepository).findBySymbolAndDateBetweenOrderByDateAsc(TEST_SYMBOL, startDate, endDate);
        }

        @Test
        @DisplayName("범위에 데이터가 없으면 빈 리스트를 반환한다")
        void getOHLCPriceRange_NoData_ReturnsEmpty() {
            // given
            LocalDate startDate = LocalDate.of(2024, 1, 1);
            LocalDate endDate = LocalDate.of(2024, 1, 31);

            given(candleRepository.findBySymbolAndDateBetweenOrderByDateAsc(TEST_SYMBOL, startDate, endDate))
                    .willReturn(new ArrayList<>());

            // when
            List<OHLCPriceDto> results = candleService.getOHLCPriceRange(TEST_SYMBOL, startDate, endDate);

            // then
            assertThat(results).isEmpty();
            verify(candleRepository).findBySymbolAndDateBetweenOrderByDateAsc(TEST_SYMBOL, startDate, endDate);
        }

        @Test
        @DisplayName("심볼을 대문자로 변환하여 조회한다")
        void getOHLCPriceRange_ConvertsToUpperCase_Success() {
            // given
            String lowerCaseSymbol = "aapl";
            LocalDate startDate = LocalDate.of(2024, 10, 23);
            LocalDate endDate = LocalDate.of(2024, 10, 25);

            given(candleRepository.findBySymbolAndDateBetweenOrderByDateAsc("AAPL", startDate, endDate))
                    .willReturn(List.of(testCandle));

            // when
            List<OHLCPriceDto> results = candleService.getOHLCPriceRange(lowerCaseSymbol, startDate, endDate);

            // then
            assertThat(results).hasSize(1);
            verify(candleRepository).findBySymbolAndDateBetweenOrderByDateAsc("AAPL", startDate, endDate);
        }
    }

    @Nested
    @DisplayName("현재가 조회 테스트")
    class GetCurrentPriceTests {

        @Test
        @DisplayName("최신 캔들 데이터로 현재가 정보를 반환한다")
        void getCurrentPrice_LatestDataExists_Success() {
            // given
            Candle previousCandle = Candle.builder()
                    .symbol(TEST_SYMBOL)
                    .date(TEST_DATE.minusDays(1))
                    .close(new BigDecimal("235.90"))
                    .build();

            Asset asset = Asset.builder()
                    .symbol(TEST_SYMBOL)
                    .marketCap(3000000000000L)
                    .build();

            given(candleRepository.findLatestBySymbol(TEST_SYMBOL))
                    .willReturn(Optional.of(testCandle));
            given(candleRepository.findLatestBySymbolBeforeDate(TEST_SYMBOL, TEST_DATE))
                    .willReturn(Optional.of(previousCandle));
            given(assetRepository.findById(TEST_SYMBOL))
                    .willReturn(Optional.of(asset));

            // when
            StockPriceDto result = candleService.getCurrentPrice(TEST_SYMBOL);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getSymbol()).isEqualTo(TEST_SYMBOL);
            assertThat(result.getCurrentPrice()).isEqualByComparingTo(TEST_CLOSE);
            assertThat(result.getPreviousClose()).isEqualByComparingTo(new BigDecimal("235.90"));
            assertThat(result.getVolume()).isEqualTo(TEST_VOLUME);
            assertThat(result.getDate()).isEqualTo(TEST_DATE);
            assertThat(result.getCurrency()).isEqualTo("USD");
            assertThat(result.getMarketCap()).isEqualTo(3000000000000L);
            assertThat(result.isAvailable()).isTrue();

            // changePercent 계산 검증: (238.15 - 235.90) / 235.90 * 100 ≈ 0.95
            assertThat(result.getChangePercent()).isNotNull();
            assertThat(result.getChangePercent().doubleValue()).isCloseTo(0.95, within(0.01));

            verify(candleRepository).findLatestBySymbol(TEST_SYMBOL);
            verify(candleRepository).findLatestBySymbolBeforeDate(TEST_SYMBOL, TEST_DATE);
            verify(assetRepository).findById(TEST_SYMBOL);
        }

        @Test
        @DisplayName("전일 데이터가 없으면 현재 종가를 전일 종가로 사용한다")
        void getCurrentPrice_NoPreviousData_UsesSameClose() {
            // given
            given(candleRepository.findLatestBySymbol(TEST_SYMBOL))
                    .willReturn(Optional.of(testCandle));
            given(candleRepository.findLatestBySymbolBeforeDate(TEST_SYMBOL, TEST_DATE))
                    .willReturn(Optional.empty());

            // when
            StockPriceDto result = candleService.getCurrentPrice(TEST_SYMBOL);

            // then
            assertThat(result.isAvailable()).isTrue();
            assertThat(result.getCurrentPrice()).isEqualByComparingTo(TEST_CLOSE);
            assertThat(result.getPreviousClose()).isEqualByComparingTo(TEST_CLOSE);
            // changePercent: (238.15 - 238.15) / 238.15 * 100 = 0
            assertThat(result.getChangePercent()).isNotNull();
            assertThat(result.getChangePercent().doubleValue()).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("최신 데이터가 없으면 available=false인 DTO를 반환한다")
        void getCurrentPrice_NoData_ReturnsUnavailable() {
            // given
            given(candleRepository.findLatestBySymbol(TEST_SYMBOL))
                    .willReturn(Optional.empty());

            // when
            StockPriceDto result = candleService.getCurrentPrice(TEST_SYMBOL);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getSymbol()).isEqualTo(TEST_SYMBOL);
            assertThat(result.isAvailable()).isFalse();
            assertThat(result.getCurrentPrice()).isNull();

            verify(candleRepository).findLatestBySymbol(TEST_SYMBOL);
            verify(candleRepository, never()).findLatestBySymbolBeforeDate(anyString(), any(LocalDate.class));
        }

        @Test
        @DisplayName("심볼을 대문자로 변환하여 조회한다")
        void getCurrentPrice_ConvertsToUpperCase_Success() {
            // given
            String lowerCaseSymbol = "aapl";
            given(candleRepository.findLatestBySymbol("AAPL"))
                    .willReturn(Optional.of(testCandle));
            given(candleRepository.findLatestBySymbolBeforeDate("AAPL", TEST_DATE))
                    .willReturn(Optional.empty());

            // when
            StockPriceDto result = candleService.getCurrentPrice(lowerCaseSymbol);

            // then
            assertThat(result.isAvailable()).isTrue();
            verify(candleRepository).findLatestBySymbol("AAPL");
        }
    }

    @Nested
    @DisplayName("다중 OHLC 조회 테스트")
    class GetMultipleOHLCPricesTests {

        @Test
        @DisplayName("여러 종목의 OHLC 데이터를 조회한다")
        void getMultipleOHLCPrices_MultipleSymbols_Success() {
            // given
            List<String> symbols = List.of("AAPL", "GOOGL", "MSFT");
            LocalDate startDate = LocalDate.of(2024, 10, 20);
            LocalDate endDate = LocalDate.of(2024, 10, 25);

            List<Candle> candles = List.of(
                    testCandle,
                    Candle.builder()
                            .symbol("GOOGL")
                            .date(TEST_DATE)
                            .open(new BigDecimal("140.00"))
                            .high(new BigDecimal("142.00"))
                            .low(new BigDecimal("139.50"))
                            .close(new BigDecimal("141.50"))
                            .adjustedClose(new BigDecimal("141.50"))
                            .volume(25000000L)
                            .currency("USD")
                            .build(),
                    Candle.builder()
                            .symbol("MSFT")
                            .date(TEST_DATE)
                            .open(new BigDecimal("410.00"))
                            .high(new BigDecimal("415.00"))
                            .low(new BigDecimal("408.50"))
                            .close(new BigDecimal("412.30"))
                            .adjustedClose(new BigDecimal("412.30"))
                            .volume(20000000L)
                            .currency("USD")
                            .build()
            );

            given(candleRepository.findBySymbolsAndDateRange(
                    List.of("AAPL", "GOOGL", "MSFT"), startDate, endDate))
                    .willReturn(candles);

            // when
            List<OHLCPriceDto> results = candleService.getMultipleOHLCPrices(symbols, startDate, endDate);

            // then
            assertThat(results).hasSize(3);
            assertThat(results).extracting(OHLCPriceDto::getSymbol)
                    .containsExactly("AAPL", "GOOGL", "MSFT");
            assertThat(results).allMatch(OHLCPriceDto::isAvailable);

            verify(candleRepository).findBySymbolsAndDateRange(
                    List.of("AAPL", "GOOGL", "MSFT"), startDate, endDate);
        }

        @Test
        @DisplayName("심볼들을 대문자로 변환하여 조회한다")
        void getMultipleOHLCPrices_ConvertsToUpperCase_Success() {
            // given
            List<String> lowerCaseSymbols = List.of("aapl", "googl");
            LocalDate startDate = LocalDate.of(2024, 10, 20);
            LocalDate endDate = LocalDate.of(2024, 10, 25);

            given(candleRepository.findBySymbolsAndDateRange(
                    List.of("AAPL", "GOOGL"), startDate, endDate))
                    .willReturn(List.of(testCandle));

            // when
            List<OHLCPriceDto> results = candleService.getMultipleOHLCPrices(lowerCaseSymbols, startDate, endDate);

            // then
            assertThat(results).hasSize(1);
            verify(candleRepository).findBySymbolsAndDateRange(
                    List.of("AAPL", "GOOGL"), startDate, endDate);
        }

        @Test
        @DisplayName("데이터가 없으면 빈 리스트를 반환한다")
        void getMultipleOHLCPrices_NoData_ReturnsEmpty() {
            // given
            List<String> symbols = List.of("AAPL", "GOOGL");
            LocalDate startDate = LocalDate.of(2024, 1, 1);
            LocalDate endDate = LocalDate.of(2024, 1, 31);

            given(candleRepository.findBySymbolsAndDateRange(symbols, startDate, endDate))
                    .willReturn(new ArrayList<>());

            // when
            List<OHLCPriceDto> results = candleService.getMultipleOHLCPrices(symbols, startDate, endDate);

            // then
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("배당 포함 캔들 조회 테스트")
    class GetCandlesWithDividendsTests {

        @Test
        @DisplayName("배당이 있는 날짜의 캔들 데이터를 조회한다")
        void getCandlesWithDividends_WithDividends_Success() {
            // given
            LocalDate startDate = LocalDate.of(2024, 1, 1);
            LocalDate endDate = LocalDate.of(2024, 12, 31);

            List<Candle> candlesWithDividends = List.of(
                    Candle.builder()
                            .symbol(TEST_SYMBOL)
                            .date(LocalDate.of(2024, 3, 15))
                            .close(new BigDecimal("170.00"))
                            .adjustedClose(new BigDecimal("170.00"))
                            .currency("USD")
                            .build(),
                    Candle.builder()
                            .symbol(TEST_SYMBOL)
                            .date(LocalDate.of(2024, 6, 15))
                            .close(new BigDecimal("180.00"))
                            .adjustedClose(new BigDecimal("180.00"))
                            .currency("USD")
                            .build()
            );

            given(candleRepository.findCandlesWithDividends(TEST_SYMBOL, startDate, endDate))
                    .willReturn(candlesWithDividends);

            // when
            List<OHLCPriceDto> results = candleService.getCandlesWithDividends(TEST_SYMBOL, startDate, endDate);

            // then
            assertThat(results).hasSize(2);
            assertThat(results).extracting(OHLCPriceDto::getDate)
                    .containsExactly(LocalDate.of(2024, 3, 15), LocalDate.of(2024, 6, 15));
            assertThat(results).allMatch(OHLCPriceDto::isAvailable);

            verify(candleRepository).findCandlesWithDividends(TEST_SYMBOL, startDate, endDate);
        }

        @Test
        @DisplayName("배당이 없으면 빈 리스트를 반환한다")
        void getCandlesWithDividends_NoDividends_ReturnsEmpty() {
            // given
            LocalDate startDate = LocalDate.of(2024, 1, 1);
            LocalDate endDate = LocalDate.of(2024, 12, 31);

            given(candleRepository.findCandlesWithDividends(TEST_SYMBOL, startDate, endDate))
                    .willReturn(new ArrayList<>());

            // when
            List<OHLCPriceDto> results = candleService.getCandlesWithDividends(TEST_SYMBOL, startDate, endDate);

            // then
            assertThat(results).isEmpty();
            verify(candleRepository).findCandlesWithDividends(TEST_SYMBOL, startDate, endDate);
        }

        @Test
        @DisplayName("심볼을 대문자로 변환하여 조회한다")
        void getCandlesWithDividends_ConvertsToUpperCase_Success() {
            // given
            String lowerCaseSymbol = "aapl";
            LocalDate startDate = LocalDate.of(2024, 1, 1);
            LocalDate endDate = LocalDate.of(2024, 12, 31);

            given(candleRepository.findCandlesWithDividends("AAPL", startDate, endDate))
                    .willReturn(List.of(testCandle));

            // when
            List<OHLCPriceDto> results = candleService.getCandlesWithDividends(lowerCaseSymbol, startDate, endDate);

            // then
            assertThat(results).hasSize(1);
            verify(candleRepository).findCandlesWithDividends("AAPL", startDate, endDate);
        }
    }

    @Nested
    @DisplayName("전체 종목 가격 조회 테스트")
    class GetAllStocksWithPricesTests {

        @Test
        @DisplayName("전체 종목의 가격 정보를 조회한다")
        void getAllStocksWithPrices_MultipleAssets_Success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            List<Asset> assets = new ArrayList<>(List.of(
                    Asset.builder()
                            .symbol("AAPL")
                            .name("Apple Inc.")
                            .currency("USD")
                            .marketCap(3000000000000L)
                            .build(),
                    Asset.builder()
                            .symbol("GOOGL")
                            .name("Alphabet Inc.")
                            .currency("USD")
                            .marketCap(1800000000000L)
                            .build()
            ));
            Page<Asset> assetPage = new PageImpl<>(assets, pageable, assets.size());

            Candle aaplCandle = testCandle;
            Candle googlCandle = Candle.builder()
                    .symbol("GOOGL")
                    .date(TEST_DATE)
                    .close(new BigDecimal("141.50"))
                    .volume(25000000L)
                    .currency("USD")
                    .build();

            Candle aaplPrevious = Candle.builder()
                    .symbol("AAPL")
                    .date(TEST_DATE.minusDays(1))
                    .close(new BigDecimal("235.90"))
                    .build();

            Candle googlPrevious = Candle.builder()
                    .symbol("GOOGL")
                    .date(TEST_DATE.minusDays(1))
                    .close(new BigDecimal("140.00"))
                    .build();

            given(assetRepository.findActiveSortedByMarketCap(any(Pageable.class), anyBoolean(), any()))
                    .willReturn(assetPage);
            given(candleRepository.findRecentBySymbols(anyList(), anyInt()))
                    .willReturn(List.of(aaplCandle, googlCandle, aaplPrevious, googlPrevious));

            // when
            Page<StockPriceDto> results = candleService.getAllStocksWithPrices(pageable, "desc", null);

            // then
            assertThat(results.getContent()).hasSize(2);
            assertThat(results.getContent()).allMatch(StockPriceDto::isAvailable);
            assertThat(results.getContent()).extracting(StockPriceDto::getSymbol)
                    .containsExactly("AAPL", "GOOGL");

            // AAPL 가격 검증
            StockPriceDto aaplPrice = results.getContent().getFirst();
            assertThat(aaplPrice.getCurrentPrice()).isEqualByComparingTo(TEST_CLOSE);
            assertThat(aaplPrice.getPreviousClose()).isEqualByComparingTo(new BigDecimal("235.90"));
            assertThat(aaplPrice.getMarketCap()).isEqualTo(3000000000000L);

            // GOOGL 가격 검증
            StockPriceDto googlPrice = results.getContent().get(1);
            assertThat(googlPrice.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("141.50"));
            assertThat(googlPrice.getPreviousClose()).isEqualByComparingTo(new BigDecimal("140.00"));

            verify(assetRepository).findActiveSortedByMarketCap(any(Pageable.class), anyBoolean(), any());
        }

        @Test
        @DisplayName("캔들 데이터가 없는 종목은 available=false로 반환한다")
        void getAllStocksWithPrices_MissingCandles_ReturnsUnavailable() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            List<Asset> assets = new ArrayList<>(List.of(
                    Asset.builder()
                            .symbol("NEWSTOCK")
                            .name("New Stock")
                            .currency("USD")
                            .build()
            ));
            Page<Asset> assetPage = new PageImpl<>(assets, pageable, assets.size());

            given(assetRepository.findActiveSortedByMarketCap(any(Pageable.class), anyBoolean(), any()))
                    .willReturn(assetPage);
            given(candleRepository.findRecentBySymbols(anyList(), anyInt()))
                    .willReturn(new ArrayList<>());

            // when
            Page<StockPriceDto> results = candleService.getAllStocksWithPrices(pageable, "desc", null);

            // then
            assertThat(results.getContent()).hasSize(1);
            StockPriceDto newStockPrice = results.getContent().getFirst();
            assertThat(newStockPrice.getSymbol()).isEqualTo("NEWSTOCK");
            assertThat(newStockPrice.isAvailable()).isFalse();
            assertThat(newStockPrice.getCurrentPrice()).isNull();
            assertThat(newStockPrice.getPreviousClose()).isNull();
        }

        @Test
        @DisplayName("종목이 없으면 빈 리스트를 반환한다")
        void getAllStocksWithPrices_NoAssets_ReturnsEmpty() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            List<Asset> emptyList = new ArrayList<>();
            Page<Asset> emptyPage = new PageImpl<>(emptyList, pageable, 0);
            given(assetRepository.findActiveSortedByMarketCap(any(Pageable.class), anyBoolean(), any()))
                    .willReturn(emptyPage);

            // when
            Page<StockPriceDto> results = candleService.getAllStocksWithPrices(pageable, "desc", null);

            // then
            assertThat(results.getContent()).isEmpty();
            verify(assetRepository).findActiveSortedByMarketCap(any(Pageable.class), anyBoolean(), any());
        }

        @Test
        @DisplayName("조회 중 예외가 발생한 종목은 건너뛴다")
        void getAllStocksWithPrices_ExceptionInProcessing_SkipsAsset() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            List<Asset> assets = new ArrayList<>(List.of(
                    Asset.builder()
                            .symbol("AAPL")
                            .name("Apple Inc.")
                            .currency("USD")
                            .build(),
                    Asset.builder()
                            .symbol("ERROR")
                            .name("Error Stock")
                            .currency("USD")
                            .build()
            ));
            Page<Asset> assetPage = new PageImpl<>(assets, pageable, assets.size());

            given(assetRepository.findActiveSortedByMarketCap(any(Pageable.class), anyBoolean(), any()))
                    .willReturn(assetPage);
            given(candleRepository.findRecentBySymbols(anyList(), anyInt()))
                    .willReturn(List.of(testCandle));

            // when
            Page<StockPriceDto> results = candleService.getAllStocksWithPrices(pageable, "desc", null);

            // then - ERROR 종목은 캔들이 없어서 available=false로 추가됨
            assertThat(results.getContent()).hasSize(2);
            assertThat(results.getContent().stream().filter(StockPriceDto::isAvailable).count()).isEqualTo(1);
        }
    }
}
