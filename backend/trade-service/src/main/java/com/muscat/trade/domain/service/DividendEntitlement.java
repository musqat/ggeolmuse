package com.muscat.trade.domain.service;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 배당을 받을 수량 — 배당락일 전(거래일 < 배당락일)에 체결된 매수 합 − 매도 합(당일 · 이후 매수는 못 받고 매도는 받음)
public final class DividendEntitlement {

  private static final Comparator<Trade> TRADE_ORDER = Comparator.comparing(Trade::getTradeDate)
    .thenComparing(Trade::getExecutedAt, Comparator.nullsLast(Comparator.naturalOrder()))
    .thenComparing(Trade::getId, Comparator.nullsLast(Comparator.naturalOrder()));

  private DividendEntitlement() {
  }

  // 매수 한 건과 그중 배당락일 전날까지 남은 수량
  public record HeldBuy(Trade buy, BigDecimal shares) {
  }

  // 한 계좌의 거래로 구한 배당락일 전날 보유 수량
  public static BigDecimal sharesHeldBefore(List<Trade> trades, LocalDate exDate) {
    return trades.stream()
      .filter(trade -> counts(trade, exDate))
      .map(trade -> trade.getTradeType() == TradeType.SELL ? trade.getQuantity().negate() : trade.getQuantity())
      .reduce(BigDecimal.ZERO, BigDecimal::add)
      .max(BigDecimal.ZERO);
  }

  // 배당락일 전날 보유분을 매수 건마다 나눔(배당락일 전 매도는 먼저 산 매수부터 빼는 FIFO, 계좌별 합은 sharesHeldBefore, 남은 수량 0 인 매수는 제외)
  public static List<HeldBuy> remainingByBuyBefore(List<Trade> trades, LocalDate exDate) {
    Map<Long, List<Trade>> tradesByAccount = trades.stream()
      .filter(trade -> counts(trade, exDate))
      .sorted(TRADE_ORDER)
      .collect(Collectors.groupingBy(Trade::getAccountId, LinkedHashMap::new, Collectors.toList()));

    List<HeldBuy> held = new ArrayList<>();
    for (List<Trade> accountTrades : tradesByAccount.values()) {
      BigDecimal sold = accountTrades.stream()
        .filter(trade -> trade.getTradeType() == TradeType.SELL)
        .map(Trade::getQuantity)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      for (Trade trade : accountTrades) {
        if (trade.getTradeType() != TradeType.BUY) {
          continue;
        }
        BigDecimal used = trade.getQuantity().min(sold);
        sold = sold.subtract(used);
        BigDecimal remaining = trade.getQuantity().subtract(used);
        if (remaining.signum() > 0) {
          held.add(new HeldBuy(trade, remaining));
        }
      }
    }
    return held;
  }

  private static boolean counts(Trade trade, LocalDate exDate) {
    return trade.getStatus() == TradeStatus.COMPLETED && trade.getTradeDate().isBefore(exDate);
  }
}
