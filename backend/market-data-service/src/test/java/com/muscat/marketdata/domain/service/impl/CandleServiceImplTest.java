package com.muscat.marketdata.domain.service.impl;

import com.muscat.marketdata.common.enums.StockSort;
import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
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

    @Mock
    private DividendRepository dividendRepository;

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

    private static Candle candle(LocalDate date, String close, String adjustedClose) {
        return Candle.builder()
                .symbol(TEST_SYMBOL)
                .date(date)
                .close(close == null ? null : new BigDecimal(close))
                .adjustedClose(adjustedClose == null ? null : new BigDecimal(adjustedClose))
                .currency("USD")
                .build();
    }

    // 종가가 비었거나 0 이하인 셋 + 조정 종가만 0 이하인 하나 + 정상 하나
    private static List<Candle> mixedCandles() {
        return List.of(
                candle(LocalDate.of(2024, 10, 21), null, "10"),
                candle(LocalDate.of(2024, 10, 22), "0", "0"),
                candle(LocalDate.of(2024, 10, 23), "-1.5", "-1.5"),
                candle(LocalDate.of(2024, 10, 24), "10", "-2.4"),
                candle(LocalDate.of(2024, 10, 25), "11", "10.5"));
    }

    private static void assertCleaned(List<OHLCPriceDto> results) {
        assertThat(results).extracting(OHLCPriceDto::getDate)
                .containsExactly(LocalDate.of(2024, 10, 24), LocalDate.of(2024, 10, 25));
        assertThat(results.get(0).getClosePrice()).isEqualByComparingTo("10");
        assertThat(results.get(0).getAdjustedClose()).isNull();
        assertThat(results.get(1).getAdjustedClose()).isEqualByComparingTo("10.5");
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

        @Test
        @DisplayName("종가가 0 이하인 캔들은 없는 것과 같이 available=false 다")
        void getOHLCPrice_NonPositiveClose_ReturnsUnavailable() {
            given(candleRepository.findBySymbolAndDate(TEST_SYMBOL, TEST_DATE))
                    .willReturn(Optional.of(candle(TEST_DATE, "0", "0")));

            OHLCPriceDto result = candleService.getOHLCPrice(TEST_SYMBOL, TEST_DATE);

            assertThat(result.isAvailable()).isFalse();
            assertThat(result.getClosePrice()).isNull();
        }

        @Test
        @DisplayName("조정 종가가 0 이하면 조정 종가만 비우고 종가는 준다")
        void getOHLCPrice_NonPositiveAdjustedClose_ReturnsNullAdjusted() {
            given(candleRepository.findBySymbolAndDate(TEST_SYMBOL, TEST_DATE))
                    .willReturn(Optional.of(candle(TEST_DATE, "10", "0")));

            OHLCPriceDto result = candleService.getOHLCPrice(TEST_SYMBOL, TEST_DATE);

            assertThat(result.isAvailable()).isTrue();
            assertThat(result.getClosePrice()).isEqualByComparingTo("10");
            assertThat(result.getAdjustedClose()).isNull();
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

        @Test
        @DisplayName("종가가 비었거나 0 이하인 행은 빼고, 조정 종가 0 이하는 비운다")
        void getOHLCPriceRange_InvalidPrices_Cleaned() {
            LocalDate startDate = LocalDate.of(2024, 10, 21);
            given(candleRepository.findBySymbolAndDateBetweenOrderByDateAsc(TEST_SYMBOL, startDate, TEST_DATE))
                    .willReturn(mixedCandles());

            assertCleaned(candleService.getOHLCPriceRange(TEST_SYMBOL, startDate, TEST_DATE));
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
            assertThat(result.getActive()).isTrue();

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
        @DisplayName("상장폐지된 종목이면 active=false 를 준다")
        void getCurrentPrice_DelistedAsset_NotActive() {
            // given
            Asset delisted = Asset.builder().symbol(TEST_SYMBOL).active(false).build();
            given(candleRepository.findLatestBySymbol(TEST_SYMBOL)).willReturn(Optional.of(testCandle));
            given(candleRepository.findLatestBySymbolBeforeDate(TEST_SYMBOL, TEST_DATE)).willReturn(Optional.empty());
            given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.of(delisted));

            // when
            StockPriceDto result = candleService.getCurrentPrice(TEST_SYMBOL);

            // then
            assertThat(result.isAvailable()).isTrue();
            assertThat(result.getActive()).isFalse();
        }

        @Test
        @DisplayName("종목 정보가 없으면 active=false 를 준다")
        void getCurrentPrice_NoAsset_NotActive() {
            // given
            given(candleRepository.findLatestBySymbol(TEST_SYMBOL)).willReturn(Optional.of(testCandle));
            given(candleRepository.findLatestBySymbolBeforeDate(TEST_SYMBOL, TEST_DATE)).willReturn(Optional.empty());
            given(assetRepository.findById(TEST_SYMBOL)).willReturn(Optional.empty());

            // when
            StockPriceDto result = candleService.getCurrentPrice(TEST_SYMBOL);

            // then
            assertThat(result.getActive()).isFalse();
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

        @Test
        @DisplayName("종가가 비었거나 0 이하인 행은 빼고, 조정 종가 0 이하는 비운다")
        void getMultipleOHLCPrices_InvalidPrices_Cleaned() {
            LocalDate startDate = LocalDate.of(2024, 10, 21);
            given(candleRepository.findBySymbolsAndDateRange(List.of(TEST_SYMBOL), startDate, TEST_DATE))
                    .willReturn(mixedCandles());

            assertCleaned(candleService.getMultipleOHLCPrices(List.of(TEST_SYMBOL), startDate, TEST_DATE));
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

        @Test
        @DisplayName("종가가 비었거나 0 이하인 행은 빼고, 조정 종가 0 이하는 비운다")
        void getCandlesWithDividends_InvalidPrices_Cleaned() {
            LocalDate startDate = LocalDate.of(2024, 10, 21);
            given(candleRepository.findCandlesWithDividends(TEST_SYMBOL, startDate, TEST_DATE))
                    .willReturn(mixedCandles());

            assertCleaned(candleService.getCandlesWithDividends(TEST_SYMBOL, startDate, TEST_DATE));
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
            Page<StockPriceDto> results = candleService.getAllStocksWithPrices(pageable, "desc", null, StockSort.MARKET_CAP);

            // then
            assertThat(results.getContent()).hasSize(2);
            assertThat(results.getContent()).allMatch(StockPriceDto::isAvailable);
            assertThat(results.getContent()).extracting(StockPriceDto::getSymbol)
                    .containsExactly("AAPL", "GOOGL");
            assertThat(results.getContent()).extracting(StockPriceDto::getDividendYieldPercent).containsOnlyNulls();

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
            Page<StockPriceDto> results = candleService.getAllStocksWithPrices(pageable, "desc", null, StockSort.MARKET_CAP);

            // then
            assertThat(results.getContent()).hasSize(1);
            StockPriceDto newStockPrice = results.getContent().getFirst();
            assertThat(newStockPrice.getSymbol()).isEqualTo("NEWSTOCK");
            assertThat(newStockPrice.isAvailable()).isFalse();
            assertThat(newStockPrice.getCurrentPrice()).isNull();
            assertThat(newStockPrice.getPreviousClose()).isNull();
        }

        private DividendRepository.SymbolAmount yearly(String symbol, String amount) {
            return new DividendRepository.SymbolAmount() {
                @Override
                public String getSymbol() {
                    return symbol;
                }

                @Override
                public BigDecimal getAmount() {
                    return new BigDecimal(amount);
                }
            };
        }

        @Test
        @DisplayName("배당수익률 정렬이면 수익률 순 조회를 쓰고, 최근 1년 배당 합 ÷ 최신 종가를 소수 둘째 자리 % 로 채운다. 종가 · 배당이 없거나 종가가 0 이면 비운다")
        void getAllStocksWithPrices_DividendYieldSort_FillsYield() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            LocalDate from = LocalDate.now().minusYears(1);
            List<Asset> assets = List.of(
                    Asset.builder().symbol("T").name("AT&T").currency("USD").latestClose(new BigDecimal("30")).build(),
                    Asset.builder().symbol("KO").name("Coca-Cola").currency("USD").latestClose(new BigDecimal("60")).build(),
                    Asset.builder().symbol("AAPL").name("Apple Inc.").currency("USD").latestClose(new BigDecimal("200")).build(),
                    Asset.builder().symbol("NOCLOSE").name("No Close").currency("USD").build(),
                    Asset.builder().symbol("NODIV").name("No Dividend").currency("USD").latestClose(new BigDecimal("10")).build(),
                    Asset.builder().symbol("ZERO").name("Zero Close").currency("USD").latestClose(BigDecimal.ZERO).build());
            given(assetRepository.findActiveSortedByDividendYield(pageable, false, null, from))
                    .willReturn(new PageImpl<>(assets, pageable, assets.size()));
            given(candleRepository.findRecentBySymbols(anyList(), anyInt())).willReturn(List.of(
                    Candle.builder().symbol("T").date(TEST_DATE).close(new BigDecimal("30")).volume(1L).currency("USD").build()));
            given(dividendRepository.sumAmountBySymbolsSince(List.of("T", "KO", "AAPL", "NOCLOSE", "NODIV", "ZERO"), from))
                    .willReturn(List.of(yearly("T", "1.0"), yearly("KO", "1.5"), yearly("AAPL", "4.0"), yearly("NOCLOSE", "9.0"),
                            yearly("ZERO", "1.0")));

            // when
            Page<StockPriceDto> results = candleService.getAllStocksWithPrices(pageable, "desc", null, StockSort.DIVIDEND_YIELD);

            // then
            assertThat(results.getContent()).extracting(StockPriceDto::getSymbol)
                    .containsExactly("T", "KO", "AAPL", "NOCLOSE", "NODIV", "ZERO");
            assertThat(results.getContent()).extracting(StockPriceDto::getDividendYieldPercent)
                    .containsExactly(new BigDecimal("3.33"), new BigDecimal("2.50"), new BigDecimal("2.00"), null, null, null);
            verify(assetRepository, never()).findActiveSortedByMarketCap(any(), anyBoolean(), any());
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
            Page<StockPriceDto> results = candleService.getAllStocksWithPrices(pageable, "desc", null, StockSort.MARKET_CAP);

            // then
            assertThat(results.getContent()).isEmpty();
            verify(assetRepository).findActiveSortedByMarketCap(any(Pageable.class), anyBoolean(), any());
            verifyNoInteractions(dividendRepository);
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
            Page<StockPriceDto> results = candleService.getAllStocksWithPrices(pageable, "desc", null, StockSort.MARKET_CAP);

            // then - ERROR 종목은 캔들이 없어서 available=false로 추가됨
            assertThat(results.getContent()).hasSize(2);
            assertThat(results.getContent().stream().filter(StockPriceDto::isAvailable).count()).isEqualTo(1);
        }
    }
}
