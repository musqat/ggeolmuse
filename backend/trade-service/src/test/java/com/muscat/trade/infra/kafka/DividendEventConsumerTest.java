package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.muscat.messaging.event.DividendUpdatedEvent;
import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.dto.TradePosition;
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
@DisplayName("배당 공시 컨슈머")
class DividendEventConsumerTest {

  private static final String SYMBOL = "AAPL";
  private static final LocalDate EX_DATE = LocalDate.of(2024, 9, 16);

  @Mock
  private TradeRepository tradeRepository;

  @Mock
  private DividendReconcileProducer dividendReconcileProducer;

  @Mock
  private Acknowledgment acknowledgment;

  @InjectMocks
  private DividendEventConsumer consumer;

  private static DividendUpdatedEvent event() {
    DividendUpdatedEvent e = new DividendUpdatedEvent();
    e.setSymbol(SYMBOL);
    e.setExDate(EX_DATE);
    e.setAmount(new BigDecimal("0.25"));
    e.setCurrency("USD");
    return e;
  }

  private static TradePosition position(String userId, long accountId) {
    return new TradePosition(userId, accountId, SYMBOL);
  }

  private void tradedBeforeExDate(TradePosition... positions) {
    given(tradeRepository.findPositionsTradedBefore(SYMBOL, TradeStatus.COMPLETED, EX_DATE))
      .willReturn(List.of(positions));
  }

  private void consume() {
    consumer.handleDividendUpdated(event(), 0, 100L, acknowledgment);
  }

  @Test
  @DisplayName("배당락일 전 거래가 있는 계좌마다 재계산을 한 번씩 요청하고 커밋한다")
  void requestsReconcilePerAccount() {
    tradedBeforeExDate(position("user-1", 20L), position("user-2", 21L));

    consume();

    verify(dividendReconcileProducer).request("user-1", 20L, SYMBOL);
    verify(dividendReconcileProducer).request("user-2", 21L, SYMBOL);
    verify(dividendReconcileProducer, times(2)).request(anyString(), anyLong(), anyString());
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("배당락일 전 거래가 없으면 요청 없이 커밋한다")
  void noTrades_AcknowledgesOnly() {
    tradedBeforeExDate();

    consume();

    verifyNoInteractions(dividendReconcileProducer);
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("요청 하나가 실패해도 나머지 계좌는 요청하고, 커밋하지 않고 배당 지급 실패로 올린다")
  void oneRequestFails_OthersStillRequested() {
    tradedBeforeExDate(position("user-1", 20L), position("user-2", 21L));
    willThrow(new TradeException(TradeResponse.EVENT_PUBLISH_FAILED))
      .given(dividendReconcileProducer).request("user-1", 20L, SYMBOL);

    assertThatThrownBy(this::consume)
      .isInstanceOf(TradeException.class)
      .hasFieldOrPropertyWithValue("errorCode", "DIVIDEND_PAYMENT_FAILED");
    verify(dividendReconcileProducer).request(eq("user-2"), eq(21L), eq(SYMBOL));
    verify(acknowledgment, never()).acknowledge();
  }
}
