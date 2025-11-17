package com.muscat.trade.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;

import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Dividend;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.DividendRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.infra.client.MarketServiceClient;
import com.muscat.trade.infra.client.dto.DividendDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("DividendService 단위 테스트")
class DividendServiceImplTest {

  @Mock
  private DividendRepository dividendRepository;
  @Mock
  private TradeRepository tradeRepository;
  @Mock
  private MarketServiceClient marketServiceClient;

  @InjectMocks
  private DividendServiceImpl dividendService;

  private static final String TEST_USER_ID = "test-user-uuid";
  private static final Long TEST_ACCOUNT_ID = 1L;
  private static final String TEST_SYMBOL = "AAPL";
  private static final String TEST_TRADE_ID = "trade-uuid-001";
  private static final BigDecimal TEST_QUANTITY = new BigDecimal("100");
  private static final BigDecimal DIVIDEND_PER_SHARE = new BigDecimal("0.25");
  private static final BigDecimal TAX_RATE = new BigDecimal("0.154"); // 15.4%

  private Trade testBuyTrade;
  private DividendDto testDividendDto;
  private Dividend testDividend;

  @BeforeEach
  void setUp() {
    testBuyTrade = Trade.builder()
      .tradeId(TEST_TRADE_ID)
      .userId(TEST_USER_ID)
      .accountId(TEST_ACCOUNT_ID)
      .symbol(TEST_SYMBOL)
      .tradeType(TradeType.BUY)
      .quantity(TEST_QUANTITY)
      .price(new BigDecimal("150.00"))
      .tradeDate(LocalDate.of(2024, 1, 15))
      .build();

    testDividendDto = new DividendDto(
      "2024-03-15", // exDate
      DIVIDEND_PER_SHARE
    );

    testDividend = Dividend.builder()
      .dividendId("dividend-uuid-001")
      .userId(TEST_USER_ID)
      .accountId(TEST_ACCOUNT_ID)
      .symbol(TEST_SYMBOL)
      .tradeId(TEST_TRADE_ID)
      .shares(TEST_QUANTITY)
      .dividendPerShare(DIVIDEND_PER_SHARE)
      .grossAmount(TEST_QUANTITY.multiply(DIVIDEND_PER_SHARE))
      .taxAmount(TEST_QUANTITY.multiply(DIVIDEND_PER_SHARE).multiply(TAX_RATE))
      .netAmount(
        TEST_QUANTITY.multiply(DIVIDEND_PER_SHARE).multiply(BigDecimal.ONE.subtract(TAX_RATE)))
      .dividendDate(LocalDate.of(2024, 3, 15))
      .processedAt(LocalDateTime.now())
      .build();
  }

  @Nested
  @DisplayName("사용자 배당 전체 조회 테스트")
  class GetUserDividendsTests {

