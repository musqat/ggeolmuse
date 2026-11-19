package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.entity.StockSplit;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.StockSplitRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.StockSplitService.Outcome;
import com.muscat.trade.domain.service.StockSplitService.Split;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import com.muscat.trade.infra.client.dto.DividendDto;
import com.muscat.trade.infra.kafka.DividendReconcileProducer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("주식 분할 반영")
class StockSplitServiceTest {

  private static final String SYMBOL = "NVDA";
  private static final String USER = "user-1";
  private static final LocalDate FIRST_SPLIT = LocalDate.of(2024, 1, 10);
  private static final LocalDate SECOND_SPLIT = LocalDate.of(2024, 3, 10);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 9, 0);

  @Mock
  private TradeRepository tradeRepository;

  @Mock
  private StockSplitRepository stockSplitRepository;

  @Mock
  private MarketServiceClientWrapper marketServiceClientWrapper;

  @Mock
  private HoldingsRebuilder holdingsRebuilder;

  @Mock
  private DividendReconcileProducer dividendReconcileProducer;

  private StockSplitService service;
  private SimpleMeterRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    service = new StockSplitService(tradeRepository, stockSplitRepository, marketServiceClientWrapper,
      holdingsRebuilder, dividendReconcileProducer, new TransactionTemplate(mock(PlatformTransactionManager.class)),
      Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneId.of("Asia/Seoul")), registry);
  }

  private static Trade trade(long id, long accountId, TradeType type, String quantity, String price,
                             LocalDate tradeDate) {
    return Trade.builder().id(id).userId(USER).accountId(accountId).symbol(SYMBOL).tradeType(type)
      .quantity(new BigDecimal(quantity)).price(new BigDecimal(price)).totalAmount(new BigDecimal("1000"))
      .tradeDate(tradeDate).executedAt(tradeDate.atTime(10, 0).plusSeconds(id)).status(TradeStatus.COMPLETED)
      .build();
  }

  private static Trade buy(long id, long accountId, String quantity, String price, LocalDate tradeDate) {
    return trade(id, accountId, TradeType.BUY, quantity, price, tradeDate);
  }

  private static OHLCPriceDto candle(LocalDate date, String low, String high) {
    BigDecimal close = new BigDecimal(low);
    return new OHLCPriceDto(SYMBOL, date, close, new BigDecimal(high), new BigDecimal(low), close, close, 1L,
      "USD", true);
  }

  private static OHLCPriceDto close(LocalDate date, String close) {
    return candle(date, close, close);
  }

  private void givenTrades(LocalDate before, Trade... trades) {
    List<Trade> list = new ArrayList<>(List.of(trades));
    given(tradeRepository.findFirstBySymbolAndTradeDateBeforeOrderByTradeDateAsc(SYMBOL, before))
      .willReturn(list.stream().min(TradeOrder.LEDGER));
    given(tradeRepository.findBySymbolAndTradeDateBeforeOrderByIdAsc(SYMBOL, before)).willReturn(list);
  }

  private List<StockSplit> savedSplits(int times) {
    ArgumentCaptor<StockSplit> saved = ArgumentCaptor.forClass(StockSplit.class);
    verify(stockSplitRepository, times(times)).save(saved.capture());
    return saved.getAllValues();
  }

  @Test
  @DisplayName("분할일 · 비율이 비었거나 비율이 0 이하 · 1 이면 건너뜀")
  void invalidSplit_Skipped() {
    List<Split> invalid = new ArrayList<>();
    invalid.add(new Split(null, new BigDecimal("4")));
    invalid.add(new Split(FIRST_SPLIT, null));
    invalid.add(new Split(FIRST_SPLIT, BigDecimal.ZERO));
    invalid.add(new Split(FIRST_SPLIT, BigDecimal.ONE));

    assertThat(service.apply(SYMBOL, invalid)).isEqualTo(new Outcome(0, 0));

    verifyNoInteractions(stockSplitRepository, tradeRepository, marketServiceClientWrapper);
  }

  @Test
  @DisplayName("분할일 전 체결이 없으면 시세를 묻지 않고 맞춘 수 0 으로 기록")
  void noTradesBeforeSplit_RecordsSplit() {
    givenTrades(FIRST_SPLIT);

    assertThat(service.apply(SYMBOL, List.of(new Split(FIRST_SPLIT, new BigDecimal("4"))))).isEqualTo(new Outcome(0, 0));

    assertThat(savedSplits(1)).extracting(StockSplit::getAdjustedTrades).containsExactly(0);
    verifyNoInteractions(marketServiceClientWrapper, holdingsRebuilder, dividendReconcileProducer);
  }

  @Test
  @DisplayName("분할 둘을 한 번에 받으면 체결마다 아직 안 들어간 뒤쪽 분할만 곱하고 분할마다 맞춘 수 기록")
  void twoSplits_AppliesPendingSuffix() {
    Trade beforeBoth = buy(1L, 1L, "10", "600.00", LocalDate.of(2024, 1, 2));
    Trade between = buy(2L, 2L, "5", "300.00", LocalDate.of(2024, 2, 1));
    Trade alreadyScaled = buy(3L, 2L, "4", "101.00", LocalDate.of(2024, 2, 2));
    givenTrades(SECOND_SPLIT, beforeBoth, between, alreadyScaled);
    given(marketServiceClientWrapper.getOHLCPriceRange(SYMBOL, "2024-01-02", "2024-03-09")).willReturn(List.of(
      close(LocalDate.of(2024, 1, 2), "100.00"), close(LocalDate.of(2024, 2, 1), "100.00"),
      close(LocalDate.of(2024, 2, 2), "100.00")));

    Outcome outcome = service.apply(SYMBOL, List.of(
      new Split(SECOND_SPLIT, new BigDecimal("3")), new Split(FIRST_SPLIT, new BigDecimal("2"))));

    assertThat(outcome).isEqualTo(new Outcome(2, 0));
    assertThat(beforeBoth.getQuantity()).isEqualByComparingTo("60");
    assertThat(beforeBoth.getPrice()).isEqualByComparingTo("100");
    assertThat(beforeBoth.getSplitAppliedAt()).isEqualTo(NOW);
    assertThat(between.getQuantity()).isEqualByComparingTo("15");
    assertThat(between.getPrice()).isEqualByComparingTo("100");
    assertThat(alreadyScaled.getSplitRatio()).isNull();
    assertThat(savedSplits(2)).extracting(StockSplit::getSplitDate, StockSplit::getAdjustedTrades)
      .containsExactly(tuple(FIRST_SPLIT, 1), tuple(SECOND_SPLIT, 2));
    verify(holdingsRebuilder).rebuild(USER, 1L, SYMBOL, false);
    verify(holdingsRebuilder).rebuild(USER, 2L, SYMBOL, false);
    verify(dividendReconcileProducer).request(USER, 1L, SYMBOL);
    verify(dividendReconcileProducer).request(USER, 2L, SYMBOL);
  }

  @Test
  @DisplayName("이미 기록된 분할도 다시 판정해 기록 뒤 분할 기준으로 바뀐 시세에 맞춰 체결을 맞추고 기록에 더함")
  void recordedSplit_JudgedAgain() {
    Trade trade = buy(1L, 1L, "10", "400.00", LocalDate.of(2024, 1, 2));
    givenTrades(FIRST_SPLIT, trade);
    given(marketServiceClientWrapper.getOHLCPriceRange(SYMBOL, "2024-01-02", "2024-01-09"))
      .willReturn(List.of(close(LocalDate.of(2024, 1, 2), "100.00")));
    StockSplit recorded = StockSplit.of(SYMBOL, FIRST_SPLIT, new BigDecimal("4"), 0, NOW.minusDays(1));
    given(stockSplitRepository.findBySymbolAndSplitDate(SYMBOL, FIRST_SPLIT)).willReturn(Optional.of(recorded));

    assertThat(service.apply(SYMBOL, List.of(new Split(FIRST_SPLIT, new BigDecimal("4"))))).isEqualTo(new Outcome(1, 0));

    assertThat(trade.getQuantity()).isEqualByComparingTo("40");
    assertThat(recorded.getAdjustedTrades()).isEqualTo(1);
    assertThat(recorded.getAppliedAt()).isEqualTo(NOW);
    verify(stockSplitRepository, never()).save(any());
  }

  @Test
  @DisplayName("이미 분할 기준인 체결뿐이면 다시 받아도 체결 · 기록 · 재계산 요청 그대로")
  void alreadyAdjusted_NoChange() {
    Trade trade = buy(1L, 1L, "40", "100.00", LocalDate.of(2024, 1, 2));
    givenTrades(FIRST_SPLIT, trade);
    given(marketServiceClientWrapper.getOHLCPriceRange(SYMBOL, "2024-01-02", "2024-01-09"))
      .willReturn(List.of(close(LocalDate.of(2024, 1, 2), "100.00")));
    StockSplit recorded = StockSplit.of(SYMBOL, FIRST_SPLIT, new BigDecimal("4"), 1, NOW.minusDays(1));
    given(stockSplitRepository.findBySymbolAndSplitDate(SYMBOL, FIRST_SPLIT)).willReturn(Optional.of(recorded));

    assertThat(service.apply(SYMBOL, List.of(new Split(FIRST_SPLIT, new BigDecimal("4"))))).isEqualTo(new Outcome(0, 0));

    assertThat(trade.getSplitRatio()).isNull();
    assertThat(recorded.getAdjustedTrades()).isEqualTo(1);
    assertThat(recorded.getAppliedAt()).isEqualTo(NOW.minusDays(1));
    verifyNoInteractions(holdingsRebuilder, dividendReconcileProducer);
  }

  @Test
  @DisplayName("체결일 시세가 없으면 체결 · 기록을 바꾸지 않고 시세 없음 예외")
  void missingCandle_Throws() {
    Trade priced = buy(1L, 1L, "10", "400.00", LocalDate.of(2024, 1, 2));
    Trade unpriced = buy(2L, 2L, "1", "400.00", LocalDate.of(2024, 1, 3));
    givenTrades(FIRST_SPLIT, priced, unpriced);
    given(marketServiceClientWrapper.getOHLCPriceRange(SYMBOL, "2024-01-02", "2024-01-09"))
      .willReturn(List.of(close(LocalDate.of(2024, 1, 2), "100.00")));

    assertThatThrownBy(() -> service.apply(SYMBOL, List.of(new Split(FIRST_SPLIT, new BigDecimal("4")))))
      .isInstanceOf(TradeException.class)
      .extracting(e -> ((TradeException) e).getErrorCode())
      .isEqualTo(TradeResponse.PRICE_DATA_UNAVAILABLE.name());

    assertThat(priced.getSplitRatio()).isNull();
    verify(stockSplitRepository, never()).save(any());
    verifyNoInteractions(holdingsRebuilder, dividendReconcileProducer);
  }

  @Test
  @DisplayName("체결가로 분할 전후를 가리지 못하는 체결은 맞추지 않음")
  void ambiguousTrade_NotAdjusted() {
    Trade ambiguous = buy(1L, 1L, "10", "101.00", LocalDate.of(2024, 1, 2));
    givenTrades(FIRST_SPLIT, ambiguous);
    given(marketServiceClientWrapper.getOHLCPriceRange(SYMBOL, "2024-01-02", "2024-01-09"))
      .willReturn(List.of(candle(LocalDate.of(2024, 1, 2), "96.00", "104.00")));

    assertThat(service.apply(SYMBOL, List.of(new Split(FIRST_SPLIT, new BigDecimal("1.05")))))
      .isEqualTo(new Outcome(0, 0));

    assertThat(ambiguous.getSplitRatio()).isNull();
    assertThat(registry.get("trade.split.unresolved").tag("reason", "ambiguous").counter().count()).isEqualTo(1);
    verifyNoInteractions(holdingsRebuilder, dividendReconcileProducer);
  }

  @Test
  @DisplayName("1:3 병합(0.33333333)은 매수 합과 매도 합을 한 번씩만 반올림해 1주 매수 셋 · 3주 매도가 같은 수량")
  void reverseSplit_KeepsBuySellTotals() {
    LocalDate day = LocalDate.of(2024, 1, 2);
    Trade buy1 = buy(1L, 1L, "1", "10.00", day);
    Trade buy2 = buy(2L, 1L, "1", "10.00", day);
    Trade buy3 = buy(3L, 1L, "1", "10.00", day);
    Trade sell = trade(4L, 1L, TradeType.SELL, "3", "10.00", day);
    Trade buy4 = buy(5L, 2L, "2", "10.00", day);
    Trade sell1 = trade(6L, 2L, TradeType.SELL, "1", "10.00", day);
    Trade sell2 = trade(7L, 2L, TradeType.SELL, "1", "10.00", day);
    givenTrades(FIRST_SPLIT, buy1, buy2, buy3, sell, buy4, sell1, sell2);
    given(marketServiceClientWrapper.getOHLCPriceRange(SYMBOL, "2024-01-02", "2024-01-09"))
      .willReturn(List.of(close(day, "30.00")));

    service.apply(SYMBOL, List.of(new Split(FIRST_SPLIT, new BigDecimal("0.33333333"))));

    assertThat(buy1.getQuantity().add(buy2.getQuantity()).add(buy3.getQuantity()))
      .isEqualByComparingTo(sell.getQuantity()).isEqualByComparingTo("1");
    assertThat(buy4.getQuantity()).isEqualByComparingTo(sell1.getQuantity().add(sell2.getQuantity()));
  }

  @Test
  @DisplayName("분할로 맞춘 체결과 분할 기준 주당 배당으로 다시 센 몫은 분할 전 몫과 같음")
  void dividendTargets_SameAfterSplit() {
    LocalDate exDate = LocalDate.of(2024, 1, 5);
    Trade trade = buy(1L, 1L, "10", "400.00", LocalDate.of(2024, 1, 2));
    BigDecimal before = DividendPayoutService.computeTargets(List.of(trade),
      List.of(new DividendDto(exDate.toString(), new BigDecimal("0.24")))).get(exDate).netAmount();
    givenTrades(FIRST_SPLIT, trade);
    given(marketServiceClientWrapper.getOHLCPriceRange(SYMBOL, "2024-01-02", "2024-01-09"))
      .willReturn(List.of(close(LocalDate.of(2024, 1, 2), "100.00")));

    service.apply(SYMBOL, List.of(new Split(FIRST_SPLIT, new BigDecimal("4"))));

    BigDecimal after = DividendPayoutService.computeTargets(List.of(trade),
      List.of(new DividendDto(exDate.toString(), new BigDecimal("0.06")))).get(exDate).netAmount();
    assertThat(after).isEqualByComparingTo(before);
  }

  @Test
  @DisplayName("같은 분할을 동시에 반영한 트랜잭션이 먼저 기록해 유니크 제약에 걸리면 한 번 더 반영해 남은 분할까지 맞춤")
  void concurrentDuplicate_RetriesOnce() {
    Trade rolledBack = buy(1L, 1L, "10", "300.00", LocalDate.of(2024, 2, 1));
    Trade reloaded = buy(1L, 1L, "10", "300.00", LocalDate.of(2024, 2, 1));
    given(tradeRepository.findFirstBySymbolAndTradeDateBeforeOrderByTradeDateAsc(SYMBOL, SECOND_SPLIT))
      .willReturn(Optional.of(rolledBack));
    given(tradeRepository.findBySymbolAndTradeDateBeforeOrderByIdAsc(SYMBOL, SECOND_SPLIT))
      .willReturn(List.of(rolledBack))
      .willReturn(List.of(reloaded));
    given(marketServiceClientWrapper.getOHLCPriceRange(SYMBOL, "2024-02-01", "2024-03-09"))
      .willReturn(List.of(close(LocalDate.of(2024, 2, 1), "100.00")));
    given(stockSplitRepository.save(any()))
      .willThrow(new DataIntegrityViolationException("uk_stock_splits"))
      .willAnswer(invocation -> invocation.getArgument(0));
    given(stockSplitRepository.existsBySymbolAndSplitDate(SYMBOL, FIRST_SPLIT)).willReturn(true);

    Outcome outcome = service.apply(SYMBOL, List.of(
      new Split(FIRST_SPLIT, new BigDecimal("2")), new Split(SECOND_SPLIT, new BigDecimal("3"))));

    assertThat(outcome.adjusted()).isEqualTo(1);
    assertThat(reloaded.getQuantity()).isEqualByComparingTo("30");
    verify(tradeRepository, times(2)).findBySymbolAndTradeDateBeforeOrderByIdAsc(SYMBOL, SECOND_SPLIT);
    verify(dividendReconcileProducer).request(USER, 1L, SYMBOL);
  }

  @Test
  @DisplayName("유니크 제약이 아닌 제약 위반이면(기록된 분할 없음) 예외 그대로, 재계산 요청 없음")
  void otherIntegrityViolation_Throws() {
    givenTrades(FIRST_SPLIT, buy(1L, 1L, "10", "400.00", LocalDate.of(2024, 1, 2)));
    given(marketServiceClientWrapper.getOHLCPriceRange(SYMBOL, "2024-01-02", "2024-01-09"))
      .willReturn(List.of(close(LocalDate.of(2024, 1, 2), "100.00")));
    given(stockSplitRepository.save(any())).willThrow(new DataIntegrityViolationException("numeric overflow"));

    assertThatThrownBy(() -> service.apply(SYMBOL, List.of(new Split(FIRST_SPLIT, new BigDecimal("4")))))
      .isInstanceOf(DataIntegrityViolationException.class);

    verifyNoInteractions(dividendReconcileProducer);
  }

  @Test
  @DisplayName("배당 몫 재계산 요청이 실패해도 맞춘 결과 그대로이고 실패 수를 돌려줌")
  void reconcileRequestFails_CountsFailure() {
    givenTrades(FIRST_SPLIT, buy(1L, 1L, "10", "400.00", LocalDate.of(2024, 1, 2)));
    given(marketServiceClientWrapper.getOHLCPriceRange(SYMBOL, "2024-01-02", "2024-01-09"))
      .willReturn(List.of(close(LocalDate.of(2024, 1, 2), "100.00")));
    willThrow(new TradeException(TradeResponse.EVENT_PUBLISH_FAILED))
      .given(dividendReconcileProducer).request(anyString(), anyLong(), anyString());

    assertThat(service.apply(SYMBOL, List.of(new Split(FIRST_SPLIT, new BigDecimal("4")))))
      .isEqualTo(new Outcome(1, 1));
  }

  @Test
  @DisplayName("시세 조회가 실패하면 체결을 바꾸지 않고 예외")
  void marketDataFails_Throws() {
    Trade trade = buy(1L, 1L, "10", "400.00", LocalDate.of(2024, 1, 2));
    given(tradeRepository.findFirstBySymbolAndTradeDateBeforeOrderByTradeDateAsc(SYMBOL, FIRST_SPLIT))
      .willReturn(Optional.of(trade));
    given(marketServiceClientWrapper.getOHLCPriceRange(SYMBOL, "2024-01-02", "2024-01-09"))
      .willThrow(new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR));

    assertThatThrownBy(() -> service.apply(SYMBOL, List.of(new Split(FIRST_SPLIT, new BigDecimal("4")))))
      .isInstanceOf(TradeException.class);

    assertThat(trade.getQuantity()).isEqualByComparingTo("10");
    verify(stockSplitRepository, never()).save(any());
  }
}
