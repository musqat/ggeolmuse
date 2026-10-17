package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.event.DividendReconcileRequest;
import com.muscat.trade.domain.service.DividendPayoutService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

@DisplayName("배당 몫 재계산 요청 컨슈머")
class DividendReconcileConsumerTest {

  private final DividendPayoutService dividendPayoutService = mock(DividendPayoutService.class);
  private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
  private final DividendReconcileConsumer consumer = new DividendReconcileConsumer(dividendPayoutService);

  private final DividendReconcileRequest request = new DividendReconcileRequest("user-1", 20L, "AAPL");

  @Test
  @DisplayName("요청을 받으면 그 계좌 · 종목을 다시 계산하고 커밋한다")
  void reconcilesAndAcknowledges() {
    consumer.handle(request, acknowledgment);

    verify(dividendPayoutService).reconcile("user-1", 20L, "AAPL");
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("재계산이 실패하면 커밋하지 않고 다시 올린다")
  void reconcileFails_Rethrows() {
    given(dividendPayoutService.reconcile("user-1", 20L, "AAPL"))
      .willThrow(new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR));

    assertThatThrownBy(() -> consumer.handle(request, acknowledgment))
      .isInstanceOf(TradeException.class);
    verify(acknowledgment, never()).acknowledge();
  }
}
