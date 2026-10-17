package com.muscat.trade.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.muscat.commonlib.dto.StockPriceDto;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.domain.dto.response.HoldingResponseDto;
import com.muscat.trade.domain.dto.response.PortfolioSummary;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.PortfolioSummaryProjection;
import com.muscat.trade.infra.client.BacktestServiceClient;
import com.muscat.trade.domain.service.DividendPayoutService;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import com.muscat.trade.infra.client.dto.InvestmentBacktestResultDto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.mock;
import java.time.Duration;

@ExtendWith(MockitoExtension.class)
@DisplayName("HoldingsService 단위 테스트")
class HoldingsServiceImplTest {

  @Mock
  private HoldingsRepository holdingsRepository;
  @Mock
  private TradeLogger tradeLogger;
  @Mock
  private BacktestServiceClient backtestServiceClient;
  @Mock
  private MarketServiceClientWrapper marketServiceClientWrapper;
  @Mock
  private DividendPayoutService dividendPayoutService;

  @InjectMocks
  private HoldingsServiceImpl holdingsService;

  private static final String TEST_USER_ID = "test-user-uuid";
  private static final Long TEST_ACCOUNT_ID = 1L;
  private static final String TEST_SYMBOL = "AAPL";
  private static final BigDecimal TEST_QUANTITY = new BigDecimal("10");
  private static final BigDecimal TEST_AVG_PRICE = new BigDecimal("150.00");
  private static final BigDecimal TEST_CURRENT_PRICE = new BigDecimal("160.00");

  private Holdings testHolding;
  private Map<String, BigDecimal> testCurrentPrices;

  @BeforeEach
  void setUp() {
    testHolding = Holdings.builder()
      .id(1L)
      .userId(TEST_USER_ID)
      .accountId(TEST_ACCOUNT_ID)
      .symbol(TEST_SYMBOL)
      .totalQuantity(TEST_QUANTITY)
      .avgPurchasePrice(TEST_AVG_PRICE)
      .totalInvestedAmount(TEST_QUANTITY.multiply(TEST_AVG_PRICE))
      .build();

    testCurrentPrices = new HashMap<>();
    testCurrentPrices.put(TEST_SYMBOL, TEST_CURRENT_PRICE);
  }

  @Nested
  @DisplayName("포트폴리오 조회 테스트")
  class GetPortfolioTests {

    @Test
    @DisplayName("계좌별 포트폴리오가 정상 조회된다")
    void getPortfolio_WithAccountId_Success() {
      // given
      List<Holdings> holdings = List.of(testHolding);
      given(holdingsRepository.findByUserIdAndAccountId(TEST_USER_ID, TEST_ACCOUNT_ID))
        .willReturn(holdings);

      StockPriceDto priceDto = new StockPriceDto(
        TEST_SYMBOL, null, TEST_CURRENT_PRICE, null, null, null,
        null, null, null, null, null, null, null, null, "USD", true, null, null);
      given(marketServiceClientWrapper.getCurrentPrices(List.of(TEST_SYMBOL))).willReturn(Map.of(TEST_SYMBOL, priceDto));

      // when
      List<HoldingResponseDto> result = holdingsService.getPortfolio(TEST_USER_ID, TEST_ACCOUNT_ID);

      // then
      assertThat(result).hasSize(1);
      HoldingResponseDto dto = result.getFirst();
      assertThat(dto.symbol()).isEqualTo(TEST_SYMBOL);
      assertThat(dto.totalQuantity()).isEqualByComparingTo(TEST_QUANTITY);
      assertThat(dto.avgPurchasePrice()).isEqualByComparingTo(TEST_AVG_PRICE);
      assertThat(dto.currentPrice()).isEqualByComparingTo(TEST_CURRENT_PRICE);

      verify(tradeLogger).logPortfolioAccess(eq(TEST_USER_ID), eq("ACCOUNT_PORTFOLIO"),
        eq(TEST_ACCOUNT_ID.toString()), eq(1));
    }

