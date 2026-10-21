package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.dto.TradePosition;
import com.muscat.trade.domain.entity.DividendPayout;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.DividendPayoutRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import com.muscat.trade.infra.client.dto.DividendDto;
import com.muscat.trade.infra.kafka.DividendPayoutProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("배당 몫 재계산")
class DividendPayoutServiceTest {

  private static final String USER = "user-1";
  private static final long ACCOUNT = 20L;
  private static final String SYMBOL = "AAPL";
  private static final LocalDate EX_2024_02 = LocalDate.of(2024, 2, 9);
  private static final LocalDate EX_2025_05 = LocalDate.of(2025, 5, 12);
  private static final LocalDate EX_2026_08 = LocalDate.of(2026, 8, 11);
  private static final long OTHER_ACCOUNT = 21L;
  private static final TradePosition POSITION = new TradePosition(USER, ACCOUNT, SYMBOL);

  @Mock
  private TradeRepository tradeRepository;

  @Mock
  private DividendPayoutRepository dividendPayoutRepository;

  @Mock
  private MarketServiceClientWrapper marketServiceClientWrapper;

  @Mock
  private DividendPayoutProducer dividendPayoutProducer;

  @Spy
  private TransactionTemplate transactionTemplate =
    new TransactionTemplate(org.mockito.Mockito.mock(PlatformTransactionManager.class));

  @InjectMocks
  private DividendPayoutService dividendPayoutService;

  private static Trade trade(long id, TradeType type, String quantity, LocalDate tradeDate) {
    return Trade.builder()
      .id(id).userId(USER).accountId(ACCOUNT).symbol(SYMBOL).tradeType(type)
      .quantity(new BigDecimal(quantity)).price(new BigDecimal("180")).totalAmount(new BigDecimal("1800"))
      .tradeDate(tradeDate).executedAt(tradeDate.atTime(10, 0)).status(TradeStatus.COMPLETED)
      .build();
  }

  // 세후는 손으로 셈: 0.24 × 10 → 2.03, 0.25 × 10 → 2.11, 0.26 × 10 → 2.20
  private static DividendPayout payout(LocalDate exDate, String perShare, String gross, String tax, String net) {
    return DividendPayout.of(USER, ACCOUNT, SYMBOL, exDate,
      new DividendAmounts(new BigDecimal(perShare), new BigDecimal("10"), new BigDecimal(gross),
        new BigDecimal(tax), new BigDecimal(net)),
      LocalDateTime.of(2026, 10, 1, 9, 0));
  }

  private static List<DividendPayout> paidThreeTimes() {
    return new ArrayList<>(List.of(
      payout(EX_2024_02, "0.24", "2.40", "0.37", "2.03"),
      payout(EX_2025_05, "0.25", "2.50", "0.39", "2.11"),
      payout(EX_2026_08, "0.26", "2.60", "0.40", "2.20")));
  }

  private void givenTrades(Trade... trades) {
    given(tradeRepository.findByUserIdAndAccountIdAndSymbolAndStatusOrderByExecutedAtAscIdAsc(
      USER, ACCOUNT, SYMBOL, TradeStatus.COMPLETED)).willReturn(List.of(trades));
  }

  private void givenDividends() {
    given(marketServiceClientWrapper.getDividends(eq(SYMBOL), eq("2024-01-02"), anyString())).willReturn(List.of(
      new DividendDto("2024-02-09", new BigDecimal("0.24")),
      new DividendDto("2025-05-12", new BigDecimal("0.25")),
      new DividendDto("2026-08-11", new BigDecimal("0.26"))));
  }

  private List<DividendPayout> published(int times) {
    ArgumentCaptor<DividendPayout> captor = ArgumentCaptor.forClass(DividendPayout.class);
    verify(dividendPayoutProducer, times(times)).publish(captor.capture());
    return captor.getAllValues();
  }

