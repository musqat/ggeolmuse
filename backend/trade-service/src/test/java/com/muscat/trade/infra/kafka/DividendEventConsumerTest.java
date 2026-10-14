package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.DividendUpdatedEvent;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.TradeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
@DisplayName("DividendEventConsumer 단위 테스트")
class DividendEventConsumerTest {

  private static final String SYMBOL = "AAPL";
  private static final LocalDate EX_DATE = LocalDate.of(2024, 9, 16);

  @Mock
  private TradeRepository tradeRepository;

  @Mock
  private DividendEventProducer dividendEventProducer;

  @Mock
  private Acknowledgment acknowledgment;

  @InjectMocks
  private DividendEventConsumer consumer;

  private static DividendUpdatedEvent event(String amount) {
    DividendUpdatedEvent e = new DividendUpdatedEvent();
    e.setSymbol(SYMBOL);
    e.setExDate(EX_DATE);
    e.setAmount(new BigDecimal(amount));
    e.setCurrency("USD");
    return e;
  }

  private static Trade trade(String userId, long accountId, TradeType type, String quantity) {
    return Trade.builder().userId(userId).accountId(accountId).symbol(SYMBOL).tradeType(type)
      .quantity(new BigDecimal(quantity)).tradeDate(EX_DATE.minusDays(3)).build();
  }

  private void tradesBeforeExDate(Trade... trades) {
    given(tradeRepository.findBySymbolAndStatusAndTradeDateBeforeOrderByTradeDateAsc(SYMBOL, TradeStatus.COMPLETED, EX_DATE))
      .willReturn(List.of(trades));
  }

  private void consume(DividendUpdatedEvent event) {
    consumer.handleDividendUpdated(event, 0, 100L, acknowledgment);
  }

  @Test
  @DisplayName("배당락일 전 거래가 없으면 아무것도 발행하지 않고 커밋한다")
  void 대상_없음() {
    tradesBeforeExDate();

    consume(event("0.25"));

    verify(dividendEventProducer, never())
      .publishDividendReceived(anyString(), any(), anyString(), anyString(), any(), any(), any());
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("배당락일 전날 보유 수량으로 세후(15.4% 원천징수) 배당금을 발행한다")
  void 세후_금액() {
    tradesBeforeExDate(trade("user-1", 10L, TradeType.BUY, "13"));

    consume(event("0.25"));

    // 세전 0.25 × 13 = 3.25, 세금 3.25 × 0.154 = 0.5005 → 0.50, 세후 2.75
    verify(dividendEventProducer).publishDividendReceived(
      "user-1", 10L, SYMBOL, "2024-09-16",
      new BigDecimal("0.25"), new BigDecimal("13"), new BigDecimal("2.75"));
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("세전 · 세금을 각각 두 자리로 반올림한 뒤 뺀다")
  void 반올림() {
    tradesBeforeExDate(trade("user-1", 10L, TradeType.BUY, "3.7"));

    consume(event("0.235"));

    // 세전 0.8695 → 0.87, 세금 0.87 × 0.154 = 0.13398 → 0.13, 세후 0.74
    verify(dividendEventProducer).publishDividendReceived(
      eq("user-1"), eq(10L), eq(SYMBOL), eq("2024-09-16"), any(), any(), eq(new BigDecimal("0.74")));
  }

  @Test
  @DisplayName("계좌마다 따로 계산해 발행하고, 배당락일 전에 다 판 계좌는 건너뛴다")
  void 계좌별() {
    tradesBeforeExDate(
      trade("user-1", 10L, TradeType.BUY, "10"),
      trade("user-1", 10L, TradeType.SELL, "4"),
      trade("user-2", 20L, TradeType.BUY, "5"),
      trade("user-2", 20L, TradeType.SELL, "5"),
      trade("user-3", 30L, TradeType.BUY, "1"));

    consume(event("0.25"));

    verify(dividendEventProducer, times(2))
      .publishDividendReceived(anyString(), any(), anyString(), anyString(), any(), any(), any());
    // 6주: 세전 1.50, 세금 0.23, 세후 1.27
    verify(dividendEventProducer).publishDividendReceived(
      eq("user-1"), eq(10L), any(), any(), any(), eq(new BigDecimal("6")), eq(new BigDecimal("1.27")));
    // 1주: 세전 0.25, 세금 0.04, 세후 0.21
    verify(dividendEventProducer).publishDividendReceived(
      eq("user-3"), eq(30L), any(), any(), any(), any(), eq(new BigDecimal("0.21")));
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("한 계좌가 실패해도 나머지는 지급하고, 커밋하지 않고 예외를 올려 다시 받는다")
  void 부분_실패() {
    tradesBeforeExDate(
      trade("user-1", 10L, TradeType.BUY, "10"),
      trade("user-2", 20L, TradeType.BUY, "5"),
      trade("user-3", 30L, TradeType.BUY, "1"));

    // user-1, user-3 호출은 이 스텁과 안 맞는 게 정상이라 lenient 로 둔다
    lenient().doThrow(new IllegalStateException("발행 실패"))
      .when(dividendEventProducer).publishDividendReceived(
        eq("user-2"), any(), any(), any(), any(), any(), any());

    assertThatThrownBy(() -> consume(event("0.25")))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("배당 지급 실패");

    verify(dividendEventProducer).publishDividendReceived(
      eq("user-1"), any(), any(), any(), any(), any(), any());
    verify(dividendEventProducer).publishDividendReceived(
      eq("user-3"), any(), any(), any(), any(), any(), any());
    // 커밋하지 않아 Kafka 가 다시 준다. 이미 지급한 계좌는 user-service 가 같은 배당으로 걸러낸다
    verify(acknowledgment, never()).acknowledge();
  }

  @Test
  @DisplayName("전원 실패하면 커밋하지 않고 예외를 올린다")
  void 전원_실패() {
    tradesBeforeExDate(trade("user-1", 10L, TradeType.BUY, "10"));

    willThrow(new IllegalStateException("발행 실패"))
      .given(dividendEventProducer).publishDividendReceived(
        any(), any(), any(), any(), any(), any(), any());

    assertThatThrownBy(() -> consume(event("0.25")))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("배당 지급 실패");

    verify(acknowledgment, never()).acknowledge();
  }

  @Test
  @DisplayName("조회가 실패하면 커밋하지 않고 예외를 올린다")
  void 조회_실패() {
    given(tradeRepository.findBySymbolAndStatusAndTradeDateBeforeOrderByTradeDateAsc(SYMBOL, TradeStatus.COMPLETED, EX_DATE))
      .willThrow(new IllegalStateException("DB 연결 끊김"));

    // 커밋을 건너뛰어야 Kafka 가 재시도한다
    assertThatThrownBy(() -> consume(event("0.25")))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("DB 연결 끊김");

    verify(acknowledgment, never()).acknowledge();
  }
}
