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
import com.muscat.trade.domain.dto.TradePosition;
import com.muscat.trade.domain.dto.response.HoldingResponseDto;
import com.muscat.trade.domain.dto.response.PortfolioSummary;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.PortfolioSummaryProjection;
import com.muscat.trade.domain.repository.TradeRepository;
import java.time.LocalDate;
import com.muscat.trade.domain.service.DividendPayoutService;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import java.math.BigDecimal;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("HoldingsService 단위 테스트")
class HoldingsServiceImplTest {

  @Mock
  private HoldingsRepository holdingsRepository;
  @Mock
  private TradeLogger tradeLogger;
  @Mock
  private MarketServiceClientWrapper marketServiceClientWrapper;
  @Mock
  private DividendPayoutService dividendPayoutService;
  @Mock
  private TradeRepository tradeRepository;

  @InjectMocks
  private HoldingsServiceImpl holdingsService;

  private static final String TEST_USER_ID = "test-user-uuid";
  private static final Long TEST_ACCOUNT_ID = 1L;
  private static final String TEST_SYMBOL = "AAPL";
  private static final BigDecimal TEST_QUANTITY = new BigDecimal("10");
  private static final BigDecimal TEST_AVG_PRICE = new BigDecimal("150.00");
  private static final BigDecimal TEST_CURRENT_PRICE = new BigDecimal("160.00");

  private Holdings testHolding;

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
      given(dividendPayoutService.dividendsOnHeldShares(TEST_USER_ID))
        .willReturn(Map.of(new TradePosition(TEST_USER_ID, TEST_ACCOUNT_ID, TEST_SYMBOL), new BigDecimal("2.12")));

      List<HoldingResponseDto> result = holdingsService.getPortfolio(TEST_USER_ID, TEST_ACCOUNT_ID);

