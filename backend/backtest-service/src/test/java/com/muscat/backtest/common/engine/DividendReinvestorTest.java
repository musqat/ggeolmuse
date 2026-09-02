package com.muscat.backtest.common.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import com.muscat.backtest.domain.model.StrategyTransaction;
import com.muscat.backtest.infra.client.MarketDataClient;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("DividendReinvestor 단위 테스트")
@ExtendWith(MockitoExtension.class)
class DividendReinvestorTest {

  @Mock MarketDataClient client;

  private static final LocalDate EX = LocalDate.of(2025, 6, 5);

  @BeforeEach
  void stub() {
    OHLCPriceDto p = new OHLCPriceDto("AAPL", EX,
        new BigDecimal("49"), new BigDecimal("51"), new BigDecimal("48"),
        new BigDecimal("50"), new BigDecimal("50"), 1L, "USD", true);
    lenient().when(client.getOHLCPriceRange(eq("AAPL"), any(), any())).thenReturn(List.of(p));
    Map<String, BigDecimal> fx = new HashMap<>();
    fx.put(EX.toString(), new BigDecimal("1300"));
    lenient().when(client.getBulkFxRates(anyList())).thenReturn(fx);
  }

  private static DividendHistoryDto history(BigDecimal amountPerShare) {
    DividendHistoryDto.DividendPayment pay = new DividendHistoryDto.DividendPayment();
    pay.setExDate(EX);
    pay.setAmount(amountPerShare);
    DividendHistoryDto h = new DividendHistoryDto();
    h.setSymbol("AAPL");
    h.setDividends(List.of(pay));
    return h;
  }

  private static List<StrategyTransaction> buyTransactions(double shares) {
    List<StrategyTransaction> txs = new ArrayList<>();
    txs.add(StrategyTransaction.builder()
        .date(LocalDate.of(2025, 1, 1)).actualDate(LocalDate.of(2025, 1, 1))
        .price(new BigDecimal("40")).shares(BigDecimal.valueOf(shares))
        .amount(new BigDecimal("100000")).fxRate(new BigDecimal("1300")).trigger("월정액")
        .build());
    return txs;
  }

  @Test
  @DisplayName("배당 재투자: 세후 배당금만큼 추가 매수 + 총액 반환")
  void reinvests() {
    var txs = buyTransactions(10);
    BigDecimal reinvested = DividendReinvestor.reinvest(
        client, history(new BigDecimal("2")), txs, "AAPL", LocalDate.of(2025, 1, 1),
        true, BigDecimal.ZERO, null, new BigDecimal("1300"));

    assertThat(reinvested).isEqualByComparingTo("20");          // 10주 × $2
    assertThat(txs).hasSize(2);                                  // 매수 + 재투자
    assertThat(txs.get(1).getShares()).isEqualByComparingTo("0.4"); // 20 / 50
    assertThat(txs.get(1).getTrigger()).isEqualTo("배당 재투자");
  }

  @Test
  @DisplayName("원천징수세 적용: 세후 금액으로 재투자")
  void appliesTax() {
    var txs = buyTransactions(10);
    BigDecimal reinvested = DividendReinvestor.reinvest(
        client, history(new BigDecimal("2")), txs, "AAPL", LocalDate.of(2025, 1, 1),
        true, new BigDecimal("0.154"), null, new BigDecimal("1300"));

    assertThat(reinvested).isEqualByComparingTo("16.92");        // 20 × (1-0.154)
  }

  @Test
  @DisplayName("재투자 비활성: 0 반환, 거래 추가 없음")
  void disabled() {
    var txs = buyTransactions(10);
    BigDecimal reinvested = DividendReinvestor.reinvest(
        client, history(new BigDecimal("2")), txs, "AAPL", LocalDate.of(2025, 1, 1),
        false, BigDecimal.ZERO, null, new BigDecimal("1300"));

    assertThat(reinvested).isEqualByComparingTo("0");
    assertThat(txs).hasSize(1);
  }
}