    @Test
    @DisplayName("포트폴리오 줄에 지금 들고 있는 주식이 받은 배당을 싣는다")
    void getPortfolio_IncludesDividendsOnHeldShares() {
      given(holdingsRepository.findByUserIdAndAccountId(TEST_USER_ID, TEST_ACCOUNT_ID)).willReturn(List.of(testHolding));
      given(marketServiceClientWrapper.getCurrentPrices(List.of(TEST_SYMBOL))).willReturn(Map.of());
      given(dividendPayoutService.dividendsOnHeldShares(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(new BigDecimal("2.12"));

      List<HoldingResponseDto> result = holdingsService.getPortfolio(TEST_USER_ID, TEST_ACCOUNT_ID);

      assertThat(result.getFirst().totalDividends()).isEqualByComparingTo("2.12");
    }

    @Test
    @DisplayName("보유 종목이 여럿이어도 현재가는 한 번에 받는다")
    void getPortfolio_ManySymbols_OneBatchCall() {
      Holdings msft = Holdings.builder().userId(TEST_USER_ID).accountId(TEST_ACCOUNT_ID).symbol("MSFT")
        .totalQuantity(new BigDecimal("2")).avgPurchasePrice(new BigDecimal("400.00"))
        .totalInvestedAmount(new BigDecimal("800.00")).build();
      given(holdingsRepository.findByUserIdAndAccountId(TEST_USER_ID, TEST_ACCOUNT_ID))
        .willReturn(List.of(testHolding, msft));
      StockPriceDto aapl = new StockPriceDto(
        TEST_SYMBOL, null, TEST_CURRENT_PRICE, null, null, null,
        null, null, null, null, null, null, null, null, "USD", true, null, null);
      StockPriceDto msftPrice = new StockPriceDto(
        "MSFT", null, new BigDecimal("410.00"), null, null, null,
        null, null, null, null, null, null, null, null, "USD", true, null, null);
      given(marketServiceClientWrapper.getCurrentPrices(List.of(TEST_SYMBOL, "MSFT")))
        .willReturn(Map.of(TEST_SYMBOL, aapl, "MSFT", msftPrice));

      List<HoldingResponseDto> result = holdingsService.getPortfolio(TEST_USER_ID, TEST_ACCOUNT_ID);

      assertThat(result).extracting(HoldingResponseDto::currentPrice)
        .usingElementComparator(BigDecimal::compareTo)
        .containsExactly(TEST_CURRENT_PRICE, new BigDecimal("410.00"));
      verify(marketServiceClientWrapper, times(1)).getCurrentPrices(List.of(TEST_SYMBOL, "MSFT"));
    }

    @Test
    @DisplayName("보유가 없으면 시세를 부르지 않는다")
    void getPortfolio_NoHoldings_NoPriceCall() {
      given(holdingsRepository.findByUserId(TEST_USER_ID)).willReturn(List.of());

      assertThat(holdingsService.getPortfolio(TEST_USER_ID, null)).isEmpty();
      verify(marketServiceClientWrapper, never()).getCurrentPrices(anyList());
    }

    @Test
    @DisplayName("두 계좌에 같은 종목이 있어도 종목마다 한 번만 묻는다")
    void getPortfolio_SameSymbolInTwoAccounts_AskedOnce() {
      Holdings otherAccount = Holdings.builder().userId(TEST_USER_ID).accountId(2L).symbol(TEST_SYMBOL)
        .totalQuantity(new BigDecimal("1")).avgPurchasePrice(TEST_AVG_PRICE).totalInvestedAmount(TEST_AVG_PRICE).build();
      given(holdingsRepository.findByUserId(TEST_USER_ID)).willReturn(List.of(testHolding, otherAccount));
      StockPriceDto aapl = new StockPriceDto(
        TEST_SYMBOL, null, TEST_CURRENT_PRICE, null, null, null,
        null, null, null, null, null, null, null, null, "USD", true, null, null);
      given(marketServiceClientWrapper.getCurrentPrices(List.of(TEST_SYMBOL))).willReturn(Map.of(TEST_SYMBOL, aapl));

      List<HoldingResponseDto> result = holdingsService.getPortfolio(TEST_USER_ID, null);

      assertThat(result).hasSize(2)
        .allSatisfy(h -> assertThat(h.currentPrice()).isEqualByComparingTo(TEST_CURRENT_PRICE));
    }

    @Test
    @DisplayName("전체 포트폴리오가 정상 조회된다 (accountId null)")
    void getPortfolio_WithoutAccountId_Success() {
      // given
      List<Holdings> holdings = List.of(testHolding);
      given(holdingsRepository.findByUserId(TEST_USER_ID)).willReturn(holdings);

      StockPriceDto priceDto = new StockPriceDto(
        TEST_SYMBOL, null, TEST_CURRENT_PRICE, null, null, null,
        null, null, null, null, null, null, null, null, "USD", true, null, null);
      given(marketServiceClientWrapper.getCurrentPrices(List.of(TEST_SYMBOL))).willReturn(Map.of(TEST_SYMBOL, priceDto));

      // when
      List<HoldingResponseDto> result = holdingsService.getPortfolio(TEST_USER_ID, null);

      // then
      assertThat(result).hasSize(1);
      verify(tradeLogger).logPortfolioAccess(eq(TEST_USER_ID), eq("USER_PORTFOLIO"),
        isNull(), eq(1));
    }

    @Test
    @DisplayName("현재가 조회 실패 시에도 포트폴리오가 조회된다")
    void getPortfolio_MarketPriceFailed_ReturnsWithoutPrice() {
      // given
      List<Holdings> holdings = List.of(testHolding);
      given(holdingsRepository.findByUserIdAndAccountId(TEST_USER_ID, TEST_ACCOUNT_ID))
        .willReturn(holdings);
      given(marketServiceClientWrapper.getCurrentPrices(List.of(TEST_SYMBOL)))
        .willThrow(new RuntimeException("Market service unavailable"));

      // when
      List<HoldingResponseDto> result = holdingsService.getPortfolio(TEST_USER_ID, TEST_ACCOUNT_ID);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.getFirst().currentPrice()).isNull();
    }

    @Test
    @DisplayName("시세 응답에 현재가가 없으면 현재가 없이 돌려준다")
    void getPortfolio_PriceMissingInResponse_ReturnsWithoutPrice() {
      given(holdingsRepository.findByUserId(TEST_USER_ID)).willReturn(List.of(testHolding));
      given(marketServiceClientWrapper.getCurrentPrices(List.of(TEST_SYMBOL)))
        .willReturn(Map.of(TEST_SYMBOL, mock(StockPriceDto.class)));

      List<HoldingResponseDto> result = holdingsService.getPortfolio(TEST_USER_ID, null);

      assertThat(result).hasSize(1);
      assertThat(result.getFirst().currentPrice()).isNull();
    }
  }

