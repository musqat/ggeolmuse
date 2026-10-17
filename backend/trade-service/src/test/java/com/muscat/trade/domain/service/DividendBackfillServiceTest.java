package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.dto.TradePosition;
import com.muscat.trade.domain.dto.response.PayoutDiffResponse;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.DividendPayoutRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import com.muscat.trade.infra.client.UserServiceClient;
import com.muscat.trade.infra.client.dto.DividendDto;
import com.muscat.trade.infra.client.dto.DividendSettlementDto;
import com.muscat.trade.infra.kafka.DividendReconcileProducer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("배당 몫 백필 · 미리 보기")
class DividendBackfillServiceTest {

  private static final LocalDate EX_DATE = LocalDate.of(2024, 2, 9);

  @Mock
  private TradeRepository tradeRepository;

  @Mock
  private DividendPayoutRepository dividendPayoutRepository;

  @Mock
  private MarketServiceClientWrapper marketServiceClientWrapper;

  @Mock
  private UserServiceClient userServiceClient;

  @Mock
  private DividendReconcileProducer dividendReconcileProducer;

  @InjectMocks
  private DividendBackfillService dividendBackfillService;

  private static Trade trade(String userId, long accountId, TradeType type, LocalDate tradeDate) {
    return Trade.builder().userId(userId).accountId(accountId).symbol("AAPL").tradeType(type)
      .quantity(new BigDecimal("10")).tradeDate(tradeDate).executedAt(tradeDate.atTime(10, 0))
      .status(TradeStatus.COMPLETED).build();
  }

  private void givenTrades(String userId, long accountId, Trade... trades) {
    given(tradeRepository.findByUserIdAndAccountIdAndSymbolAndStatusOrderByExecutedAtAscIdAsc(
      userId, accountId, "AAPL", TradeStatus.COMPLETED)).willReturn(List.of(trades));
  }

  @Test
  @DisplayName("체결 기록과 몫 표의 자리를 겹치지 않게 모아 자리마다 재계산을 한 번씩 요청한다")
  void requestAll_OncePerPosition() {
    given(tradeRepository.findDistinctPositions(TradeStatus.COMPLETED)).willReturn(List.of(
      new TradePosition("user-1", 20L, "AAPL"), new TradePosition("user-2", 21L, "AAPL")));
    given(dividendPayoutRepository.findDistinctPositions()).willReturn(List.of(
      new TradePosition("user-1", 20L, "AAPL"), new TradePosition("user-3", 22L, "MSFT")));

    int requested = dividendBackfillService.requestAll();

    assertThat(requested).isEqualTo(3);
    verify(dividendReconcileProducer).request("user-1", 20L, "AAPL");
    verify(dividendReconcileProducer).request("user-2", 21L, "AAPL");
    verify(dividendReconcileProducer).request("user-3", 22L, "MSFT");
  }

  @Test
  @DisplayName("계좌마다 넣은 배당과 받을 몫을 견줘 다른 계좌만 차이와 함께 준다")
  void diff_OnlyAccountsThatDiffer() {
    given(tradeRepository.findDistinctPositions(TradeStatus.COMPLETED)).willReturn(List.of(
      new TradePosition("user-1", 20L, "AAPL"), new TradePosition("user-2", 21L, "AAPL"),
      new TradePosition("user-3", 22L, "AAPL")));
    given(dividendPayoutRepository.findDistinctPositions()).willReturn(List.of());
    LocalDate bought = LocalDate.of(2024, 1, 2);
    givenTrades("user-1", 20L, trade("user-1", 20L, TradeType.BUY, bought));
    givenTrades("user-2", 21L, trade("user-2", 21L, TradeType.BUY, bought));
    givenTrades("user-3", 22L, trade("user-3", 22L, TradeType.BUY, bought),
      trade("user-3", 22L, TradeType.SELL, LocalDate.of(2024, 1, 5)));
    given(marketServiceClientWrapper.getDividends(eq("AAPL"), eq("2024-01-02"), anyString()))
      .willReturn(List.of(new DividendDto("2024-02-09", new BigDecimal("0.24"))));
    given(userServiceClient.getDividendSettlements()).willReturn(List.of(
      new DividendSettlementDto(21L, "AAPL", EX_DATE, new BigDecimal("2.03")),
      new DividendSettlementDto(22L, "AAPL", EX_DATE, new BigDecimal("2.12"))));

    List<PayoutDiffResponse> diff = dividendBackfillService.diff();

    // 0.24 × 10 → 세후 2.03. 계좌 21 은 같아서 빠지고, 22 는 배당락일 전에 다 팔아 받을 몫이 0
    assertThat(diff).extracting(PayoutDiffResponse::accountId, d -> d.settled().toPlainString(),
        d -> d.target().toPlainString(), d -> d.delta().toPlainString(), PayoutDiffResponse::differingRows)
      .containsExactly(
        tuple(20L, "0", "2.03", "2.03", 1),
        tuple(22L, "2.12", "0", "-2.12", 1));
    verify(marketServiceClientWrapper, times(1)).getDividends(eq("AAPL"), anyString(), anyString());
  }
}