      assertThat(result.getFirst().totalDividends()).isEqualByComparingTo("2.12");
    }

    @Test
    @DisplayName("보유 줄의 분할 비율은 지금 남은 매수 묶음 가운데 분할로 맞춘 가장 이른 체결의 누적 비율")
    void getPortfolio_SplitRatioOfEarliestRemainingLot() {
      Holdings msft = Holdings.builder().userId(TEST_USER_ID).accountId(TEST_ACCOUNT_ID).symbol("MSFT")
        .totalQuantity(new BigDecimal("2")).avgPurchasePrice(new BigDecimal("400.00"))
        .totalInvestedAmount(new BigDecimal("800.00")).build();
      given(holdingsRepository.findByUserIdAndAccountId(TEST_USER_ID, TEST_ACCOUNT_ID))
        .willReturn(List.of(testHolding, msft));
      given(marketServiceClientWrapper.getCurrentPrices(List.of(TEST_SYMBOL, "MSFT"))).willReturn(Map.of());
      given(tradeRepository.findByUserIdAndStatusOrderByExecutedAtAscIdAsc(TEST_USER_ID, TradeStatus.COMPLETED))
        .willReturn(List.of(
          lot(2L, TEST_SYMBOL, TradeType.BUY, "6", LocalDate.of(2024, 2, 1), "3"),
          lot(1L, TEST_SYMBOL, TradeType.BUY, "6", LocalDate.of(2024, 1, 2), "6"),
          lot(3L, "MSFT", TradeType.BUY, "2", LocalDate.of(2024, 7, 1), null)));

      List<HoldingResponseDto> result = holdingsService.getPortfolio(TEST_USER_ID, TEST_ACCOUNT_ID);

      assertThat(result.get(0).splitRatio()).isEqualByComparingTo("6");
      assertThat(result.get(1).splitRatio()).isNull();
    }

    @Test
    @DisplayName("다 판 매수 묶음의 분할은 빼고 남은 묶음 기준, 다 판 뒤 분할 뒤에 다시 산 자리는 빈 값")
    void getPortfolio_SplitRatioIgnoresSoldLots() {
      Holdings msft = Holdings.builder().userId(TEST_USER_ID).accountId(TEST_ACCOUNT_ID).symbol("MSFT")
        .totalQuantity(new BigDecimal("2")).avgPurchasePrice(new BigDecimal("400.00"))
        .totalInvestedAmount(new BigDecimal("800.00")).build();
      given(holdingsRepository.findByUserIdAndAccountId(TEST_USER_ID, TEST_ACCOUNT_ID))
        .willReturn(List.of(testHolding, msft));
      given(marketServiceClientWrapper.getCurrentPrices(List.of(TEST_SYMBOL, "MSFT"))).willReturn(Map.of());
      given(tradeRepository.findByUserIdAndStatusOrderByExecutedAtAscIdAsc(TEST_USER_ID, TradeStatus.COMPLETED))
        .willReturn(List.of(
          lot(1L, TEST_SYMBOL, TradeType.BUY, "40", LocalDate.of(2024, 1, 2), "40"),
          lot(2L, TEST_SYMBOL, TradeType.BUY, "10", LocalDate.of(2024, 3, 1), "10"),
          lot(3L, TEST_SYMBOL, TradeType.SELL, "40", LocalDate.of(2024, 4, 1), "10"),
          lot(4L, "MSFT", TradeType.BUY, "4", LocalDate.of(2024, 1, 2), "4"),
          lot(5L, "MSFT", TradeType.SELL, "4", LocalDate.of(2024, 2, 1), "4"),
          lot(6L, "MSFT", TradeType.BUY, "2", LocalDate.of(2024, 7, 1), null)));

      List<HoldingResponseDto> result = holdingsService.getPortfolio(TEST_USER_ID, TEST_ACCOUNT_ID);

      assertThat(result.get(0).splitRatio()).isEqualByComparingTo("10");
      assertThat(result.get(1).splitRatio()).isNull();
    }

    private Trade lot(long id, String symbol, TradeType type, String quantity, LocalDate tradeDate, String ratio) {
      return Trade.builder().id(id).userId(TEST_USER_ID).accountId(TEST_ACCOUNT_ID).symbol(symbol).tradeType(type)
        .quantity(new BigDecimal(quantity)).tradeDate(tradeDate).executedAt(tradeDate.atTime(10, 0))
        .splitRatio(ratio == null ? null : new BigDecimal(ratio)).status(TradeStatus.COMPLETED).build();
    }

    @Test
    @DisplayName("미실현 손익 · 수익률은 센트로 반올림한 평균 단가가 아니라 총 투자금액 기준")
    void getPortfolio_PnLFromInvestedAmount() {
      Holdings split = Holdings.builder().userId(TEST_USER_ID).accountId(TEST_ACCOUNT_ID).symbol(TEST_SYMBOL)
        .totalQuantity(new BigDecimal("300")).avgPurchasePrice(new BigDecimal("33.33"))
        .totalInvestedAmount(new BigDecimal("10000.00")).build();
      given(holdingsRepository.findByUserIdAndAccountId(TEST_USER_ID, TEST_ACCOUNT_ID)).willReturn(List.of(split));
      given(marketServiceClientWrapper.getCurrentPrices(List.of(TEST_SYMBOL)))
        .willReturn(Map.of(TEST_SYMBOL, currentPrice(new BigDecimal("40.00"))));

      HoldingResponseDto dto = holdingsService.getPortfolio(TEST_USER_ID, TEST_ACCOUNT_ID).getFirst();

      assertThat(dto.unrealizedPnL()).isEqualByComparingTo("2000.00");
      assertThat(dto.returnRate()).isEqualByComparingTo("20");
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

  private static StockPriceDto currentPrice(BigDecimal price) {
    return new StockPriceDto(TEST_SYMBOL, null, price, null, null, null,
      null, null, null, null, null, null, null, null, "USD", true, null, null);
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
      given(marketServiceClientWrapper.getCurrentPrices(List.of(TEST_SYMBOL)))
        .willReturn(Map.of(TEST_SYMBOL, currentPrice(TEST_CURRENT_PRICE)));

      // when
      PortfolioSummary result = holdingsService.getPortfolioSummary(TEST_USER_ID);

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
    @DisplayName("종목별 평가손익 · 수익률도 센트로 반올림한 평균 단가가 아니라 총 투자금액 기준")
    void getPortfolioSummary_SymbolPnLFromInvestedAmount() {
      Holdings split = Holdings.builder().userId(TEST_USER_ID).accountId(TEST_ACCOUNT_ID).symbol(TEST_SYMBOL)
        .totalQuantity(new BigDecimal("300")).avgPurchasePrice(new BigDecimal("33.33"))
        .totalInvestedAmount(new BigDecimal("10000.00")).build();
      given(holdingsRepository.findByUserId(TEST_USER_ID)).willReturn(List.of(split));
      given(holdingsRepository.calculatePortfolioSummary(TEST_USER_ID))
        .willReturn(new PortfolioSummaryProjection(new BigDecimal("10000.00"), 1));
      given(marketServiceClientWrapper.getCurrentPrices(List.of(TEST_SYMBOL)))
        .willReturn(Map.of(TEST_SYMBOL, currentPrice(new BigDecimal("40.00"))));

      PortfolioSummary result = holdingsService.getPortfolioSummary(TEST_USER_ID);

      assertThat(result.getSymbolUnrealizedPnL().get(TEST_SYMBOL)).isEqualByComparingTo("2000.00");
      assertThat(result.getSymbolReturnRates().get(TEST_SYMBOL)).isEqualByComparingTo("20");
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
      given(marketServiceClientWrapper.getCurrentPrices(List.of(TEST_SYMBOL))).willReturn(Map.of());

      // when
      PortfolioSummary result = holdingsService.getPortfolioSummary(TEST_USER_ID);

      // then
      assertThat(result.getTotalCurrentValue()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(result.getTotalUnrealizedPnL())
        .isEqualByComparingTo(BigDecimal.ZERO.subtract(TEST_QUANTITY.multiply(TEST_AVG_PRICE)));
    }

    @Test
    @DisplayName("현재가를 받지 않으면 보유 조회와 같은 시세 조회로 현재가를 받아 요약한다")
    void getPortfolioSummary_ServerPrices() {
      given(holdingsRepository.findByUserId(TEST_USER_ID)).willReturn(List.of(testHolding));
      given(holdingsRepository.calculatePortfolioSummary(TEST_USER_ID))
        .willReturn(new PortfolioSummaryProjection(TEST_QUANTITY.multiply(TEST_AVG_PRICE), 1));
      given(marketServiceClientWrapper.getCurrentPrices(List.of(TEST_SYMBOL)))
        .willReturn(Map.of(TEST_SYMBOL, currentPrice(TEST_CURRENT_PRICE)));

      PortfolioSummary result = holdingsService.getPortfolioSummary(TEST_USER_ID);

      assertThat(result.getTotalCurrentValue()).isEqualByComparingTo(TEST_QUANTITY.multiply(TEST_CURRENT_PRICE));
      assertThat(result.getHoldings()).extracting(HoldingResponseDto::currentPrice).containsExactly(TEST_CURRENT_PRICE);
    }

    @Test
    @DisplayName("시세 조회가 실패하면 현재가 없이 요약한다")
    void getPortfolioSummary_ServerPricesFail_SummarizesWithoutPrices() {
      given(holdingsRepository.findByUserId(TEST_USER_ID)).willReturn(List.of(testHolding));
      given(holdingsRepository.calculatePortfolioSummary(TEST_USER_ID))
        .willReturn(new PortfolioSummaryProjection(TEST_QUANTITY.multiply(TEST_AVG_PRICE), 1));
      given(marketServiceClientWrapper.getCurrentPrices(List.of(TEST_SYMBOL)))
        .willThrow(new RuntimeException("Market service unavailable"));

      PortfolioSummary result = holdingsService.getPortfolioSummary(TEST_USER_ID);

      assertThat(result.getTotalCurrentValue()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(result.getHoldingCount()).isEqualTo(1);
    }
  }
}