    @Test
    @DisplayName("여러 종목의 배당이 정상 조회된다")
    void getUserDividends_MultipleSymbols_Success() {
      // given
      List<String> symbols = List.of("AAPL", "MSFT");
      given(tradeRepository.findDistinctSymbolsByUserId(TEST_USER_ID))
        .willReturn(symbols);

      // AAPL 관련 설정
      Trade appleTrade = Trade.builder()
        .tradeId("apple-trade-id")
        .userId(TEST_USER_ID)
        .accountId(TEST_ACCOUNT_ID)
        .symbol("AAPL")
        .tradeType(TradeType.BUY)
        .quantity(new BigDecimal("50"))
        .tradeDate(LocalDate.of(2024, 1, 1))
        .build();

      given(tradeRepository.findByUserIdAndSymbolOrderByTradeDateAsc(TEST_USER_ID, "AAPL"))
        .willReturn(List.of(appleTrade));

      DividendDto appleDiv = new DividendDto("2024-02-15", new BigDecimal("0.25"));
      given(marketServiceClient.getDividends(eq("AAPL"), anyString(), anyString()))
        .willReturn(List.of(appleDiv));

      given(dividendRepository.existsByTradeIdAndDividendDate(anyString(), any(LocalDate.class)))
        .willReturn(false);

      Dividend savedAppleDividend = Dividend.builder()
        .dividendId("apple-div-id")
        .userId(TEST_USER_ID)
        .symbol("AAPL")
        .tradeId("apple-trade-id")
        .build();

      given(dividendRepository.findByUserIdAndSymbolOrderByDividendDateDesc(TEST_USER_ID, "AAPL"))
        .willReturn(List.of(savedAppleDividend));

      // MSFT 관련 설정
      Trade msftTrade = Trade.builder()
        .tradeId("msft-trade-id")
        .userId(TEST_USER_ID)
        .accountId(TEST_ACCOUNT_ID)
        .symbol("MSFT")
        .tradeType(TradeType.BUY)
        .quantity(new BigDecimal("30"))
        .tradeDate(LocalDate.of(2024, 1, 1))
        .build();

      given(tradeRepository.findByUserIdAndSymbolOrderByTradeDateAsc(TEST_USER_ID, "MSFT"))
        .willReturn(List.of(msftTrade));

      DividendDto msftDiv = new DividendDto("2024-02-20", new BigDecimal("0.75"));
      given(marketServiceClient.getDividends(eq("MSFT"), anyString(), anyString()))
        .willReturn(List.of(msftDiv));

      Dividend savedMsftDividend = Dividend.builder()
        .dividendId("msft-div-id")
        .userId(TEST_USER_ID)
        .symbol("MSFT")
        .tradeId("msft-trade-id")
        .build();

      given(dividendRepository.findByUserIdAndSymbolOrderByDividendDateDesc(TEST_USER_ID, "MSFT"))
        .willReturn(List.of(savedMsftDividend));

      given(tradeRepository.findById(anyString())).willReturn(Optional.of(appleTrade));

      // when
      List<Map<String, Object>> result = dividendService.getUserDividends(TEST_USER_ID);

      // then
      assertThat(result).hasSize(2);
      verify(tradeRepository).findDistinctSymbolsByUserId(TEST_USER_ID);
      verify(marketServiceClient, times(2)).getDividends(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("거래한 종목이 없으면 빈 리스트가 반환된다")
    void getUserDividends_NoTrades_ReturnsEmpty() {
      // given
      given(tradeRepository.findDistinctSymbolsByUserId(TEST_USER_ID))
        .willReturn(new ArrayList<>());

      // when
      List<Map<String, Object>> result = dividendService.getUserDividends(TEST_USER_ID);

      // then
      assertThat(result).isEmpty();
      verify(marketServiceClient, never()).getDividends(anyString(), anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("배당 캐싱 테스트")
  class GetDividendsWithCacheTests {

    @Test
    @DisplayName("신규 배당이 정상적으로 캐싱된다")
    void getDividendsWithCache_NewDividend_Success() {
      // given
      LocalDate startDate = LocalDate.of(2024, 1, 1);
      LocalDate endDate = LocalDate.of(2024, 12, 31);

      given(tradeRepository.findByUserIdAndSymbolOrderByTradeDateAsc(TEST_USER_ID, TEST_SYMBOL))
        .willReturn(List.of(testBuyTrade));

      given(marketServiceClient.getDividends(TEST_SYMBOL, startDate.toString(), endDate.toString()))
        .willReturn(List.of(testDividendDto));

      // 배당이 아직 캐시되지 않음
      given(dividendRepository.existsByTradeIdAndDividendDate(TEST_TRADE_ID,
        LocalDate.parse(testDividendDto.exDate())))
        .willReturn(false);

      given(
        dividendRepository.findByUserIdAndSymbolOrderByDividendDateDesc(TEST_USER_ID, TEST_SYMBOL))
        .willReturn(List.of(testDividend));

      given(tradeRepository.findById(TEST_TRADE_ID)).willReturn(Optional.of(testBuyTrade));

      // when
      List<Map<String, Object>> result = dividendService.getDividendsWithCache(
        TEST_USER_ID, TEST_SYMBOL, startDate, endDate);

      // then
      assertThat(result).isNotEmpty();
      verify(dividendRepository).save(any(Dividend.class));
      verify(dividendRepository).flush();

      Map<String, Object> dividend = result.getFirst();
      assertThat(dividend.get("symbol")).isEqualTo(TEST_SYMBOL);
      assertThat(dividend.get("tradeId")).isEqualTo(TEST_TRADE_ID);
    }

    @Test
    @DisplayName("이미 캐싱된 배당은 중복 저장되지 않는다")
    void getDividendsWithCache_ExistingDividend_SkipsDuplicate() {
      // given
      LocalDate startDate = LocalDate.of(2024, 1, 1);
      LocalDate endDate = LocalDate.of(2024, 12, 31);

      given(tradeRepository.findByUserIdAndSymbolOrderByTradeDateAsc(TEST_USER_ID, TEST_SYMBOL))
        .willReturn(List.of(testBuyTrade));

      given(marketServiceClient.getDividends(TEST_SYMBOL, startDate.toString(), endDate.toString()))
        .willReturn(List.of(testDividendDto));

      // 배당이 이미 캐시됨
      given(dividendRepository.existsByTradeIdAndDividendDate(TEST_TRADE_ID,
        LocalDate.parse(testDividendDto.exDate())))
        .willReturn(true);

      given(
        dividendRepository.findByUserIdAndSymbolOrderByDividendDateDesc(TEST_USER_ID, TEST_SYMBOL))
        .willReturn(List.of(testDividend));

      given(tradeRepository.findById(TEST_TRADE_ID)).willReturn(Optional.of(testBuyTrade));

      // when
      List<Map<String, Object>> result = dividendService.getDividendsWithCache(
        TEST_USER_ID, TEST_SYMBOL, startDate, endDate);

      // then
      assertThat(result).isNotEmpty();
      verify(dividendRepository, never()).save(any(Dividend.class)); // 중복 저장 안 됨
      verify(dividendRepository).flush();
    }

    @Test
    @DisplayName("일부 매도된 경우 남은 주식에 대해서만 배당이 계산된다")
    void getDividendsWithCache_PartialSell_CalculatesRemaining() {
      // given
      LocalDate buyDate = LocalDate.of(2024, 1, 15);
      LocalDate sellDate = LocalDate.of(2024, 2, 1);
      LocalDate dividendDate = LocalDate.of(2024, 3, 15);

      Trade buyTrade = Trade.builder()
        .tradeId(TEST_TRADE_ID)
        .userId(TEST_USER_ID)
        .accountId(TEST_ACCOUNT_ID)
        .symbol(TEST_SYMBOL)
        .tradeType(TradeType.BUY)
        .quantity(new BigDecimal("100")) // 100주 매수
        .tradeDate(buyDate)
        .build();

      Trade sellTrade = Trade.builder()
        .tradeId("sell-trade-id")
        .userId(TEST_USER_ID)
        .accountId(TEST_ACCOUNT_ID)
        .symbol(TEST_SYMBOL)
        .tradeType(TradeType.SELL)
        .quantity(new BigDecimal("40")) // 40주 매도
        .tradeDate(sellDate)
        .build();

      given(tradeRepository.findByUserIdAndSymbolOrderByTradeDateAsc(TEST_USER_ID, TEST_SYMBOL))
        .willReturn(List.of(buyTrade, sellTrade));

      DividendDto dividendDto = new DividendDto(dividendDate.toString(), DIVIDEND_PER_SHARE);
      given(marketServiceClient.getDividends(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(List.of(dividendDto));

      given(dividendRepository.existsByTradeIdAndDividendDate(anyString(), any(LocalDate.class)))
        .willReturn(false);

      // ArgumentCaptor로 저장된 배당 검증
      given(
        dividendRepository.findByUserIdAndSymbolOrderByDividendDateDesc(TEST_USER_ID, TEST_SYMBOL))
        .willReturn(new ArrayList<>());

      // when
      dividendService.getDividendsWithCache(TEST_USER_ID, TEST_SYMBOL, buyDate, LocalDate.now());

      // then
      verify(dividendRepository).save(argThat(dividend -> {
        // 60주에 대한 배당만 계산되어야 함 (100 - 40 = 60)
        assertThat(dividend.getShares()).isEqualByComparingTo(new BigDecimal("60"));
        return true;
      }));
    }

    @Test
    @DisplayName("배당 기준일 전에 전량 매도된 경우 배당이 저장되지 않는다")
    void getDividendsWithCache_AllSoldBeforeDividend_NoDividend() {
      // given
      LocalDate buyDate = LocalDate.of(2024, 1, 15);
      LocalDate sellDate = LocalDate.of(2024, 2, 1);
      LocalDate dividendDate = LocalDate.of(2024, 3, 15);

      Trade buyTrade = Trade.builder()
        .tradeId(TEST_TRADE_ID)
        .userId(TEST_USER_ID)
        .accountId(TEST_ACCOUNT_ID)
        .symbol(TEST_SYMBOL)
        .tradeType(TradeType.BUY)
        .quantity(new BigDecimal("100"))
        .tradeDate(buyDate)
        .build();

      Trade sellTrade = Trade.builder()
        .tradeId("sell-trade-id")
        .userId(TEST_USER_ID)
        .accountId(TEST_ACCOUNT_ID)
        .symbol(TEST_SYMBOL)
        .tradeType(TradeType.SELL)
        .quantity(new BigDecimal("100")) // 전량 매도
        .tradeDate(sellDate)
        .build();

      given(tradeRepository.findByUserIdAndSymbolOrderByTradeDateAsc(TEST_USER_ID, TEST_SYMBOL))
        .willReturn(List.of(buyTrade, sellTrade));

      DividendDto dividendDto = new DividendDto(dividendDate.toString(), DIVIDEND_PER_SHARE);
      given(marketServiceClient.getDividends(eq(TEST_SYMBOL), anyString(), anyString()))
        .willReturn(List.of(dividendDto));

      given(
        dividendRepository.findByUserIdAndSymbolOrderByDividendDateDesc(TEST_USER_ID, TEST_SYMBOL))
        .willReturn(new ArrayList<>());

      // when
      List<Map<String, Object>> result = dividendService.getDividendsWithCache(
        TEST_USER_ID, TEST_SYMBOL, buyDate, LocalDate.now());

      // then
      assertThat(result).isEmpty();
      verify(dividendRepository, never()).save(any(Dividend.class)); // 배당 저장 안 됨
    }

    @Test
    @DisplayName("Market Service 실패 시 캐시된 데이터를 반환한다")
    void getDividendsWithCache_MarketServiceFailed_ReturnsCached() {
      // given
      LocalDate startDate = LocalDate.of(2024, 1, 1);
      LocalDate endDate = LocalDate.of(2024, 12, 31);

      given(tradeRepository.findByUserIdAndSymbolOrderByTradeDateAsc(TEST_USER_ID, TEST_SYMBOL))
        .willReturn(List.of(testBuyTrade));

      given(marketServiceClient.getDividends(TEST_SYMBOL, startDate.toString(), endDate.toString()))
        .willThrow(new RuntimeException("Market service unavailable"));

      given(
        dividendRepository.findByUserIdAndSymbolOrderByDividendDateDesc(TEST_USER_ID, TEST_SYMBOL))
        .willReturn(List.of(testDividend));

      given(tradeRepository.findById(TEST_TRADE_ID)).willReturn(Optional.of(testBuyTrade));

      // when
      List<Map<String, Object>> result = dividendService.getDividendsWithCache(
        TEST_USER_ID, TEST_SYMBOL, startDate, endDate);

      // then
      assertThat(result).isNotEmpty();
      verify(dividendRepository, never()).save(any(Dividend.class));
    }

    @Test
    @DisplayName("매수 거래가 없으면 빈 리스트가 반환된다")
    void getDividendsWithCache_NoBuyTrades_ReturnsEmpty() {
      // given
      LocalDate startDate = LocalDate.of(2024, 1, 1);
      LocalDate endDate = LocalDate.of(2024, 12, 31);

      given(tradeRepository.findByUserIdAndSymbolOrderByTradeDateAsc(TEST_USER_ID, TEST_SYMBOL))
        .willReturn(new ArrayList<>());

      // when
      List<Map<String, Object>> result = dividendService.getDividendsWithCache(
        TEST_USER_ID, TEST_SYMBOL, startDate, endDate);

      // then
      assertThat(result).isEmpty();
      verify(marketServiceClient, never()).getDividends(anyString(), anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("배당 캐시 강제 갱신 테스트")
  class RefreshDividendCacheTests {

    @Test
    @DisplayName("모든 거래 종목의 배당 캐시가 갱신된다")
    void refreshDividendCache_Success() {
      // given
      List<String> symbols = List.of("AAPL", "MSFT");
      given(tradeRepository.findDistinctSymbolsByUserId(TEST_USER_ID))
        .willReturn(symbols);

      Trade appleTrade = Trade.builder()
        .tradeId("apple-trade-id")
        .userId(TEST_USER_ID)
        .symbol("AAPL")
        .tradeType(TradeType.BUY)
        .quantity(new BigDecimal("50"))
        .tradeDate(LocalDate.of(2024, 1, 1))
        .build();

      Trade msftTrade = Trade.builder()
        .tradeId("msft-trade-id")
        .userId(TEST_USER_ID)
        .symbol("MSFT")
        .tradeType(TradeType.BUY)
        .quantity(new BigDecimal("30"))
        .tradeDate(LocalDate.of(2024, 1, 1))
        .build();

      given(tradeRepository.findByUserIdAndSymbolOrderByTradeDateAsc(TEST_USER_ID, "AAPL"))
        .willReturn(List.of(appleTrade));
      given(tradeRepository.findByUserIdAndSymbolOrderByTradeDateAsc(TEST_USER_ID, "MSFT"))
        .willReturn(List.of(msftTrade));

      given(marketServiceClient.getDividends(eq("AAPL"), anyString(), anyString()))
        .willReturn(new ArrayList<>());
      given(marketServiceClient.getDividends(eq("MSFT"), anyString(), anyString()))
        .willReturn(new ArrayList<>());

      given(dividendRepository.findByUserIdAndSymbolOrderByDividendDateDesc(eq(TEST_USER_ID),
        anyString()))
        .willReturn(new ArrayList<>());

      // when
      dividendService.refreshDividendCache(TEST_USER_ID);

      // then
      verify(marketServiceClient).getDividends(eq("AAPL"), anyString(), anyString());
      verify(marketServiceClient).getDividends(eq("MSFT"), anyString(), anyString());
    }

    @Test
    @DisplayName("거래 종목이 없으면 캐시 갱신이 실행되지 않는다")
    void refreshDividendCache_NoTrades_NoRefresh() {
      // given
      given(tradeRepository.findDistinctSymbolsByUserId(TEST_USER_ID))
        .willReturn(new ArrayList<>());

      // when
      dividendService.refreshDividendCache(TEST_USER_ID);

      // then
      verify(marketServiceClient, never()).getDividends(anyString(), anyString(), anyString());
    }
  }
}