  @Test
  @DisplayName("과거 날짜로 산 종목은 매수일 뒤 배당락일 몫을 모두 만든다")
  void reconcile_PastBuy_PaysEveryExDateAfterBuy() {
    givenTrades(trade(1, TradeType.BUY, "10", LocalDate.of(2024, 1, 2)));
    givenDividends();
    given(dividendPayoutRepository.findByAccountIdAndSymbol(ACCOUNT, SYMBOL)).willReturn(new ArrayList<>());

    int changed = dividendPayoutService.reconcile(USER, ACCOUNT, SYMBOL);

    assertThat(changed).isEqualTo(3);
    assertThat(published(3)).extracting(DividendPayout::getExDate, p -> p.getNetAmount().toPlainString(),
        p -> p.getShares().stripTrailingZeros().toPlainString())
      .containsExactly(
        tuple(EX_2024_02, "2.03", "10"),
        tuple(EX_2025_05, "2.11", "10"),
        tuple(EX_2026_08, "2.20", "10"));
    verify(dividendPayoutRepository, times(3)).save(any(DividendPayout.class));
  }

  @Test
  @DisplayName("과거 날짜로 팔면 매도일 뒤 배당락일 몫을 0 으로 돌린다")
  void reconcile_PastSell_ZeroesExDatesAfterSell() {
    givenTrades(
      trade(1, TradeType.BUY, "10", LocalDate.of(2024, 1, 2)),
      trade(2, TradeType.SELL, "10", LocalDate.of(2025, 3, 10)));
    givenDividends();
    List<DividendPayout> existing = paidThreeTimes();
    given(dividendPayoutRepository.findByAccountIdAndSymbol(ACCOUNT, SYMBOL)).willReturn(existing);

    int changed = dividendPayoutService.reconcile(USER, ACCOUNT, SYMBOL);

    assertThat(changed).isEqualTo(2);
    assertThat(published(2)).extracting(DividendPayout::getExDate).containsExactly(EX_2025_05, EX_2026_08);
    assertThat(existing.get(0).getNetAmount()).isEqualByComparingTo("2.03");
    assertThat(existing.get(1).getNetAmount()).isEqualByComparingTo("0");
    assertThat(existing.get(1).getShares()).isEqualByComparingTo("0");
    assertThat(existing.get(2).getNetAmount()).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("몫 행이 없어도 보유가 0 인 배당락일은 0 몫을 보낸다")
  void reconcile_NoRowYet_SendsZeroForExDatesWithoutShares() {
    givenTrades(
      trade(1, TradeType.BUY, "10", LocalDate.of(2024, 1, 2)),
      trade(2, TradeType.SELL, "10", LocalDate.of(2025, 3, 10)));
    givenDividends();
    given(dividendPayoutRepository.findByAccountIdAndSymbol(ACCOUNT, SYMBOL)).willReturn(new ArrayList<>());

    int changed = dividendPayoutService.reconcile(USER, ACCOUNT, SYMBOL);

    assertThat(changed).isEqualTo(3);
    assertThat(published(3)).extracting(DividendPayout::getExDate, p -> p.getNetAmount().toPlainString())
      .containsExactly(
        tuple(EX_2024_02, "2.03"),
        tuple(EX_2025_05, "0.00"),
        tuple(EX_2026_08, "0.00"));
  }

  @Test
  @DisplayName("다시 보내기면 바뀌지 않은 몫도 보낸다")
  void reconcile_Resend_PublishesUnchangedRows() {
    givenTrades(trade(1, TradeType.BUY, "10", LocalDate.of(2024, 1, 2)));
    givenDividends();
    given(dividendPayoutRepository.findByAccountIdAndSymbol(ACCOUNT, SYMBOL)).willReturn(paidThreeTimes());

    int changed = dividendPayoutService.reconcile(USER, ACCOUNT, SYMBOL, true, List.of());

    assertThat(changed).isZero();
    assertThat(published(3)).extracting(DividendPayout::getExDate).containsExactly(EX_2024_02, EX_2025_05, EX_2026_08);
    verify(dividendPayoutRepository, never()).save(any());
  }

  @Test
  @DisplayName("넣은 배당락일이 가져온 배당 목록 밖이면 0 몫을 만든다")
  void reconcile_SettledExDateOutsideDividends_SendsZero() {
    LocalDate before = LocalDate.of(2023, 11, 10);
    givenTrades(trade(1, TradeType.BUY, "10", LocalDate.of(2024, 1, 2)));
    givenDividends();
    given(dividendPayoutRepository.findByAccountIdAndSymbol(ACCOUNT, SYMBOL)).willReturn(new ArrayList<>());

    dividendPayoutService.reconcile(USER, ACCOUNT, SYMBOL, false, List.of(before));

    assertThat(published(4)).extracting(DividendPayout::getExDate, p -> p.getNetAmount().toPlainString())
      .containsExactly(
        tuple(before, "0.00"),
        tuple(EX_2024_02, "2.03"),
        tuple(EX_2025_05, "2.11"),
        tuple(EX_2026_08, "2.20"));
  }

  @Test
  @DisplayName("체결이 하나도 없어도 넣은 배당락일은 0 몫을 보내고 시세 서비스는 부르지 않는다")
  void reconcile_NoTradesButSettled_SendsZero() {
    givenTrades();
    given(dividendPayoutRepository.findByAccountIdAndSymbol(ACCOUNT, SYMBOL)).willReturn(new ArrayList<>());

    int changed = dividendPayoutService.reconcile(USER, ACCOUNT, SYMBOL, false, List.of(EX_2025_05));

    assertThat(changed).isEqualTo(1);
    assertThat(published(1)).extracting(DividendPayout::getExDate, p -> p.getNetAmount().toPlainString())
      .containsExactly(tuple(EX_2025_05, "0.00"));
    verifyNoInteractions(marketServiceClientWrapper);
  }

  @Test
  @DisplayName("배당락일 당일에 판 주식은 그 배당을 받는다")
  void reconcile_SellOnExDate_KeepsThatDividend() {
    givenTrades(
      trade(1, TradeType.BUY, "10", LocalDate.of(2024, 1, 2)),
      trade(2, TradeType.SELL, "10", EX_2025_05));
    givenDividends();
    given(dividendPayoutRepository.findByAccountIdAndSymbol(ACCOUNT, SYMBOL)).willReturn(new ArrayList<>());

    dividendPayoutService.reconcile(USER, ACCOUNT, SYMBOL);

    assertThat(published(3)).extracting(DividendPayout::getExDate, p -> p.getShares().stripTrailingZeros().toPlainString())
      .containsExactly(
        tuple(EX_2024_02, "10"),
        tuple(EX_2025_05, "10"),
        tuple(EX_2026_08, "0"));
  }

  @Test
  @DisplayName("바뀐 게 없으면 저장도 이벤트도 없다")
  void reconcile_NothingChanged_PublishesNothing() {
    givenTrades(trade(1, TradeType.BUY, "10", LocalDate.of(2024, 1, 2)));
    givenDividends();
    given(dividendPayoutRepository.findByAccountIdAndSymbol(ACCOUNT, SYMBOL)).willReturn(paidThreeTimes());

    int changed = dividendPayoutService.reconcile(USER, ACCOUNT, SYMBOL);

    assertThat(changed).isZero();
    verify(dividendPayoutRepository, never()).save(any());
    verifyNoInteractions(dividendPayoutProducer);
  }

  @Test
  @DisplayName("시세 서비스에서 배당을 못 받으면 트랜잭션을 열지 않고 몫도 건드리지 않은 채 오류를 올린다")
  void reconcile_DividendFetchFails_KeepsPayouts() {
    givenTrades(trade(1, TradeType.BUY, "10", LocalDate.of(2024, 1, 2)));
    given(marketServiceClientWrapper.getDividends(eq(SYMBOL), eq("2024-01-02"), anyString()))
      .willThrow(new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR));

    assertThatThrownBy(() -> dividendPayoutService.reconcile(USER, ACCOUNT, SYMBOL))
      .isInstanceOf(TradeException.class)
      .hasFieldOrPropertyWithValue("errorCode", "MARKET_DATA_SERVICE_ERROR");
    verify(transactionTemplate, never()).execute(any());
    verify(dividendPayoutRepository, never()).findByAccountIdAndSymbol(any(), any());
    verify(dividendPayoutRepository, never()).save(any());
    verifyNoInteractions(dividendPayoutProducer);
  }

