package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.event.TradeCancelledEvent;
import com.muscat.trade.domain.event.TradeSavedEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("배당 몫 재계산 요청 시점")
class DividendReconcileTriggerTest {

  private final DividendReconcileProducer producer = mock(DividendReconcileProducer.class);
  private final DividendReconcileTrigger trigger = new DividendReconcileTrigger(producer);

  private static Trade trade() {
    return Trade.builder().id(7L).userId("user-1").accountId(20L).symbol("AAPL").tradeType(TradeType.BUY)
      .quantity(new BigDecimal("10")).tradeDate(LocalDate.of(2024, 1, 2)).build();
  }

  @Test
  @DisplayName("체결이 커밋되면 그 계좌 · 종목의 재계산을 요청한다")
  void onTradeSaved_RequestsReconcile() {
    trigger.onTradeSaved(new TradeSavedEvent(trade()));

    verify(producer).request("user-1", 20L, "AAPL");
  }

  @Test
  @DisplayName("체결 취소가 커밋되면 그 계좌 · 종목의 재계산을 요청한다")
  void onTradeCancelled_RequestsReconcile() {
    trigger.onTradeCancelled(new TradeCancelledEvent("user-1", 20L, "AAPL"));

    verify(producer).request("user-1", 20L, "AAPL");
  }

  @Test
  @DisplayName("요청을 못 보내도 체결 처리 쪽으로 오류를 올리지 않는다")
  void requestFails_Swallowed() {
    willThrow(new TradeException(TradeResponse.EVENT_PUBLISH_FAILED)).given(producer).request("user-1", 20L, "AAPL");

    assertThatCode(() -> trigger.onTradeSaved(new TradeSavedEvent(trade()))).doesNotThrowAnyException();
  }
}
