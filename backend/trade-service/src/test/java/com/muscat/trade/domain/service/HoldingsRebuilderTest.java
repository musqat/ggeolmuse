package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.config.TradeProperties;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("보유 다시 쌓기")
class HoldingsRebuilderTest {

  @Mock
  private TradeRepository tradeRepository;

  @Mock
  private HoldingsRepository holdingsRepository;

  @Mock
  private TradeProperties tradeProperties;

  @Mock
  private TradeLogger tradeLogger;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private HoldingsRebuilder rebuilder;

  @BeforeEach
  void setUp() {
    TradeProperties.Calculation calculation = new TradeProperties.Calculation();
    calculation.setPricePrecision(2);
    given(tradeProperties.getCalculation()).willReturn(calculation);
    rebuilder = new HoldingsRebuilder(tradeRepository, holdingsRepository, tradeProperties, tradeLogger,
      meterRegistry);
  }

  private static Trade trade(long id, TradeType type, String quantity, String price) {
    return Trade.builder()
      .id(id).userId("user-1").accountId(20L).symbol("AAPL").tradeType(type)
      .quantity(new BigDecimal(quantity)).price(new BigDecimal(price))
      .totalAmount(new BigDecimal(quantity).multiply(new BigDecimal(price)))
      .build();
  }

  private void givenTrades(Trade... trades) {
    given(tradeRepository.findByUserIdAndAccountIdAndSymbolAndStatusOrderByExecutedAtAscIdAsc(
      "user-1", 20L, "AAPL", TradeStatus.COMPLETED)).willReturn(List.of(trades));
  }

  private void givenHoldings(Holdings holdings) {
    given(holdingsRepository.findByUserIdAndAccountIdAndSymbolWithLock("user-1", 20L, "AAPL"))
      .willReturn(Optional.ofNullable(holdings));
  }

  private double uncoveredCount() {
    return meterRegistry.get("trade.cancel.review").tag("reason", "uncovered_sell").counter().count();
  }

  @Test
  @DisplayName("남은 체결로 쌓은 값을 기존 보유에 덮어쓴다")
  void rebuild_OverwritesExisting() {
    Holdings existing = Holdings.builder().userId("user-1").accountId(20L).symbol("AAPL")
      .totalQuantity(new BigDecimal("15")).avgPurchasePrice(new BigDecimal("146.67"))
      .totalInvestedAmount(new BigDecimal("2200.00")).build();
    givenHoldings(existing);
    givenTrades(trade(1, TradeType.BUY, "5", "140.00"), trade(3, TradeType.SELL, "2", "150.00"));

    rebuilder.rebuild("user-1", 20L, "AAPL");

    assertThat(existing.getTotalQuantity()).isEqualByComparingTo("3");
    assertThat(existing.getAvgPurchasePrice()).isEqualByComparingTo("140.00");
    assertThat(existing.getTotalInvestedAmount()).isEqualByComparingTo("420.00");
    assertThat(uncoveredCount()).isZero();
  }

  @Test
  @DisplayName("남은 체결이 없으면 보유를 지운다")
  void rebuild_NoTrades_DeletesExisting() {
    Holdings existing = Holdings.builder().userId("user-1").accountId(20L).symbol("AAPL")
      .totalQuantity(new BigDecimal("10")).build();
    givenHoldings(existing);
    givenTrades();

    rebuilder.rebuild("user-1", 20L, "AAPL");

    verify(holdingsRepository).delete(existing);
  }

  @Test
  @DisplayName("보유 행이 없는데 쌓은 값이 있으면 새로 저장한다")
  void rebuild_NoExisting_SavesNew() {
    givenHoldings(null);
    givenTrades(trade(1, TradeType.BUY, "4", "100.00"));

    rebuilder.rebuild("user-1", 20L, "AAPL");

    ArgumentCaptor<Holdings> saved = ArgumentCaptor.forClass(Holdings.class);
    verify(holdingsRepository).save(saved.capture());
    assertThat(saved.getValue().getTotalQuantity()).isEqualByComparingTo("4");
  }

  @Test
  @DisplayName("보유보다 많은 매도를 만나면 비우고 이어 쌓고 카운터를 올린다")
  void rebuild_UncoveredSell_ResetsAndCounts() {
    givenHoldings(null);
    givenTrades(trade(1, TradeType.BUY, "5", "140.00"), trade(2, TradeType.SELL, "7", "150.00"),
      trade(3, TradeType.BUY, "2", "100.00"));

    rebuilder.rebuild("user-1", 20L, "AAPL");

    ArgumentCaptor<Holdings> saved = ArgumentCaptor.forClass(Holdings.class);
    verify(holdingsRepository).save(saved.capture());
    assertThat(saved.getValue().getTotalQuantity()).isEqualByComparingTo("2");
    assertThat(saved.getValue().getAvgPurchasePrice()).isEqualByComparingTo("100.00");
    assertThat(uncoveredCount()).isEqualTo(1.0);
  }
}
