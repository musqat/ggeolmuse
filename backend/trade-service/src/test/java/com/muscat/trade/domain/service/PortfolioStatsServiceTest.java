package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
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
import com.muscat.trade.domain.dto.response.ProfitDetailResponse;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.Holding;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.MonthlyProfit;
import com.muscat.trade.domain.dto.response.ProfitDetailResponse.Sale;
import com.muscat.trade.domain.dto.response.ProfitFlowResponse.ProfitPoint;
import com.muscat.trade.domain.dto.response.SymbolContributionResponse;
import com.muscat.trade.domain.dto.response.SymbolContributionResponse.SymbolContribution;
import com.muscat.trade.domain.entity.DividendPayout;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.DividendPayoutRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("계좌 통계 탭 조회")
class PortfolioStatsServiceTest {

  private static final String USER = "user-1";
  private static final long ACCOUNT = 3L;
  private static final LocalDate TODAY = LocalDate.of(2024, 11, 10);
  private static final LocalDate CLOSE_DATE = LocalDate.of(2024, 11, 8);

  @Mock
  private TradeRepository tradeRepository;

  @Mock
  private DividendPayoutRepository dividendPayoutRepository;

  @Mock
  private MarketServiceClientWrapper marketServiceClientWrapper;

  @Mock
  private ProfitFlowService profitFlowService;

  private PortfolioStatsService service;

