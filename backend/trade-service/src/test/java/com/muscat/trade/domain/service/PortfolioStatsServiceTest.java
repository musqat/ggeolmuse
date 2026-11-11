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
import com.muscat.trade.domain.dto.response.DividendStatsResponse;
import com.muscat.trade.domain.dto.response.DividendStatsResponse.Payout;
import com.muscat.trade.domain.dto.response.DividendStatsResponse.SymbolDividend;
import com.muscat.trade.domain.dto.response.FxStatsResponse;
import com.muscat.trade.domain.dto.response.FxStatsResponse.Exchange;
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
import com.muscat.trade.infra.client.UserServiceClientWrapper;
import com.muscat.trade.infra.client.dto.ExchangeRecordDto;
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

  @Mock
  private UserServiceClientWrapper userServiceClientWrapper;

  private PortfolioStatsService service;

  private long ids = 1;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2024-11-10T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    service = new PortfolioStatsService(
      new PortfolioStatsReader(tradeRepository, dividendPayoutRepository, marketServiceClientWrapper),
      profitFlowService, userServiceClientWrapper, new TradeProperties(), clock);
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
    return payout(ACCOUNT, symbol, exDate, net);
  }

  private static DividendPayout payout(long accountId, String symbol, LocalDate exDate, String net) {
    return DividendPayout.of(USER, accountId, symbol, exDate,
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

  private static ExchangeRecordDto krwToUsd(long accountId, LocalDate date, String usd, String rate) {
    BigDecimal dollars = new BigDecimal(usd);
    BigDecimal fxRate = new BigDecimal(rate);
    return new ExchangeRecordDto(accountId, date.atTime(10, 15, 30), "KRW", "USD", dollars.multiply(fxRate), dollars,
      fxRate);
  }

  private static ExchangeRecordDto usdToKrw(long accountId, LocalDate date, String usd, String rate) {
    BigDecimal dollars = new BigDecimal(usd);
    BigDecimal fxRate = new BigDecimal(rate);
    return new ExchangeRecordDto(accountId, date.atTime(10, 15, 30), "USD", "KRW", dollars, dollars.multiply(fxRate),
      fxRate);
  }

  private static ProfitPoint point(LocalDate date, String profitKrw) {
    return new ProfitPoint(date, new BigDecimal(profitKrw), BigDecimal.ZERO);
  }

  @Test
  @DisplayName("수익 자세히는 기간 안 매도를 판 날 환율로, 들고 있는 종목을 수익 흐름 마지막 날 종가 · 환율로 내고 달마다 수익은 수익 흐름 점으로 낸다")
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
    fx(Map.of("2024-10-20", new BigDecimal("1350"), "2024-11-08", new BigDecimal("1380")));

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
    assertThat(holding.unrealizedKrw()).isEqualByComparingTo("138000");
    assertThat(holding.returnPercent()).isEqualByComparingTo("25.00");
    assertThat(response.unrealizedKrw()).isEqualByComparingTo("138000");
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
      point(LocalDate.of(2024, 9, 30), "100"), point(soldOn, "300"), point(CLOSE_DATE, "300")));
    given(marketServiceClientWrapper.getOHLCPriceRange("AAPL", "2024-10-27", "2024-11-10"))
      .willReturn(List.of(candle("AAPL", CLOSE_DATE, "120.00")));
    fx(Map.of("2024-10-01", new BigDecimal("1300"), "2024-11-08", new BigDecimal("1400")));

    ProfitDetailResponse response = service.profit(USER, "all", FlowPeriod.ALL);

    assertThat(response.monthly()).extracting(MonthlyProfit::profitKrw)
      .usingElementComparator(BigDecimal::compareTo)
      .containsExactly(new BigDecimal("100"), new BigDecimal("200"), BigDecimal.ZERO);
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
  }

  @Test
  @DisplayName("종목별 기여는 오늘 환율이 들어와도 수익 흐름 마지막 날의 종가 · 환율로 내서 현재 수익과 같다")
  void symbols_ValuesAtLastFlowDay() {
    ownAccount(List.of(buy(ACCOUNT, "AAPL", "1", "100.00", "0.00", LocalDate.of(2024, 9, 2))));
    given(profitFlowService.points(eq(USER), anyList(), eq(TODAY))).willReturn(List.of(
      point(LocalDate.of(2024, 10, 31), "20000"), point(CLOSE_DATE, "27600")));
    given(marketServiceClientWrapper.getOHLCPriceRange("AAPL", "2024-10-27", "2024-11-10"))
      .willReturn(List.of(candle("AAPL", CLOSE_DATE, "120.00")));
    fx(Map.of("2024-11-08", new BigDecimal("1380")));

    SymbolContributionResponse response = service.symbols(USER, "3", FlowPeriod.ALL);

    assertThat(response.symbols().getFirst().unrealizedKrw()).isEqualByComparingTo("27600");
    assertThat(response.totalKrw()).isEqualByComparingTo("27600");
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
  @DisplayName("배당은 배당락일이 기간 안인 배당만 배당락일 환율로 바꾼다")
  void dividends_OneAccountInPeriod() {
    ownAccount(List.of(buy(ACCOUNT, "KO", "10", "60.00", "0.00", LocalDate.of(2024, 9, 2))),
      payout("KO", LocalDate.of(2024, 10, 10), "1.00"), payout("KO", LocalDate.of(2024, 10, 15), "2.00"),
      payout("AAPL", LocalDate.of(2024, 11, 1), "0.50"));
    fx(Map.of("2024-10-15", new BigDecimal("1350"), "2024-11-01", new BigDecimal("1400")));

    DividendStatsResponse response = service.dividends(USER, "3", FlowPeriod.ONE_MONTH);

    assertThat(response.netKrw()).isEqualByComparingTo("3400");
    assertThat(response.netUsd()).isEqualByComparingTo("2.50");
    assertThat(response.payoutCount()).isEqualTo(2);
    assertThat(response.payouts()).extracting(Payout::exDate)
      .containsExactly(LocalDate.of(2024, 11, 1), LocalDate.of(2024, 10, 15));
    assertThat(response.payouts()).extracting(Payout::netKrw)
      .usingElementComparator(BigDecimal::compareTo)
      .containsExactly(new BigDecimal("700"), new BigDecimal("2700"));
    assertThat(response.symbols()).extracting(SymbolDividend::symbol).containsExactly("KO", "AAPL");
    verifyNoInteractions(profitFlowService, userServiceClientWrapper);
  }

  @Test
  @DisplayName("전체 계좌 배당은 계좌마다 배당 줄을 두고 종목 합은 계좌를 합친다")
  void dividends_AllAccounts() {
    LocalDate exDate = LocalDate.of(2024, 10, 15);
    given(tradeRepository.findByUserId(USER)).willReturn(List.of(
      buy(ACCOUNT, "KO", "10", "60.00", "0.00", LocalDate.of(2024, 9, 2)),
      buy(4L, "KO", "5", "60.00", "0.00", LocalDate.of(2024, 9, 3))));
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of(
      payout(4L, "KO", exDate, "1.00"), payout("KO", exDate, "2.00")));
    fx(Map.of("2024-10-15", new BigDecimal("1350")));

    DividendStatsResponse response = service.dividends(USER, "all", FlowPeriod.ALL);

    assertThat(response.payouts()).extracting(Payout::accountId).containsExactly(ACCOUNT, 4L);
    assertThat(response.symbols()).hasSize(1);
    assertThat(response.symbols().getFirst().netUsd()).isEqualByComparingTo("3.00");
    assertThat(response.symbols().getFirst().payoutCount()).isEqualTo(2);
    assertThat(response.netKrw()).isEqualByComparingTo("4050");
    verify(tradeRepository, never()).findFirstByAccountIdOrderByIdAsc(any());
  }

  @Test
  @DisplayName("환율 효과는 그 계좌의 환전 기록만 오늘과 기간 시작 전날 환율로 내고 체결이 없는 계좌도 낸다")
  void fx_OneAccountWithDayBeforePeriod() {
    given(tradeRepository.findFirstByAccountIdOrderByIdAsc(ACCOUNT)).willReturn(Optional.empty());
    given(userServiceClientWrapper.getExchangeRecords()).willReturn(List.of(
      krwToUsd(ACCOUNT, LocalDate.of(2024, 9, 2), "1000.00", "1300"),
      krwToUsd(4L, LocalDate.of(2024, 10, 20), "500.00", "1350"),
      krwToUsd(ACCOUNT, LocalDate.of(2024, 10, 20), "1000.00", "1350")));
    fx(Map.of("2024-10-10", new BigDecimal("1340"), "2024-11-10", new BigDecimal("1385.2")));

    FxStatsResponse response = service.fx(USER, "3", FlowPeriod.ONE_MONTH);

    assertThat(response.fxEffectKrw()).isEqualByComparingTo("80400");
    assertThat(response.heldUsd()).isEqualByComparingTo("2000.00");
    assertThat(response.averageRate()).isEqualByComparingTo("1325.00");
    assertThat(response.currentRate()).isEqualByComparingTo("1385.20");
    assertThat(response.exchanges()).extracting(Exchange::accountId).containsExactly(ACCOUNT);
    assertThat(response.exchanges().getFirst().effectKrw()).isEqualByComparingTo("35200");
    verify(tradeRepository, never()).findByUserIdAndAccountId(any(), any());
    verifyNoInteractions(dividendPayoutRepository, profitFlowService);
  }

  @Test
  @DisplayName("전체 계좌 · 전체 기간 환율 효과는 모든 계좌의 환전 기록을 오늘 환율 하나로 낸다")
  void fx_AllAccountsAllPeriod() {
    given(userServiceClientWrapper.getExchangeRecords()).willReturn(List.of(
      krwToUsd(ACCOUNT, LocalDate.of(2024, 9, 2), "1000.00", "1300"),
      usdToKrw(4L, LocalDate.of(2024, 11, 1), "300.00", "1380")));
    fx(Map.of("2024-11-10", new BigDecimal("1385.2")));

    FxStatsResponse response = service.fx(USER, "all", FlowPeriod.ALL);

    assertThat(response.fxEffectKrw()).isEqualByComparingTo("83640");
    assertThat(response.exchangeCount()).isEqualTo(2);
    assertThat(response.krwToUsdKrw()).isEqualByComparingTo("1300000");
    assertThat(response.usdToKrwUsd()).isEqualByComparingTo("300.00");
    verifyNoInteractions(tradeRepository);
  }

  @Test
  @DisplayName("환전 기록이 없으면 효과 0 · 빈 목록이고 지금 환율만 낸다")
  void fx_NoExchanges() {
    given(userServiceClientWrapper.getExchangeRecords()).willReturn(List.of());
    fx(Map.of("2024-11-10", new BigDecimal("1385.2")));

    FxStatsResponse response = service.fx(USER, "all", FlowPeriod.ONE_YEAR);

    assertThat(response.fxEffectKrw()).isEqualByComparingTo("0");
    assertThat(response.heldUsd()).isEqualByComparingTo("0");
    assertThat(response.averageRate()).isNull();
    assertThat(response.currentRate()).isEqualByComparingTo("1385.20");
    assertThat(response.exchanges()).isEmpty();
  }

  @Test
  @DisplayName("환전 기록을 못 받으면 사용자 서비스 오류로 예외가 나고 시세는 부르지 않는다")
  void fx_UserServiceError() {
    given(userServiceClientWrapper.getExchangeRecords())
      .willThrow(new TradeException(TradeResponse.USER_SERVICE_ERROR));

    assertThatThrownBy(() -> service.fx(USER, "all", FlowPeriod.ALL))
      .hasFieldOrPropertyWithValue("errorCode", TradeResponse.USER_SERVICE_ERROR.name());
    verifyNoInteractions(marketServiceClientWrapper);
  }

  @Test
  @DisplayName("환율 효과도 계좌 값이 숫자 · all 이 아니면 잘못된 계좌로 예외가 나고 사용자 서비스는 부르지 않는다")
  void fx_InvalidAccount_NoUserCall() {
    assertThatThrownBy(() -> service.fx(USER, "abc", FlowPeriod.ALL))
      .hasFieldOrPropertyWithValue("errorCode", TradeResponse.INVALID_ACCOUNT_ID.name());
    verifyNoInteractions(userServiceClientWrapper, tradeRepository);
  }

  @Test
  @DisplayName("체결이 없는 계좌는 빈 목록 · 합 0 이고 시세는 부르지 않는다")
  void emptyAccount_NoMarketCalls() {
    given(tradeRepository.findFirstByAccountIdOrderByIdAsc(ACCOUNT)).willReturn(Optional.empty());
    given(tradeRepository.findByUserIdAndAccountId(USER, ACCOUNT)).willReturn(List.of());
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of());

    ProfitDetailResponse profit = service.profit(USER, "3", FlowPeriod.THREE_MONTHS);
    SymbolContributionResponse symbols = service.symbols(USER, "3", FlowPeriod.ONE_YEAR);
    DividendStatsResponse dividends = service.dividends(USER, "3", FlowPeriod.ALL);

    assertThat(profit.monthly()).isEmpty();
    assertThat(profit.sales()).isEmpty();
    assertThat(profit.holdings()).isEmpty();
    assertThat(profit.realizedKrw()).isEqualByComparingTo("0");
    assertThat(profit.unrealizedKrw()).isEqualByComparingTo("0");
    assertThat(symbols.symbols()).isEmpty();
    assertThat(symbols.totalKrw()).isEqualByComparingTo("0");
    assertThat(dividends.payouts()).isEmpty();
    assertThat(dividends.netKrw()).isEqualByComparingTo("0");
    verifyNoInteractions(marketServiceClientWrapper);
  }

  @Test
  @DisplayName("체결 테이블의 계좌 주인이 다르면 계좌 접근 거부로 예외가 나고 수익 흐름 · 시세 · 사용자 서비스는 부르지 않는다")
  void otherUsersAccount_AccessDenied() {
    Trade others = buy(ACCOUNT, "AAPL", "1", "100.00", "0.00", LocalDate.of(2024, 9, 2));
    others.setUserId("user-2");
    given(tradeRepository.findFirstByAccountIdOrderByIdAsc(ACCOUNT)).willReturn(Optional.of(others));

    assertThatThrownBy(() -> service.profit(USER, "3", FlowPeriod.ALL))
      .hasFieldOrPropertyWithValue("errorCode", TradeResponse.ACCOUNT_ACCESS_DENIED.name());
    assertThatThrownBy(() -> service.symbols(USER, "3", FlowPeriod.ALL))
      .isInstanceOf(TradeException.class)
      .hasFieldOrPropertyWithValue("errorCode", TradeResponse.ACCOUNT_ACCESS_DENIED.name());
    assertThatThrownBy(() -> service.dividends(USER, "3", FlowPeriod.ALL))
      .hasFieldOrPropertyWithValue("errorCode", TradeResponse.ACCOUNT_ACCESS_DENIED.name());
    assertThatThrownBy(() -> service.fx(USER, "3", FlowPeriod.ALL))
      .hasFieldOrPropertyWithValue("errorCode", TradeResponse.ACCOUNT_ACCESS_DENIED.name());
    verifyNoInteractions(profitFlowService, marketServiceClientWrapper, userServiceClientWrapper);
  }
}