  @Nested
  @DisplayName("종목별 보유 조회 테스트")
  class GetHoldingBySymbolTests {

    @Test
    @DisplayName("보유 종목이 정상 조회된다")
    void getHoldingBySymbol_Found_Success() {
      // given
      given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.of(testHolding));

      // when
      HoldingResponseDto result = holdingsService.getHoldingBySymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL);

      // then
      assertThat(result).isNotNull();
      assertThat(result.symbol()).isEqualTo(TEST_SYMBOL);
      verify(tradeLogger).logPortfolioAccess(eq(TEST_USER_ID), eq("SYMBOL_HOLDING"),
        eq(TEST_ACCOUNT_ID + ":" + TEST_SYMBOL), eq(1));
    }

    @Test
    @DisplayName("보유하지 않은 종목 조회 시 null 반환")
    void getHoldingBySymbol_NotFound_ReturnsNull() {
      // given
      given(holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL))
        .willReturn(Optional.empty());

      // when
      HoldingResponseDto result = holdingsService.getHoldingBySymbol(
        TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL);

      // then
      assertThat(result).isNull();
      verify(tradeLogger).logPortfolioAccess(eq(TEST_USER_ID), eq("SYMBOL_HOLDING"),
        eq(TEST_ACCOUNT_ID + ":" + TEST_SYMBOL), eq(0));
    }
  }

  @Nested
  @DisplayName("포트폴리오 요약 조회 테스트")
  class GetPortfolioSummaryTests {

    @Test
    @DisplayName("포트폴리오 요약 정보가 정확히 계산된다")
    void getPortfolioSummary_Success() {
      // given
      List<Holdings> portfolio = List.of(testHolding);
      given(holdingsRepository.findByUserId(TEST_USER_ID)).willReturn(portfolio);

      PortfolioSummaryProjection projection =
        new PortfolioSummaryProjection(
          TEST_QUANTITY.multiply(TEST_AVG_PRICE), // totalInvestedAmount
          1 // holdingCount
        );
      given(holdingsRepository.calculatePortfolioSummary(TEST_USER_ID))
        .willReturn(projection);

      // when
      PortfolioSummary result = holdingsService.getPortfolioSummary(
        TEST_USER_ID, testCurrentPrices);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getTotalInvestedAmount())
        .isEqualByComparingTo(TEST_QUANTITY.multiply(TEST_AVG_PRICE));
      assertThat(result.getHoldingCount()).isEqualTo(1);

      // 평가액 = 수량 * 현재가 = 10 * 160 = 1600
      BigDecimal expectedCurrentValue = TEST_QUANTITY.multiply(TEST_CURRENT_PRICE);
      assertThat(result.getTotalCurrentValue()).isEqualByComparingTo(expectedCurrentValue);

      // 평가손익 = 1600 - 1500 = 100
      BigDecimal expectedPnL = expectedCurrentValue.subtract(
        TEST_QUANTITY.multiply(TEST_AVG_PRICE));
      assertThat(result.getTotalUnrealizedPnL()).isEqualByComparingTo(expectedPnL);

      verify(tradeLogger).logPortfolioAccess(eq(TEST_USER_ID), eq("PORTFOLIO_SUMMARY"),
        anyString(), eq(1));
    }

    @Test
    @DisplayName("현재가 없는 종목은 평가액 계산에서 제외된다")
    void getPortfolioSummary_NoPriceAvailable_ExcludesFromCalculation() {
      // given
      List<Holdings> portfolio = List.of(testHolding);
      given(holdingsRepository.findByUserId(TEST_USER_ID)).willReturn(portfolio);

      PortfolioSummaryProjection projection =
        new PortfolioSummaryProjection(
          TEST_QUANTITY.multiply(TEST_AVG_PRICE), // totalInvestedAmount
          1 // holdingCount
        );
      given(holdingsRepository.calculatePortfolioSummary(TEST_USER_ID))
        .willReturn(projection);

      // 현재가 정보 없음
      Map<String, BigDecimal> emptyPrices = new HashMap<>();

      // when
      PortfolioSummary result = holdingsService.getPortfolioSummary(TEST_USER_ID, emptyPrices);

      // then
      assertThat(result.getTotalCurrentValue()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(result.getTotalUnrealizedPnL())
        .isEqualByComparingTo(BigDecimal.ZERO.subtract(TEST_QUANTITY.multiply(TEST_AVG_PRICE)));
    }
  }

  @Nested
  @DisplayName("백테스트 포함 포트폴리오 조회 테스트")
  class GetPortfolioSummaryWithBacktestTests {

    @Test
    @DisplayName("백테스트 결과와 함께 포트폴리오가 조회된다")
    void getPortfolioSummaryWithBacktest_WithBacktestResult_Success() {
      // given
      List<Holdings> portfolio = List.of(testHolding);
      given(holdingsRepository.findByUserId(TEST_USER_ID)).willReturn(portfolio);

      PortfolioSummaryProjection projection =
        new PortfolioSummaryProjection(
          TEST_QUANTITY.multiply(TEST_AVG_PRICE), // totalInvestedAmount
          1 // holdingCount
        );
      given(holdingsRepository.calculatePortfolioSummary(TEST_USER_ID))
        .willReturn(projection);

      InvestmentBacktestResultDto backtestResult = InvestmentBacktestResultDto.builder()
        .userId(TEST_USER_ID)
        .status("COMPLETED")
        .backtestResult("{\"sharpeRatio\": 1.5}")
        .calculatedAt(LocalDateTime.now().minusHours(1))
        .build();
      given(backtestServiceClient.getCachedInvestmentBacktestResult(anyString(), eq(TEST_USER_ID)))
        .willReturn(backtestResult);

      // when
      PortfolioSummary result = holdingsService.getPortfolioSummaryWithBacktest(
        TEST_USER_ID, testCurrentPrices, "Bearer token");

      // then
      assertThat(result.isBacktestAvailable()).isTrue();
      assertThat(result.getBacktestStatus()).isEqualTo("COMPLETED");
      assertThat(result.getBacktestResult()).contains("sharpeRatio");

      verify(tradeLogger).logPortfolioAccess(eq(TEST_USER_ID), eq("PORTFOLIO_WITH_BACKTEST"),
        anyString(), eq(1));
    }

    @Test
    @DisplayName("백테스트 결과가 없어도 포트폴리오는 정상 조회된다")
    void getPortfolioSummaryWithBacktest_NoBacktestResult_ReturnsPortfolioOnly() {
      // given
      List<Holdings> portfolio = List.of(testHolding);
      given(holdingsRepository.findByUserId(TEST_USER_ID)).willReturn(portfolio);

      PortfolioSummaryProjection projection =
        new PortfolioSummaryProjection(
          TEST_QUANTITY.multiply(TEST_AVG_PRICE), // totalInvestedAmount
          1 // holdingCount
        );
      given(holdingsRepository.calculatePortfolioSummary(TEST_USER_ID))
        .willReturn(projection);

      given(backtestServiceClient.getCachedInvestmentBacktestResult(anyString(), eq(TEST_USER_ID)))
        .willReturn(null);

      // when
      PortfolioSummary result = holdingsService.getPortfolioSummaryWithBacktest(
        TEST_USER_ID, testCurrentPrices, "Bearer token");

      // then
      assertThat(result.isBacktestAvailable()).isFalse();
      assertThat(result.getBacktestStatus()).isNull();
    }

    @Test
    @DisplayName("백테스트 조회 실패 시 포트폴리오만 반환한다")
    void getPortfolioSummaryWithBacktest_BacktestFailed_ReturnsPortfolioOnly() {
      // given
      List<Holdings> portfolio = List.of(testHolding);
      given(holdingsRepository.findByUserId(TEST_USER_ID)).willReturn(portfolio);

      PortfolioSummaryProjection projection =
        new PortfolioSummaryProjection(
          TEST_QUANTITY.multiply(TEST_AVG_PRICE), // totalInvestedAmount
          1 // holdingCount
        );
      given(holdingsRepository.calculatePortfolioSummary(TEST_USER_ID))
        .willReturn(projection);

      given(backtestServiceClient.getCachedInvestmentBacktestResult(anyString(), eq(TEST_USER_ID)))
        .willThrow(new RuntimeException("Backtest service unavailable"));

      // when
      PortfolioSummary result = holdingsService.getPortfolioSummaryWithBacktest(
        TEST_USER_ID, testCurrentPrices, "Bearer token");

      // then
      assertThat(result.isBacktestAvailable()).isFalse();
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("24시간이 지난 백테스트 캐시는 없는 것으로 본다")
    void getPortfolioSummaryWithBacktest_ExpiredCache_NotAvailable() {
      given(holdingsRepository.findByUserId(TEST_USER_ID)).willReturn(List.of(testHolding));
      given(holdingsRepository.calculatePortfolioSummary(TEST_USER_ID))
        .willReturn(new PortfolioSummaryProjection(TEST_QUANTITY.multiply(TEST_AVG_PRICE), 1));
      given(backtestServiceClient.getCachedInvestmentBacktestResult(anyString(), eq(TEST_USER_ID)))
        .willReturn(InvestmentBacktestResultDto.builder().status("COMPLETED").backtestResult("{}")
          .calculatedAt(LocalDateTime.now().minusHours(25)).build());

      PortfolioSummary result = holdingsService.getPortfolioSummaryWithBacktest(
        TEST_USER_ID, testCurrentPrices, "Bearer token");

      assertThat(result.isBacktestAvailable()).isFalse();
      assertThat(result.getBacktestResult()).isNull();
    }

    @Test
    @DisplayName("백테스트 조회가 1초 안에 안 끝나면 기다리지 않고 포트폴리오만 돌려준다")
    void getPortfolioSummaryWithBacktest_Timeout_NotAvailable() {
      given(holdingsRepository.findByUserId(TEST_USER_ID)).willReturn(List.of(testHolding));
      given(holdingsRepository.calculatePortfolioSummary(TEST_USER_ID))
        .willReturn(new PortfolioSummaryProjection(TEST_QUANTITY.multiply(TEST_AVG_PRICE), 1));
      given(backtestServiceClient.getCachedInvestmentBacktestResult(anyString(), eq(TEST_USER_ID)))
        .willAnswer(call -> {
          Thread.sleep(1500);
          return null;
        });

      long started = System.nanoTime();
      PortfolioSummary result = holdingsService.getPortfolioSummaryWithBacktest(
        TEST_USER_ID, testCurrentPrices, "Bearer token");

      assertThat(result.isBacktestAvailable()).isFalse();
      assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1400));
    }
  }
}