  private long ids = 1;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2024-11-10T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    service = new PortfolioStatsService(
      new PortfolioStatsReader(tradeRepository, dividendPayoutRepository, marketServiceClientWrapper),
      profitFlowService, new TradeProperties(), clock);
  }

  private Trade trade(long accountId, TradeType type, String symbol, String quantity, String price, String fee,
    LocalDate tradeDate, TradeStatus status) {
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
      .executedAt(tradeDate.atTime(10, 0))
      .createdAt(tradeDate.atTime(10, 0))
      .status(status)
      .build();
  }

  private Trade buy(long accountId, String symbol, String quantity, String price, String fee, LocalDate date) {
    return trade(accountId, TradeType.BUY, symbol, quantity, price, fee, date, TradeStatus.COMPLETED);
  }

  private Trade sell(long accountId, String symbol, String quantity, String price, String fee, LocalDate date) {
    return trade(accountId, TradeType.SELL, symbol, quantity, price, fee, date, TradeStatus.COMPLETED);
  }

  private static DividendPayout payout(String symbol, LocalDate exDate, String net) {
    return DividendPayout.of(USER, ACCOUNT, symbol, exDate,
      new DividendAmounts(new BigDecimal("0.20"), new BigDecimal("10"), new BigDecimal(net), BigDecimal.ZERO,
        new BigDecimal(net)), LocalDateTime.of(2024, 9, 1, 9, 0));
  }

  private static OHLCPriceDto candle(String symbol, LocalDate date, String close) {
    BigDecimal price = new BigDecimal(close);
    return new OHLCPriceDto(symbol, date, price, price, price, price, price, 1000L, "USD", true);
  }

  private void ownAccount(List<Trade> trades, DividendPayout... payouts) {
    given(tradeRepository.findFirstByAccountIdOrderByIdAsc(ACCOUNT)).willReturn(Optional.of(trades.getFirst()));
    given(tradeRepository.findByUserIdAndAccountId(USER, ACCOUNT)).willReturn(trades);
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of(payouts));
  }

  private void fx(Map<String, BigDecimal> rates) {
    given(marketServiceClientWrapper.getBulkFxRates(List.copyOf(rates.keySet().stream().sorted().toList())))
      .willReturn(rates);
  }

  private static ProfitPoint point(LocalDate date, String profitKrw) {
    return new ProfitPoint(date, new BigDecimal(profitKrw), BigDecimal.ZERO);
  }

  @Test
  @DisplayName("수익 자세히는 기간 안 매도를 판 날 환율로, 기간 끝에 들고 있는 종목을 오늘 종가 · 환율로 내고 달마다 수익은 수익 흐름 점으로 낸다")
  void profit_OneAccount() {
    Trade inPeriod = sell(ACCOUNT, "AAPL", "2", "120.00", "0.00", LocalDate.of(2024, 10, 20));
    ownAccount(List.of(
      buy(ACCOUNT, "AAPL", "10", "100.00", "1.00", LocalDate.of(2024, 9, 2)),
      sell(ACCOUNT, "AAPL", "4", "110.00", "0.00", LocalDate.of(2024, 10, 1)),
      inPeriod,
      trade(ACCOUNT, TradeType.SELL, "AAPL", "1", "130.00", "0.00", LocalDate.of(2024, 11, 5),
        TradeStatus.PENDING)));
    given(profitFlowService.points(eq(USER), anyList(), eq(TODAY))).willReturn(List.of(
      point(LocalDate.of(2024, 10, 10), "500"), point(LocalDate.of(2024, 10, 31), "900"),
      point(CLOSE_DATE, "1200")));
    given(marketServiceClientWrapper.getOHLCPriceRange("AAPL", "2024-10-27", "2024-11-10"))
      .willReturn(List.of(candle("AAPL", CLOSE_DATE, "125.00")));
    fx(Map.of("2024-10-20", new BigDecimal("1350"), "2024-11-10", new BigDecimal("1400")));

    ProfitDetailResponse response = service.profit(USER, "3", FlowPeriod.ONE_MONTH);

    assertThat(response.monthly()).extracting(MonthlyProfit::month)
      .containsExactly(YearMonth.of(2024, 10), YearMonth.of(2024, 11));
    assertThat(response.monthly()).extracting(MonthlyProfit::profitKrw)
      .usingElementComparator(BigDecimal::compareTo)
      .containsExactly(new BigDecimal("400"), new BigDecimal("300"));
    assertThat(response.sales()).hasSize(1);
    Sale sale = response.sales().getFirst();
    assertThat(sale.tradeId()).isEqualTo(inPeriod.getId());
    assertThat(sale.accountId()).isEqualTo(ACCOUNT);
    assertThat(sale.realizedUsd()).isEqualByComparingTo("40.00");
    assertThat(sale.fxRate()).isEqualByComparingTo("1350");
    assertThat(sale.realizedKrw()).isEqualByComparingTo("54000");
    assertThat(sale.returnPercent()).isEqualByComparingTo("20.00");
    assertThat(response.realizedKrw()).isEqualByComparingTo("54000");
    Holding holding = response.holdings().getFirst();
    assertThat(holding.symbol()).isEqualTo("AAPL");
    assertThat(holding.quantity()).isEqualByComparingTo("4");
    assertThat(holding.averagePriceUsd()).isEqualByComparingTo("100.00");
    assertThat(holding.closeUsd()).isEqualByComparingTo("125.00");
    assertThat(holding.closeDate()).isEqualTo(CLOSE_DATE);
    assertThat(holding.unrealizedUsd()).isEqualByComparingTo("100.00");
    assertThat(holding.unrealizedKrw()).isEqualByComparingTo("140000");
    assertThat(holding.returnPercent()).isEqualByComparingTo("25.00");
    assertThat(response.unrealizedKrw()).isEqualByComparingTo("140000");
    verify(profitFlowService).points(eq(USER),
      argThat(activities -> activities.size() == 1 && activities.getFirst().accountId() == ACCOUNT
        && activities.getFirst().trades().size() == 4),
      eq(TODAY));
  }

  @Test
  @DisplayName("전체 계좌 수익 자세히는 모든 계좌의 매도를 모으고 들고 있는 종목은 종목마다 합친다")
  void profit_AllAccountsMergesHoldings() {
    LocalDate soldOn = LocalDate.of(2024, 10, 1);
    Trade aaplBuy = buy(ACCOUNT, "AAPL", "2", "100.00", "0.00", LocalDate.of(2024, 9, 2));
    Trade otherAaplBuy = buy(4L, "AAPL", "1", "130.00", "0.00", LocalDate.of(2024, 9, 3));
    Trade aaplSell = sell(ACCOUNT, "AAPL", "1", "150.00", "0.00", soldOn);
    Trade msftBuy = buy(4L, "MSFT", "1", "50.00", "0.00", LocalDate.of(2024, 9, 3));
    Trade msftSell = sell(4L, "MSFT", "1", "60.00", "0.00", soldOn);
    given(tradeRepository.findByUserId(USER)).willReturn(List.of(msftSell, aaplSell, msftBuy, otherAaplBuy, aaplBuy));
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of());
    given(profitFlowService.points(eq(USER), anyList(), eq(TODAY))).willReturn(List.of(
      point(LocalDate.of(2024, 9, 30), "100"), point(soldOn, "300")));
    given(marketServiceClientWrapper.getOHLCPriceRange("AAPL", "2024-10-27", "2024-11-10"))
      .willReturn(List.of(candle("AAPL", CLOSE_DATE, "120.00")));
    fx(Map.of("2024-10-01", new BigDecimal("1300"), "2024-11-10", new BigDecimal("1400")));

    ProfitDetailResponse response = service.profit(USER, "all", FlowPeriod.ALL);

    assertThat(response.monthly()).extracting(MonthlyProfit::profitKrw)
      .usingElementComparator(BigDecimal::compareTo)
      .containsExactly(new BigDecimal("100"), new BigDecimal("200"));
    assertThat(response.sales()).extracting(Sale::tradeId).containsExactly(msftSell.getId(), aaplSell.getId());
    assertThat(response.sales()).extracting(Sale::realizedKrw)
      .usingElementComparator(BigDecimal::compareTo)
      .containsExactly(new BigDecimal("13000"), new BigDecimal("65000"));
    assertThat(response.realizedKrw()).isEqualByComparingTo("78000");
    assertThat(response.holdings()).hasSize(1);
    Holding aapl = response.holdings().getFirst();
    assertThat(aapl.quantity()).isEqualByComparingTo("2");
    assertThat(aapl.averagePriceUsd()).isEqualByComparingTo("115.00");
    assertThat(aapl.unrealizedKrw()).isEqualByComparingTo("14000");
    assertThat(aapl.returnPercent()).isEqualByComparingTo("4.35");
    verify(tradeRepository, never()).findFirstByAccountIdOrderByIdAsc(any());
  }

  @Test
  @DisplayName("종목별 기여는 기간 시작 전날과 오늘의 종가 · 환율로 칸마다 늘어난 원화를 낸다")
  void symbols_EndMinusDayBeforePeriod() {
    ownAccount(List.of(
        buy(ACCOUNT, "AAPL", "10", "100.00", "1.00", LocalDate.of(2024, 9, 2)),
        sell(ACCOUNT, "AAPL", "4", "110.00", "0.50", LocalDate.of(2024, 10, 20)),
        buy(ACCOUNT, "MSFT", "1", "50.00", "0.00", LocalDate.of(2024, 10, 25))),
      payout("AAPL", LocalDate.of(2024, 11, 1), "2.00"));
    given(marketServiceClientWrapper.getOHLCPriceRange("AAPL", "2024-09-26", "2024-11-10")).willReturn(List.of(
      candle("AAPL", LocalDate.of(2024, 10, 10), "105.00"), candle("AAPL", CLOSE_DATE, "120.00")));
    given(marketServiceClientWrapper.getOHLCPriceRange("MSFT", "2024-10-27", "2024-11-10"))
      .willReturn(List.of(candle("MSFT", CLOSE_DATE, "55.00")));
    fx(Map.of("2024-10-10", new BigDecimal("1300"), "2024-11-10", new BigDecimal("1400")));

    SymbolContributionResponse response = service.symbols(USER, "3", FlowPeriod.ONE_MONTH);

    assertThat(response.symbols()).extracting(SymbolContribution::symbol).containsExactly("AAPL", "MSFT");
    SymbolContribution aapl = response.symbols().getFirst();
    assertThat(aapl.realizedKrw()).isEqualByComparingTo("56000");
    assertThat(aapl.unrealizedKrw()).isEqualByComparingTo("103000");
    assertThat(aapl.dividendKrw()).isEqualByComparingTo("2800");
    assertThat(aapl.feeKrw()).isEqualByComparingTo("800");
    assertThat(aapl.totalKrw()).isEqualByComparingTo("161000");
    assertThat(aapl.sharePercent()).isEqualByComparingTo("95.83");
    assertThat(response.symbols().getLast().totalKrw()).isEqualByComparingTo("7000");
    assertThat(response.symbols().getLast().sharePercent()).isEqualByComparingTo("4.17");
    assertThat(response.totalKrw()).isEqualByComparingTo("168000");
    verifyNoInteractions(profitFlowService);
  }

  @Test
  @DisplayName("전체 기간 종목별 기여는 시작 전 값 없이 오늘 값만 보고 계좌마다의 종목을 합친다")
  void symbols_AllPeriodAllAccounts() {
    given(tradeRepository.findByUserId(USER)).willReturn(List.of(
      buy(ACCOUNT, "AAPL", "1", "100.00", "0.00", LocalDate.of(2024, 9, 2)),
      buy(4L, "AAPL", "1", "110.00", "0.00", LocalDate.of(2024, 9, 3))));
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of());
    given(marketServiceClientWrapper.getOHLCPriceRange("AAPL", "2024-10-27", "2024-11-10"))
      .willReturn(List.of(candle("AAPL", CLOSE_DATE, "120.00")));
    fx(Map.of("2024-11-10", new BigDecimal("1400")));

    SymbolContributionResponse response = service.symbols(USER, "all", FlowPeriod.ALL);

    assertThat(response.symbols()).hasSize(1);
    assertThat(response.symbols().getFirst().unrealizedKrw()).isEqualByComparingTo("42000");
    assertThat(response.symbols().getFirst().sharePercent()).isEqualByComparingTo("100");
    assertThat(response.symbols().getFirst().held()).isTrue();
    assertThat(response.totalKrw()).isEqualByComparingTo("42000");
  }

  @Test
  @DisplayName("체결이 없는 계좌는 빈 목록 · 합 0 이고 시세는 부르지 않는다")
  void emptyAccount_NoMarketCalls() {
    given(tradeRepository.findFirstByAccountIdOrderByIdAsc(ACCOUNT)).willReturn(Optional.empty());
    given(tradeRepository.findByUserIdAndAccountId(USER, ACCOUNT)).willReturn(List.of());
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of());

    ProfitDetailResponse profit = service.profit(USER, "3", FlowPeriod.THREE_MONTHS);
    SymbolContributionResponse symbols = service.symbols(USER, "3", FlowPeriod.ONE_YEAR);

    assertThat(profit.monthly()).isEmpty();
    assertThat(profit.sales()).isEmpty();
    assertThat(profit.holdings()).isEmpty();
    assertThat(profit.realizedKrw()).isEqualByComparingTo("0");
    assertThat(profit.unrealizedKrw()).isEqualByComparingTo("0");
    assertThat(symbols.symbols()).isEmpty();
    assertThat(symbols.totalKrw()).isEqualByComparingTo("0");
    verifyNoInteractions(marketServiceClientWrapper);
  }

  @Test
  @DisplayName("체결 테이블의 계좌 주인이 다르면 계좌 접근 거부로 예외가 나고 수익 흐름 · 시세는 부르지 않는다")
  void otherUsersAccount_AccessDenied() {
    Trade others = buy(ACCOUNT, "AAPL", "1", "100.00", "0.00", LocalDate.of(2024, 9, 2));
    others.setUserId("user-2");
    given(tradeRepository.findFirstByAccountIdOrderByIdAsc(ACCOUNT)).willReturn(Optional.of(others));

    assertThatThrownBy(() -> service.profit(USER, "3", FlowPeriod.ALL))
      .hasFieldOrPropertyWithValue("errorCode", TradeResponse.ACCOUNT_ACCESS_DENIED.name());
    assertThatThrownBy(() -> service.symbols(USER, "3", FlowPeriod.ALL))
      .isInstanceOf(TradeException.class)
      .hasFieldOrPropertyWithValue("errorCode", TradeResponse.ACCOUNT_ACCESS_DENIED.name());
    verifyNoInteractions(profitFlowService, marketServiceClientWrapper);
  }
}
