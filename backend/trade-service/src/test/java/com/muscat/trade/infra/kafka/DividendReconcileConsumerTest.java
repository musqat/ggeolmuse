package com.muscat.trade.infra.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.domain.event.DividendReconcileRequest;
import com.muscat.trade.domain.service.DividendPayoutService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

@DisplayName("배당 몫 재계산 요청 컨슈머")
class DividendReconcileConsumerTest {

  private final DividendPayoutService dividendPayoutService = mock(DividendPayoutService.class);
  private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
  private final DividendReconcileConsumer consumer = new DividendReconcileConsumer(dividendPayoutService);

  private final DividendReconcileRequest request = DividendReconcileRequest.of("user-1", 20L, "AAPL");

  @Test
  @DisplayName("요청을 받으면 그 계좌 · 종목을 다시 계산하고 커밋한다")
  void reconcilesAndAcknowledges() {
    consumer.handle(request, acknowledgment);

    verify(dividendPayoutService).reconcile("user-1", 20L, "AAPL", false, List.of());
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("백필 요청이면 다시 보내기와 넣은 배당락일을 넘긴다")
  void backfillRequest_PassesResendAndSettledExDates() {
    LocalDate exDate = LocalDate.of(2025, 5, 12);

    consumer.handle(new DividendReconcileRequest("user-1", 20L, "AAPL", true, List.of(exDate)), acknowledgment);

    verify(dividendPayoutService).reconcile("user-1", 20L, "AAPL", true, List.of(exDate));
  }

  @Test
  @DisplayName("넣은 배당락일이 없는 요청은 빈 목록으로 다시 계산한다")
  void missingSettledExDates_TreatedAsEmpty() {
    consumer.handle(new DividendReconcileRequest("user-1", 20L, "AAPL", false, null), acknowledgment);

    verify(dividendPayoutService).reconcile("user-1", 20L, "AAPL", false, List.of());
  }

  @Test
  @DisplayName("재계산이 실패하면 커밋하지 않고 다시 올린다")
  void reconcileFails_Rethrows() {
    given(dividendPayoutService.reconcile("user-1", 20L, "AAPL", false, List.of()))
      .willThrow(new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR));

    assertThatThrownBy(() -> consumer.handle(request, acknowledgment))
      .isInstanceOf(TradeException.class);
    verify(acknowledgment, never()).acknowledge();
  }
}
