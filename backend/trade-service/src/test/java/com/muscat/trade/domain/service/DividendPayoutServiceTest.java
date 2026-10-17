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

import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.exception.TradeException;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("배당 몫 재계산")
class DividendPayoutServiceTest {

  private static final String USER = "user-1";
  private static final long ACCOUNT = 20L;
  private static final String SYMBOL = "AAPL";
  private static final LocalDate EX_2024_02 = LocalDate.of(2024, 2, 9);
  private static final LocalDate EX_2025_05 = LocalDate.of(2025, 5, 12);
  private static final LocalDate EX_2026_08 = LocalDate.of(2026, 8, 11);

  @Mock
  private TradeRepository tradeRepository;

  @Mock
  private DividendPayoutRepository dividendPayoutRepository;

  @Mock
  private MarketServiceClientWrapper marketServiceClientWrapper;

  @Mock
  private DividendPayoutProducer dividendPayoutProducer;

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
  @DisplayName("배당락일 당일에 판 주식은 그 배당을 받는다")
  void reconcile_SellOnExDate_KeepsThatDividend() {
    givenTrades(
      trade(1, TradeType.BUY, "10", LocalDate.of(2024, 1, 2)),
      trade(2, TradeType.SELL, "10", EX_2025_05));
    givenDividends();
    given(dividendPayoutRepository.findByAccountIdAndSymbol(ACCOUNT, SYMBOL)).willReturn(new ArrayList<>());

    dividendPayoutService.reconcile(USER, ACCOUNT, SYMBOL);

    assertThat(published(2)).extracting(DividendPayout::getExDate, p -> p.getShares().stripTrailingZeros().toPlainString())
      .containsExactly(
        tuple(EX_2024_02, "10"),
        tuple(EX_2025_05, "10"));
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
  @DisplayName("시세 서비스에서 배당을 못 받으면 몫을 건드리지 않고 오류를 올린다")
  void reconcile_DividendFetchFails_KeepsPayouts() {
    givenTrades(trade(1, TradeType.BUY, "10", LocalDate.of(2024, 1, 2)));
    given(marketServiceClientWrapper.getDividends(eq(SYMBOL), eq("2024-01-02"), anyString()))
      .willThrow(new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR));
    List<DividendPayout> existing = paidThreeTimes();
    given(dividendPayoutRepository.findByAccountIdAndSymbol(ACCOUNT, SYMBOL)).willReturn(existing);

    assertThatThrownBy(() -> dividendPayoutService.reconcile(USER, ACCOUNT, SYMBOL))
      .isInstanceOf(TradeException.class)
      .hasFieldOrPropertyWithValue("errorCode", "MARKET_DATA_SERVICE_ERROR");
    assertThat(existing).extracting(p -> p.getNetAmount().toPlainString()).containsExactly("2.03", "2.11", "2.20");
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
}
