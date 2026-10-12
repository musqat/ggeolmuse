package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
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
}
