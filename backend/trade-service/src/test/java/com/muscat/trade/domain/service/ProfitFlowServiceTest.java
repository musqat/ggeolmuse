package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.enums.type.FlowPeriod;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.config.TradeProperties;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse.ProfitPoint;
import com.muscat.trade.domain.entity.AccountProfitDaily;
import com.muscat.trade.domain.entity.DividendPayout;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.AccountProfitDailyRepository;
import com.muscat.trade.domain.repository.DividendPayoutRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.ProfitFlowCalculator.DailyProfit;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("계좌 수익 흐름 조회 · 저장")
class ProfitFlowServiceTest {

  private static final String USER = "user-1";
  private static final long ACCOUNT = 20L;
  private static final LocalDate D1 = LocalDate.of(2024, 1, 2);
  private static final LocalDate D2 = LocalDate.of(2024, 1, 3);
  private static final LocalDate D3 = LocalDate.of(2024, 1, 4);
  private static final LocalDateTime NOW = LocalDateTime.of(2024, 1, 31, 10, 0);
  private static final LocalDateTime STORED_AT = LocalDateTime.of(2024, 1, 11, 20, 0);

  @Mock
  private TradeRepository tradeRepository;

  @Mock
  private DividendPayoutRepository dividendPayoutRepository;

  @Mock
  private AccountProfitDailyRepository accountProfitDailyRepository;

  @Mock
  private AccountProfitStore accountProfitStore;

  @Mock
  private MarketServiceClientWrapper marketServiceClientWrapper;

  @Captor
  private ArgumentCaptor<List<AccountProfitDaily>> rows;

  private ProfitFlowService service;