  @Test
  @DisplayName("체결이 다 취소돼 없으면 남은 몫을 0 으로 돌리고 시세 서비스는 부르지 않는다")
  void reconcile_NoTradesLeft_ZeroesAll() {
    givenTrades();
    List<DividendPayout> existing = new ArrayList<>(paidThreeTimes().subList(0, 2));
    given(dividendPayoutRepository.findByAccountIdAndSymbol(ACCOUNT, SYMBOL)).willReturn(existing);

    int changed = dividendPayoutService.reconcile(USER, ACCOUNT, SYMBOL);

    assertThat(changed).isEqualTo(2);
    assertThat(published(2)).extracting(p -> p.getNetAmount().toPlainString()).containsOnly("0.00");
    verifyNoInteractions(marketServiceClientWrapper);
  }

  private void givenUserTrades(Trade... trades) {
    given(tradeRepository.findByUserIdAndStatusOrderByExecutedAtAscIdAsc(USER, TradeStatus.COMPLETED))
      .willReturn(List.of(trades));
  }

  @Test
  @DisplayName("지금 남은 매수 묶음만 그 매수일 뒤 배당을 받은 것으로 센다")
  void dividendsOnHeldShares_OnlyRemainingLots() {
    givenUserTrades(
      trade(1, TradeType.BUY, "10", LocalDate.of(2024, 1, 2)),
      trade(2, TradeType.BUY, "5", LocalDate.of(2025, 1, 2)),
      trade(3, TradeType.SELL, "10", LocalDate.of(2025, 6, 1)));
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of(
      payout(LocalDate.of(2024, 2, 9), "0.24", "2.40", "0.37", "2.03"),
      payout(LocalDate.of(2025, 2, 7), "0.25", "3.75", "0.58", "3.17"),
      payout(LocalDate.of(2025, 5, 12), "0.25", "3.75", "0.58", "3.17")));

