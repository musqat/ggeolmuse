package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.event.TradeCancelledEvent;
import com.muscat.trade.domain.event.TradeConfirmedEvent;
import com.muscat.trade.domain.event.TradeSavedEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("배당 몫 재계산 요청 시점")
class DividendReconcileTriggerTest {

  private final DividendReconcileProducer producer = mock(DividendReconcileProducer.class);
  private final List<Runnable> tasks = new ArrayList<>();
  private final DividendReconcileTrigger trigger = new DividendReconcileTrigger(producer, tasks::add);

  private static Trade trade() {
    return Trade.builder().id(7L).userId("user-1").accountId(20L).symbol("AAPL").tradeType(TradeType.BUY)
      .quantity(new BigDecimal("10")).tradeDate(LocalDate.of(2024, 1, 2)).build();
  }

  private void runTasks() {
    tasks.forEach(Runnable::run);
  }

  @Test
  @DisplayName("체결이 커밋되면 그 계좌 · 종목의 재계산 요청을 실행기에 넘겨 따로 보낸다")
  void onTradeSaved_RequestsReconcileOnExecutor() {
    trigger.onTradeSaved(new TradeSavedEvent(trade()));

    verifyNoInteractions(producer);
    runTasks();
    verify(producer).request("user-1", 20L, "AAPL");
  }

  @Test
  @DisplayName("대기 매수 저장은 보유가 그대로라 재계산을 요청하지 않는다")
  void onTradeSaved_PendingBuy_NoRequest() {
    Trade pending = trade();
    pending.setStatus(TradeStatus.PENDING);

    trigger.onTradeSaved(new TradeSavedEvent(pending));

    runTasks();
    verifyNoInteractions(producer);
  }

  @Test
  @DisplayName("체결 취소가 커밋되면 그 계좌 · 종목의 재계산 요청을 실행기에 넘겨 따로 보낸다")
  void onTradeCancelled_RequestsReconcileOnExecutor() {
    trigger.onTradeCancelled(new TradeCancelledEvent("user-1", 20L, "AAPL"));

    verifyNoInteractions(producer);
    runTasks();
    verify(producer).request("user-1", 20L, "AAPL");
  }

  @Test
  @DisplayName("대기 매수가 완료되면 그 계좌 · 종목의 재계산 요청을 실행기에 넘겨 따로 보낸다")
  void onTradeConfirmed_RequestsReconcileOnExecutor() {
    trigger.onTradeConfirmed(new TradeConfirmedEvent("user-1", 20L, "AAPL"));

    verifyNoInteractions(producer);
    runTasks();
    verify(producer).request("user-1", 20L, "AAPL");
  }

  @Test
  @DisplayName("요청을 못 보내도 오류를 올리지 않는다")
  void requestFails_Swallowed() {
    willThrow(new TradeException(TradeResponse.EVENT_PUBLISH_FAILED)).given(producer).request("user-1", 20L, "AAPL");
    trigger.onTradeSaved(new TradeSavedEvent(trade()));

    assertThatCode(this::runTasks).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("실행기가 받지 못해도 체결 처리 쪽으로 오류를 올리지 않는다")
  void executorRejects_Swallowed() {
    DividendReconcileTrigger full = new DividendReconcileTrigger(producer, task -> {
      throw new RejectedExecutionException("full");
    });

    assertThatCode(() -> full.onTradeSaved(new TradeSavedEvent(trade()))).doesNotThrowAnyException();
  }
}
