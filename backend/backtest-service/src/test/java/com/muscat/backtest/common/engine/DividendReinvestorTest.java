package com.muscat.backtest.common.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;

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
  @DisplayName("배당락일 당일에 산 주식은 그 배당을 받지 못해 재투자도 없다")
  void boughtOnExDate_NoReinvest() {
    List<StrategyTransaction> txs = new ArrayList<>();
    txs.add(StrategyTransaction.builder()
        .date(EX.minusDays(2)).actualDate(EX)
        .price(new BigDecimal("40")).shares(BigDecimal.TEN)
        .amount(new BigDecimal("100000")).fxRate(new BigDecimal("1300")).trigger("월정액")
        .build());

    BigDecimal reinvested = DividendReinvestor.reinvest(
        client, history(new BigDecimal("2")), txs, "AAPL", EX.minusDays(2),
        true, BigDecimal.ZERO, null, new BigDecimal("1300"));

    assertThat(reinvested).isEqualByComparingTo("0");
    assertThat(txs).hasSize(1);
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

  private static DividendHistoryDto historyOf(DividendHistoryDto.DividendPayment... payments) {
    DividendHistoryDto h = new DividendHistoryDto();
    h.setSymbol("AAPL");
    h.setDividends(List.of(payments));
    return h;
  }

  private static DividendHistoryDto.DividendPayment payment(LocalDate exDate) {
    DividendHistoryDto.DividendPayment pay = new DividendHistoryDto.DividendPayment();
    pay.setExDate(exDate);
    pay.setAmount(new BigDecimal("2"));
    return pay;
  }

  @Test
  @DisplayName("배당 이력이 없거나 비어 있으면 시세를 조회하지 않고 0")
  void noDividends() {
    DividendHistoryDto nullList = new DividendHistoryDto();

    for (DividendHistoryDto history : new DividendHistoryDto[] {null, nullList, historyOf()}) {
      var txs = buyTransactions(10);
      assertThat(DividendReinvestor.reinvest(client, history, txs, "AAPL", LocalDate.of(2025, 1, 1),
        true, BigDecimal.ZERO, null, new BigDecimal("1300"))).isEqualByComparingTo("0");
      assertThat(txs).hasSize(1);
    }
    verify(client, never()).getOHLCPriceRange(any(), any(), any());
  }

  @Test
  @DisplayName("권리락일이 없거나 첫 매수 전이거나 오늘 이후인 배당은 걸러져 시세를 조회하지 않는다")
  void filtersDividends() {
    var txs = buyTransactions(10);

    BigDecimal reinvested = DividendReinvestor.reinvest(client,
      historyOf(payment(null), payment(LocalDate.of(2024, 12, 1)), payment(LocalDate.now().plusDays(10))),
      txs, "AAPL", LocalDate.of(2025, 1, 1), true, BigDecimal.ZERO, null, new BigDecimal("1300"));

    assertThat(reinvested).isEqualByComparingTo("0");
    assertThat(txs).hasSize(1);
    verify(client, never()).getOHLCPriceRange(any(), any(), any());
  }

  @Test
  @DisplayName("권리락일 시세가 없으면 그 배당은 재투자하지 않는다")
  void missingPrice() {
    given(client.getOHLCPriceRange(eq("AAPL"), any(), any())).willReturn(List.of(new OHLCPriceDto("AAPL", EX.minusDays(1),
      BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 1L, "USD", true)));
    var txs = buyTransactions(10);

    BigDecimal reinvested = DividendReinvestor.reinvest(client, history(new BigDecimal("2")), txs, "AAPL",
      LocalDate.of(2025, 1, 1), true, BigDecimal.ZERO, null, new BigDecimal("1300"));

    assertThat(reinvested).isEqualByComparingTo("0");
    assertThat(txs).hasSize(1);
  }

  @Test
  @DisplayName("매수 환율을 직접 주면 일괄 환율을 조회하지 않고 재투자 거래에 그 환율을 쓴다")
  void manualFx() {
    var txs = buyTransactions(10);

    DividendReinvestor.reinvest(client, history(new BigDecimal("2")), txs, "AAPL", LocalDate.of(2025, 1, 1),
      true, BigDecimal.ZERO, new BigDecimal("1250"), new BigDecimal("1300"));

    assertThat(txs.get(1).getFxRate()).isEqualByComparingTo("1250");
    verify(client, never()).getBulkFxRates(anyList());
  }
}
