package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("배당 받을 수량 (배당락일 전날 보유)")
class DividendEntitlementTest {

  private static final LocalDate EX = LocalDate.of(2024, 9, 16);

  private static Trade trade(TradeType type, String quantity, LocalDate date, TradeStatus status) {
    return Trade.builder().tradeType(type).quantity(new BigDecimal(quantity)).tradeDate(date).status(status).build();
  }

  private static Trade buy(String quantity, LocalDate date) {
    return trade(TradeType.BUY, quantity, date, TradeStatus.COMPLETED);
  }

  private static Trade sell(String quantity, LocalDate date) {
    return trade(TradeType.SELL, quantity, date, TradeStatus.COMPLETED);
  }

  private static Trade lot(long id, long accountId, TradeType type, String quantity, LocalDate date) {
    return Trade.builder().id(id).accountId(accountId).tradeType(type).quantity(new BigDecimal(quantity))
      .tradeDate(date).build();
  }

  // "매수 id:남은 수량"
  private static List<String> held(List<Trade> trades) {
    return DividendEntitlement.remainingByBuyBefore(trades, EX).stream()
      .map(h -> h.buy().getId() + ":" + h.shares().toPlainString())
      .toList();
  }

  @Test
  @DisplayName("배당락일 전 매수는 받고, 배당락일 당일 · 이후 매수는 못 받는다")
  void buysBeforeExDateOnly() {
    List<Trade> trades = List.of(buy("10", EX.minusDays(1)), buy("5", EX), buy("3", EX.plusDays(1)));

    assertThat(DividendEntitlement.sharesHeldBefore(trades, EX)).isEqualByComparingTo("10");
  }

  @Test
  @DisplayName("배당락일 전 매도는 빼고, 배당락일 당일 · 이후 매도는 빼지 않는다(그날까지 갖고 있었다)")
  void sellsBeforeExDateSubtracted() {
    List<Trade> trades = List.of(buy("10", EX.minusDays(5)), buy("10", EX.minusDays(4)),
      sell("5", EX.minusDays(1)), sell("15", EX));

    assertThat(DividendEntitlement.sharesHeldBefore(trades, EX)).isEqualByComparingTo("15");
  }

  @Test
  @DisplayName("취소된 거래는 세지 않고, 배당락일 전에 다 팔았으면 0")
  void cancelledIgnoredAndSoldOutIsZero() {
    List<Trade> trades = List.of(buy("10", EX.minusDays(3)), sell("10", EX.minusDays(2)),
      trade(TradeType.BUY, "7", EX.minusDays(1), TradeStatus.CANCELLED));

    assertThat(DividendEntitlement.sharesHeldBefore(trades, EX)).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("매수 건별: 배당락일 전 매도를 먼저 산 매수부터 빼고, 배당락일 당일 매수 · 매도는 세지 않는다")
  void remainingByBuy_Fifo() {
    LocalDate day = EX.minusDays(4);
    Trade later = lot(2, 10, TradeType.BUY, "10", day);
    later.setExecutedAt(day.atTime(10, 0));
    Trade earlier = lot(1, 10, TradeType.BUY, "10", day);
    earlier.setExecutedAt(day.atTime(9, 0));
    // 같은 날 두 매수는 체결 시각 순서(입력 순서는 일부러 뒤집음)
    List<Trade> trades = List.of(later, earlier,
      lot(3, 10, TradeType.SELL, "5", EX.minusDays(1)),
      lot(4, 10, TradeType.BUY, "7", EX),
      lot(5, 10, TradeType.SELL, "8", EX));

    assertThat(held(trades)).containsExactly("1:5", "2:10");
    assertThat(DividendEntitlement.remainingByBuyBefore(trades, EX).stream()
      .map(DividendEntitlement.HeldBuy::shares).reduce(BigDecimal.ZERO, BigDecimal::add))
      .isEqualByComparingTo(DividendEntitlement.sharesHeldBefore(trades, EX));
  }

  @Test
  @DisplayName("매수 건별: 매도는 같은 계좌의 매수에서만 빼고, 다 판 매수는 뺀다")
  void remainingByBuy_PerAccount() {
    List<Trade> trades = List.of(
      lot(1, 20, TradeType.BUY, "4", EX.minusDays(6)),
      lot(2, 10, TradeType.BUY, "10", EX.minusDays(5)),
      lot(3, 30, TradeType.BUY, "2", EX.minusDays(5)),
      lot(4, 10, TradeType.SELL, "3", EX.minusDays(2)),
      lot(5, 30, TradeType.SELL, "2", EX.minusDays(1)));

    assertThat(held(trades)).containsExactly("1:4", "2:7");
  }
}