  private long ids = 1;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2024-01-31T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    service = new ProfitFlowService(
      new PortfolioStatsReader(tradeRepository, dividendPayoutRepository, marketServiceClientWrapper),
      accountProfitDailyRepository, accountProfitStore, new TradeProperties(), clock);
  }

  private Trade trade(long accountId, TradeType type, String symbol, String quantity, String price, String fee,
    LocalDate tradeDate, TradeStatus status, LocalDateTime createdAt) {
    return Trade.builder()
      .id(ids++)
      .userId(USER)
      .accountId(accountId)
      .symbol(symbol)
      .tradeType(type)
      .quantity(new BigDecimal(quantity))
      .price(new BigDecimal(price))
      .fee(new BigDecimal(fee))
      .totalAmount(BigDecimal.ZERO)
      .tradeDate(tradeDate)
      .executedAt(createdAt)
      .createdAt(createdAt)
      .status(status)
      .build();
  }

  private Trade buy(String symbol, String quantity, String price, String fee, LocalDate tradeDate) {
    return trade(ACCOUNT, TradeType.BUY, symbol, quantity, price, fee, tradeDate, TradeStatus.COMPLETED,
      tradeDate.atTime(10, 0));
  }

  private static DividendPayout payout(long accountId, LocalDate exDate, String net, LocalDateTime updatedAt) {
    return DividendPayout.of(USER, accountId, "AAPL", exDate,
      new DividendAmounts(new BigDecimal("0.10"), new BigDecimal("10"), new BigDecimal(net), BigDecimal.ZERO,
        new BigDecimal(net)), updatedAt);
  }

  private static OHLCPriceDto candle(String symbol, LocalDate date, String close) {
    BigDecimal price = close == null ? null : new BigDecimal(close);
    return new OHLCPriceDto(symbol, date, price, price, price, price, price, 1000L, "USD", true);
  }

  private static AccountProfitDaily stored(LocalDate date, long profitKrw, LocalDateTime computedAt) {
    return AccountProfitDaily.of(USER, ACCOUNT, new DailyProfit(date, BigDecimal.ZERO, BigDecimal.ZERO,
      BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.valueOf(profitKrw), new BigDecimal("777.00"),
      new BigDecimal("777")), computedAt);
  }

  // 2024-01-02 부터 하루씩 10줄
  private static List<AccountProfitDaily> storedTenDays() {
    List<AccountProfitDaily> rows = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      rows.add(stored(D1.plusDays(i), 1000L * i, STORED_AT));
    }
    return rows;
  }

  private void ownAccount(List<Trade> trades) {
    given(tradeRepository.findFirstByAccountIdOrderByIdAsc(ACCOUNT)).willReturn(Optional.of(trades.getFirst()));
    given(tradeRepository.findByUserIdAndAccountId(USER, ACCOUNT)).willReturn(trades);
  }

  private void fxEverywhere(String rate) {
    given(marketServiceClientWrapper.getBulkFxRates(anyList())).willAnswer(invocation -> {
      Map<String, BigDecimal> rates = new LinkedHashMap<>();
      List<String> dates = invocation.getArgument(0);
      dates.forEach(date -> rates.put(date, new BigDecimal(rate)));
      return rates;
    });
  }

  private static List<LocalDate> dates(List<ProfitPoint> points) {
    return points.stream().map(ProfitPoint::date).toList();
  }

  @Test
  @DisplayName("처음 열면 첫 거래일부터 오늘까지 계산해 계산 시각과 함께 저장하고 점을 돌려준다")
  void flow_FirstOpen_ComputesFromFirstTradeAndStores() {
    ownAccount(List.of(buy("AAPL", "10", "100.00", "1.00", D1)));
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of(
      payout(ACCOUNT, D2, "1.00", NOW), payout(99L, D2, "50.00", NOW)));
    given(accountProfitDailyRepository.findByUserIdAndAccountIdOrderByTradeDateAsc(USER, ACCOUNT))
      .willReturn(List.of());
    given(marketServiceClientWrapper.getOHLCPriceRange("AAPL", "2023-12-19", "2024-01-31")).willReturn(List.of(
      candle("AAPL", D1, "101.00"), candle("AAPL", D2, "105.00"), candle("AAPL", D3, null)));
    given(marketServiceClientWrapper.getBulkFxRates(List.of("2024-01-02", "2024-01-03")))
      .willReturn(Map.of("2024-01-02", new BigDecimal("1300"), "2024-01-03", new BigDecimal("1310")));

    ProfitFlowResponse response = service.flow(USER, "20", FlowPeriod.ALL);

    assertThat(response.points()).containsExactly(
      new ProfitPoint(D1, new BigDecimal("11700"), new BigDecimal("1300000")),
      new ProfitPoint(D2, new BigDecimal("65500"), new BigDecimal("1310000")));
    assertThat(response.currentProfitKrw()).isEqualByComparingTo("65500");
    verify(accountProfitStore).replaceFrom(eq(ACCOUNT), eq(D1), rows.capture());
    assertThat(rows.getValue()).extracting(AccountProfitDaily::getTradeDate).containsExactly(D1, D2);
    assertThat(rows.getValue()).allSatisfy(row -> {
      assertThat(row.getUserId()).isEqualTo(USER);
      assertThat(row.getAccountId()).isEqualTo(ACCOUNT);
      assertThat(row.getComputedAt()).isEqualTo(NOW);
    });
    assertThat(rows.getValue().getLast().getDividendUsd()).isEqualByComparingTo("1.00");
    assertThat(rows.getValue().getLast().getInvestedUsd()).isEqualByComparingTo("1000.00");
    assertThat(rows.getValue().getLast().getInvestedKrw()).isEqualByComparingTo("1310000");
  }

  @Test
  @DisplayName("저장분이 있으면 최근 7거래일만 다시 계산하고 그 앞은 저장분을 그대로 쓴다")
  void flow_Stored_RecomputesLastSevenTradingDays() {
    ownAccount(List.of(buy("AAPL", "10", "100.00", "0.00", D1)));
    given(dividendPayoutRepository.findByUserId(USER))
      .willReturn(List.of(payout(ACCOUNT, D2, "1.00", STORED_AT.minusDays(1))));
    given(accountProfitDailyRepository.findByUserIdAndAccountIdOrderByTradeDateAsc(USER, ACCOUNT))
      .willReturn(storedTenDays());
    LocalDate from = LocalDate.of(2024, 1, 5);
    given(marketServiceClientWrapper.getOHLCPriceRange("AAPL", "2023-12-22", "2024-01-31")).willReturn(List.of(
      candle("AAPL", D3, "100.00"), candle("AAPL", from, "101.00"), candle("AAPL", LocalDate.of(2024, 1, 12), "102.00")));
    fxEverywhere("1");

    ProfitFlowResponse response = service.flow(USER, "20", FlowPeriod.ALL);

    assertThat(dates(response.points())).containsExactly(D1, D2, D3, from, LocalDate.of(2024, 1, 12));
    assertThat(response.points().get(2).profitKrw()).isEqualByComparingTo("2000");
    assertThat(response.points().get(3).profitKrw()).isEqualByComparingTo("11");
    assertThat(response.points().get(2).investedKrw()).isEqualByComparingTo("777");
    assertThat(response.points().get(3).investedKrw()).isEqualByComparingTo("1000");
    verify(accountProfitStore).replaceFrom(eq(ACCOUNT), eq(from), rows.capture());
    assertThat(rows.getValue()).hasSize(2);
  }

  @Test
  @DisplayName("다시 계산하는 날짜 앞 14일 안에 종가가 없는 종목은 그 앞 마지막 종가로, 그것도 없으면 0 으로 평가한다")
  void flow_NoCloseInLookback_UsesLastEarlierClose() {
    LocalDate halted = LocalDate.of(2023, 11, 1);
    ownAccount(List.of(
      trade(ACCOUNT, TradeType.BUY, "XYZ", "10", "100.00", "0.00", halted, TradeStatus.COMPLETED,
        halted.atTime(10, 0)),
      trade(ACCOUNT, TradeType.BUY, "OLD", "1", "50.00", "0.00", halted, TradeStatus.COMPLETED,
        halted.atTime(10, 0)),
      buy("AAPL", "1", "100.00", "0.00", D1)));
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of());
    given(accountProfitDailyRepository.findByUserIdAndAccountIdOrderByTradeDateAsc(USER, ACCOUNT))
      .willReturn(storedTenDays());
    LocalDate from = LocalDate.of(2024, 1, 5);
    given(marketServiceClientWrapper.getOHLCPriceRange("AAPL", "2023-12-22", "2024-01-31"))
      .willReturn(List.of(candle("AAPL", from, "100.00")));
    given(marketServiceClientWrapper.getOHLCPriceRange("XYZ", "2023-12-22", "2024-01-31")).willReturn(List.of());
    given(marketServiceClientWrapper.getOHLCPriceRange("XYZ", "2023-11-01", "2023-12-21")).willReturn(List.of(
      candle("XYZ", halted, "100.00"), candle("XYZ", LocalDate.of(2023, 12, 1), "120.00")));
    given(marketServiceClientWrapper.getOHLCPriceRange(eq("OLD"), anyString(), anyString())).willReturn(List.of());
    fxEverywhere("1");

    ProfitFlowResponse response = service.flow(USER, "20", FlowPeriod.ALL);

    assertThat(response.points().getLast())
      .isEqualTo(new ProfitPoint(from, new BigDecimal("150"), new BigDecimal("1150")));
  }

  private void assertRecomputedFrom(List<Trade> extraTrades, List<DividendPayout> payouts, LocalDate expected) {
    List<Trade> trades = new ArrayList<>(List.of(buy("AAPL", "10", "100.00", "0.00", D1)));
    trades.addAll(extraTrades);
    ownAccount(trades);
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(payouts);
    given(accountProfitDailyRepository.findByUserIdAndAccountIdOrderByTradeDateAsc(USER, ACCOUNT))
      .willReturn(storedTenDays());
    given(marketServiceClientWrapper.getOHLCPriceRange(eq("AAPL"), anyString(), eq("2024-01-31")))
      .willReturn(List.of(candle("AAPL", D1, "100.00")));
    fxEverywhere("1");

    service.flow(USER, "20", FlowPeriod.ALL);

    verify(accountProfitStore).replaceFrom(eq(ACCOUNT), eq(expected), any());
    verify(marketServiceClientWrapper).getOHLCPriceRange("AAPL", expected.minusDays(14).toString(), "2024-01-31");
  }

  @Test
  @DisplayName("마지막 계산 뒤(1분 여유)에 만든 과거 날짜 체결이 있으면 그 거래일부터 다시 계산한다")
  void flow_TradeCreatedAfterLastComputation_RecomputesFromItsTradeDate() {
    assertRecomputedFrom(List.of(
        trade(ACCOUNT, TradeType.BUY, "AAPL", "2", "100.00", "0.00", D2, TradeStatus.COMPLETED,
          STORED_AT.minusSeconds(30)),
        trade(ACCOUNT, TradeType.BUY, "AAPL", "2", "100.00", "0.00", D1, TradeStatus.COMPLETED,
          STORED_AT.minusMinutes(2))),
      List.of(), D2);
  }

  @Test
  @DisplayName("마지막 계산 뒤에 취소된 체결 · 바뀐 배당 몫이 있으면 그 거래일 · 배당락일부터 다시 계산한다")
  void flow_CancelledTradeOrChangedPayout_RecomputesFromItsDate() {
    Trade cancelled = trade(ACCOUNT, TradeType.SELL, "AAPL", "1", "100.00", "0.00", D3, TradeStatus.CANCELLED,
      D3.atTime(10, 0));
    cancelled.cancel("INSUFFICIENT_BALANCE", NOW);
    Trade cancelledEarlier = trade(ACCOUNT, TradeType.SELL, "AAPL", "1", "100.00", "0.00", D1, TradeStatus.CANCELLED,
      D1.atTime(10, 0));
    cancelledEarlier.cancel("INSUFFICIENT_BALANCE", STORED_AT.minusDays(1));

    assertRecomputedFrom(List.of(cancelled, cancelledEarlier),
      List.of(payout(ACCOUNT, D2, "1.00", NOW), payout(ACCOUNT, D1, "1.00", STORED_AT.minusDays(1))), D2);
  }

  @Test
  @DisplayName("마지막 계산 뒤 바뀐 날짜가 최근 7거래일 안이면 최근 7거래일부터 다시 계산한다")
  void flow_ChangeInsideRecentDays_RecomputesFromRecentDays() {
    assertRecomputedFrom(List.of(trade(ACCOUNT, TradeType.BUY, "AAPL", "1", "100.00", "0.00",
      LocalDate.of(2024, 1, 10), TradeStatus.COMPLETED, NOW)), List.of(), LocalDate.of(2024, 1, 5));
  }

  @Test
  @DisplayName("가장 이른 체결이 취소되면 그 거래일부터 저장분을 지우고 남은 첫 거래일부터 계산한다")
  void flow_FirstTradeCancelled_DeletesFromItAndComputesFromNewFirst() {
    Trade cancelled = buy("AAPL", "10", "100.00", "0.00", D1);
    cancelled.cancel("INSUFFICIENT_BALANCE", NOW);
    ownAccount(List.of(cancelled, buy("AAPL", "1", "100.00", "0.00", D3)));
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of());
    given(accountProfitDailyRepository.findByUserIdAndAccountIdOrderByTradeDateAsc(USER, ACCOUNT))
      .willReturn(storedTenDays());
    given(marketServiceClientWrapper.getOHLCPriceRange("AAPL", "2023-12-21", "2024-01-31"))
      .willReturn(List.of(candle("AAPL", D3, "100.00")));
    fxEverywhere("1");

    ProfitFlowResponse response = service.flow(USER, "20", FlowPeriod.ALL);

    assertThat(dates(response.points())).containsExactly(D3);
    verify(accountProfitStore).replaceFrom(eq(ACCOUNT), eq(D1), rows.capture());
    assertThat(rows.getValue()).extracting(AccountProfitDaily::getTradeDate).containsExactly(D3);
  }

  @Test
  @DisplayName("정산 전 매수가 있으면 그 거래일부터는 계산해 보여 주되 저장하지 않는다")
  void flow_PendingBuy_StoresOnlyBeforeItsTradeDate() {
    ownAccount(List.of(buy("AAPL", "10", "100.00", "0.00", D1),
      trade(ACCOUNT, TradeType.BUY, "AAPL", "5", "100.00", "0.00", D2, TradeStatus.PENDING, NOW)));
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of());
    given(accountProfitDailyRepository.findByUserIdAndAccountIdOrderByTradeDateAsc(USER, ACCOUNT))
      .willReturn(List.of());
    given(marketServiceClientWrapper.getOHLCPriceRange("AAPL", "2023-12-19", "2024-01-31")).willReturn(List.of(
      candle("AAPL", D1, "100.00"), candle("AAPL", D2, "101.00"), candle("AAPL", D3, "102.00")));
    fxEverywhere("1");

    ProfitFlowResponse response = service.flow(USER, "20", FlowPeriod.ALL);

    assertThat(dates(response.points())).containsExactly(D1, D2, D3);
    verify(accountProfitStore).replaceFrom(eq(ACCOUNT), eq(D1), rows.capture());
    assertThat(rows.getValue()).extracting(AccountProfitDaily::getTradeDate).containsExactly(D1);
  }

  @Test
  @DisplayName("완료 체결이 없으면 저장분을 지우고 빈 흐름을 돌려주며 시세는 부르지 않는다")
  void flow_NoCompletedTrades_ClearsStoredRows() {
    ownAccount(List.of(trade(ACCOUNT, TradeType.BUY, "AAPL", "1", "100.00", "0.00", D1, TradeStatus.CANCELLED,
      D1.atTime(10, 0))));
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of());
    given(accountProfitDailyRepository.findByUserIdAndAccountIdOrderByTradeDateAsc(USER, ACCOUNT))
      .willReturn(storedTenDays());

    ProfitFlowResponse response = service.flow(USER, "20", FlowPeriod.ALL);

    assertThat(response.points()).isEmpty();
    verify(accountProfitStore).replaceFrom(ACCOUNT, D1, List.of());
    verifyNoInteractions(marketServiceClientWrapper);
  }

  @Test
  @DisplayName("저장분도 완료 체결도 없으면 아무것도 지우지 않는다")
  void flow_NothingStored_NoDelete() {
    ownAccount(List.of(trade(ACCOUNT, TradeType.BUY, "AAPL", "1", "100.00", "0.00", D1, TradeStatus.PENDING,
      NOW)));
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of());
    given(accountProfitDailyRepository.findByUserIdAndAccountIdOrderByTradeDateAsc(USER, ACCOUNT))
      .willReturn(List.of());

    assertThat(service.flow(USER, "20", FlowPeriod.ALL).points()).isEmpty();
    verifyNoInteractions(accountProfitStore, marketServiceClientWrapper);
  }

  @Test
  @DisplayName("체결 테이블의 계좌 주인이 다르면 계좌 접근 거부로 예외가 나고 시세 · 저장은 하지 않는다")
  void flow_OtherUsersAccount_AccessDenied() {
    Trade others = trade(ACCOUNT, TradeType.BUY, "AAPL", "1", "100.00", "0.00", D1, TradeStatus.COMPLETED, NOW);
    others.setUserId("user-2");
    given(tradeRepository.findFirstByAccountIdOrderByIdAsc(ACCOUNT)).willReturn(Optional.of(others));

    assertThatThrownBy(() -> service.flow(USER, "20", FlowPeriod.ALL))
      .isInstanceOf(TradeException.class)
      .hasFieldOrPropertyWithValue("errorCode", TradeResponse.ACCOUNT_ACCESS_DENIED.name());
    verifyNoInteractions(accountProfitStore, marketServiceClientWrapper);
  }

  @Test
  @DisplayName("체결이 하나도 없는 계좌는 빈 흐름이다")
  void flow_AccountWithoutTrades_Empty() {
    given(tradeRepository.findFirstByAccountIdOrderByIdAsc(ACCOUNT)).willReturn(Optional.empty());
    given(tradeRepository.findByUserIdAndAccountId(USER, ACCOUNT)).willReturn(List.of());
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of());

    assertThat(service.flow(USER, "20", FlowPeriod.ONE_MONTH).points()).isEmpty();
  }

  @Test
  @DisplayName("계좌 값이 all 이나 양의 숫자가 아니면 계좌 ID 오류로 예외가 난다")
  void flow_InvalidAccount_InvalidAccountId() {
    for (String account : List.of("abc", "0", "-3", "ALL ")) {
      assertThatThrownBy(() -> service.flow(USER, account, FlowPeriod.ALL))
        .isInstanceOf(TradeException.class)
        .hasFieldOrPropertyWithValue("errorCode", TradeResponse.INVALID_ACCOUNT_ID.name());
    }
    verifyNoInteractions(tradeRepository);
  }

  @Test
  @DisplayName("전체 계좌는 계좌마다 계산한 수익 · 투자 원금을 날짜마다 더하고, 그 날짜에 점이 없는 계좌는 직전 점을 쓴다")
  void flow_AllAccounts_SumsByDateWithCarry() {
    Trade aapl = trade(ACCOUNT, TradeType.BUY, "AAPL", "1", "100.00", "0.00", D1, TradeStatus.COMPLETED,
      D1.atTime(10, 0));
    Trade msft = trade(21L, TradeType.BUY, "MSFT", "1", "50.00", "0.00", D2, TradeStatus.COMPLETED,
      D2.atTime(10, 0));
    given(tradeRepository.findByUserId(USER)).willReturn(List.of(msft, aapl));
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of());
    given(accountProfitDailyRepository.findByUserIdAndAccountIdOrderByTradeDateAsc(eq(USER), any()))
      .willReturn(List.of());
    given(marketServiceClientWrapper.getOHLCPriceRange("AAPL", "2023-12-19", "2024-01-31")).willReturn(List.of(
      candle("AAPL", D1, "100.00"), candle("AAPL", D3, "110.00")));
    given(marketServiceClientWrapper.getOHLCPriceRange("MSFT", "2023-12-20", "2024-01-31")).willReturn(List.of(
      candle("MSFT", D2, "55.00")));
    fxEverywhere("1");

    ProfitFlowResponse response = service.flow(USER, "all", FlowPeriod.ALL);

    assertThat(response.points()).containsExactly(
      new ProfitPoint(D1, new BigDecimal("0"), new BigDecimal("100")),
      new ProfitPoint(D2, new BigDecimal("5"), new BigDecimal("150")),
      new ProfitPoint(D3, new BigDecimal("15"), new BigDecimal("150")));
    verify(tradeRepository, never()).findFirstByAccountIdOrderByIdAsc(any());
  }

  @Test
  @DisplayName("기간을 고르면 오늘에서 그 날수 전부터의 점으로 최고 · 최저를 낸다")
  void flow_Period_FiltersPoints() {
    LocalDate december = LocalDate.of(2023, 12, 1);
    ownAccount(List.of(buy("AAPL", "1", "100.00", "0.00", december)));
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of());
    given(accountProfitDailyRepository.findByUserIdAndAccountIdOrderByTradeDateAsc(USER, ACCOUNT))
      .willReturn(List.of());
    given(marketServiceClientWrapper.getOHLCPriceRange("AAPL", "2023-11-17", "2024-01-31")).willReturn(List.of(
      candle("AAPL", december, "130.00"), candle("AAPL", LocalDate.of(2023, 12, 31), "90.00"),
      candle("AAPL", LocalDate.of(2024, 1, 1), "110.00")));
    fxEverywhere("1");

    ProfitFlowResponse response = service.flow(USER, "20", FlowPeriod.ONE_MONTH);

    assertThat(dates(response.points())).containsExactly(LocalDate.of(2024, 1, 1));
    assertThat(response.best().profitKrw()).isEqualByComparingTo("10");
  }

  @Test
  @DisplayName("시세를 못 받으면 저장하지 않고 시장 데이터 오류로 예외가 난다")
  void flow_MarketDataDown_NoStore() {
    ownAccount(List.of(buy("AAPL", "1", "100.00", "0.00", D1)));
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of());
    given(accountProfitDailyRepository.findByUserIdAndAccountIdOrderByTradeDateAsc(USER, ACCOUNT))
      .willReturn(List.of());
    given(marketServiceClientWrapper.getOHLCPriceRange(anyString(), anyString(), anyString()))
      .willThrow(new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR));

    assertThatThrownBy(() -> service.flow(USER, "20", FlowPeriod.ALL))
      .hasFieldOrPropertyWithValue("errorCode", TradeResponse.MARKET_DATA_SERVICE_ERROR.name());
    verifyNoInteractions(accountProfitStore);
  }
}