    // 남은 건 2025-01-02 매수 5주. 주당 0.25 × 5주 → 세후 1.06, 배당락일 둘
    assertThat(dividendPayoutService.dividendsOnHeldShares(USER).get(POSITION)).isEqualByComparingTo("2.12");
  }

  @Test
  @DisplayName("보유 전체를 거래 · 몫 한 번씩만 읽어 계좌 · 종목마다 센다")
  void dividendsOnHeldShares_ReadsOnceForAllPositions() {
    Trade msft = Trade.builder()
      .id(2L).userId(USER).accountId(OTHER_ACCOUNT).symbol("MSFT").tradeType(TradeType.BUY)
      .quantity(new BigDecimal("3")).price(new BigDecimal("400")).totalAmount(new BigDecimal("1200"))
      .tradeDate(LocalDate.of(2024, 1, 2)).executedAt(LocalDate.of(2024, 1, 2).atTime(10, 0))
      .status(TradeStatus.COMPLETED)
      .build();
    givenUserTrades(trade(1, TradeType.BUY, "10", LocalDate.of(2024, 1, 2)), msft);
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of(
      payout(EX_2024_02, "0.24", "2.40", "0.37", "2.03"),
      DividendPayout.of(USER, OTHER_ACCOUNT, "MSFT", LocalDate.of(2024, 2, 14),
        DividendAmounts.of(new BigDecimal("0.75"), new BigDecimal("3")), LocalDateTime.of(2026, 10, 1, 9, 0))));

    Map<TradePosition, BigDecimal> totals = dividendPayoutService.dividendsOnHeldShares(USER);

    // MSFT 0.75 × 3주 → 세전 2.25 · 세금 0.35 · 세후 1.90
    assertThat(totals.get(POSITION)).isEqualByComparingTo("2.03");
    assertThat(totals.get(new TradePosition(USER, OTHER_ACCOUNT, "MSFT"))).isEqualByComparingTo("1.90");
    verify(tradeRepository, times(1)).findByUserIdAndStatusOrderByExecutedAtAscIdAsc(USER, TradeStatus.COMPLETED);
    verify(dividendPayoutRepository, times(1)).findByUserId(USER);
  }

  @Test
  @DisplayName("보유가 0 인 몫 행은 받은 배당에 넣지 않는다")
  void dividendsOnHeldShares_SkipsZeroRows() {
    givenUserTrades(trade(1, TradeType.BUY, "10", LocalDate.of(2024, 1, 2)));
    DividendPayout zeroed = DividendPayout.of(USER, ACCOUNT, SYMBOL, EX_2025_05,
      DividendAmounts.of(new BigDecimal("0.25"), BigDecimal.ZERO), LocalDateTime.of(2026, 10, 1, 9, 0));
    given(dividendPayoutRepository.findByUserId(USER)).willReturn(List.of(
      payout(EX_2024_02, "0.24", "2.40", "0.37", "2.03"), zeroed));

    assertThat(dividendPayoutService.dividendsOnHeldShares(USER).get(POSITION)).isEqualByComparingTo("2.03");
  }
}
