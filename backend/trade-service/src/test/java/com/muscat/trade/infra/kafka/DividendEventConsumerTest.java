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
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.repository.HoldingsRepository;
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
  private HoldingsRepository holdingsRepository;

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

  private static Holdings holdings(String userId, long accountId, String quantity) {
    return Holdings.builder()
      .userId(userId)
      .accountId(accountId)
      .symbol(SYMBOL)
      .totalQuantity(new BigDecimal(quantity))
      .build();
  }

  private void consume(DividendUpdatedEvent event) {
    consumer.handleDividendUpdated(event, 0, 100L, acknowledgment);
  }

  @Test
  @DisplayName("보유자가 없으면 아무것도 발행하지 않고 커밋한다")
  void 보유자_없음() {
    given(holdingsRepository.findBySymbol(SYMBOL)).willReturn(List.of());

    consume(event("0.25"));

    verify(dividendEventProducer, never())
      .publishDividendReceived(anyString(), any(), anyString(), anyString(), any(), any(), any());
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("보유 수량만큼 배당금을 계산해 발행한다")
  void 금액_계산() {
    given(holdingsRepository.findBySymbol(SYMBOL))
      .willReturn(List.of(holdings("user-1", 10L, "13")));

    consume(event("0.25"));

    // 0.25 * 13 = 3.25
    verify(dividendEventProducer).publishDividendReceived(
      "user-1", 10L, SYMBOL, "2024-09-16",
      new BigDecimal("0.25"), new BigDecimal("13"), new BigDecimal("3.25"));
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("소수 셋째 자리에서 반올림해 두 자리로 맞춘다")
  void 반올림() {
    given(holdingsRepository.findBySymbol(SYMBOL))
      .willReturn(List.of(holdings("user-1", 10L, "3.7")));

    consume(event("0.235"));

    // 0.235 * 3.7 = 0.8695 → 0.87 (HALF_UP, scale 2)
    verify(dividendEventProducer).publishDividendReceived(
      eq("user-1"), eq(10L), eq(SYMBOL), eq("2024-09-16"),
      any(), any(), eq(new BigDecimal("0.87")));
  }

  @Test
  @DisplayName("보유자가 여럿이면 각각 발행한다")
  void 다수_보유자() {
    given(holdingsRepository.findBySymbol(SYMBOL)).willReturn(List.of(
      holdings("user-1", 10L, "10"),
      holdings("user-2", 20L, "5"),
      holdings("user-3", 30L, "1")));

    consume(event("0.25"));

    verify(dividendEventProducer, times(3))
      .publishDividendReceived(anyString(), any(), anyString(), anyString(), any(), any(), any());
    verify(dividendEventProducer).publishDividendReceived(
      eq("user-1"), eq(10L), any(), any(), any(), any(), eq(new BigDecimal("2.50")));
    verify(dividendEventProducer).publishDividendReceived(
      eq("user-3"), eq(30L), any(), any(), any(), any(), eq(new BigDecimal("0.25")));
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("보유 수량이 0이면 0원으로 발행한다")
  void 수량_0() {
    given(holdingsRepository.findBySymbol(SYMBOL))
      .willReturn(List.of(holdings("user-1", 10L, "0")));

    consume(event("0.25"));

    // 전량 매도한 보유 행이 남아 있으면 0원 이벤트가 나간다
    verify(dividendEventProducer).publishDividendReceived(
      eq("user-1"), eq(10L), any(), any(), any(), any(), eq(new BigDecimal("0.00")));
  }

  @Test
  @DisplayName("한 명이 실패해도 나머지는 계속 지급한다")
  void 부분_실패() {
    given(holdingsRepository.findBySymbol(SYMBOL)).willReturn(List.of(
      holdings("user-1", 10L, "10"),
      holdings("user-2", 20L, "5"),
      holdings("user-3", 30L, "1")));

    // user-1, user-3 호출은 이 스텁과 안 맞는 게 정상이라 lenient 로 둔다
    lenient().doThrow(new IllegalStateException("발행 실패"))
      .when(dividendEventProducer).publishDividendReceived(
        eq("user-2"), any(), any(), any(), any(), any(), any());

    consume(event("0.25"));

    // 개별 실패는 삼키고 루프를 계속 돈다
    verify(dividendEventProducer).publishDividendReceived(
      eq("user-1"), any(), any(), any(), any(), any(), any());
    verify(dividendEventProducer).publishDividendReceived(
      eq("user-3"), any(), any(), any(), any(), any(), any());
    // 실패가 섞여도 커밋한다. 실패분은 재처리되지 않는다
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("전원 실패해도 커밋한다")
  void 전원_실패() {
    given(holdingsRepository.findBySymbol(SYMBOL))
      .willReturn(List.of(holdings("user-1", 10L, "10")));

    willThrow(new IllegalStateException("발행 실패"))
      .given(dividendEventProducer).publishDividendReceived(
        any(), any(), any(), any(), any(), any(), any());

    consume(event("0.25"));

    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("조회가 실패하면 커밋하지 않고 예외를 올린다")
  void 조회_실패() {
    given(holdingsRepository.findBySymbol(SYMBOL))
      .willThrow(new IllegalStateException("DB 연결 끊김"));

    // 커밋을 건너뛰어야 Kafka 가 재시도한다
    assertThatThrownBy(() -> consume(event("0.25")))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("DB 연결 끊김");

    verify(acknowledgment, never()).acknowledge();
  }

  @Test
  @DisplayName("배당락일은 yyyy-MM-dd 문자열로 넘어간다")
  void 날짜_형식() {
    given(holdingsRepository.findBySymbol(SYMBOL))
      .willReturn(List.of(holdings("user-1", 10L, "1")));

    consume(event("0.25"));

    verify(dividendEventProducer).publishDividendReceived(
      any(), any(), any(), eq("2024-09-16"), any(), any(), any());
  }
}
