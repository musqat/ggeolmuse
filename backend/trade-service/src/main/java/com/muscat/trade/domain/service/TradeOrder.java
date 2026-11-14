package com.muscat.trade.domain.service;

import com.muscat.trade.domain.entity.Trade;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;

// 보유 원장과 계좌 통계가 체결을 쌓는 순서(거래일 → 체결 시각 → ID)
public final class TradeOrder {

  public static final Comparator<Trade> LEDGER = Comparator
    .comparing(Trade::getTradeDate, Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
    .thenComparing(Trade::getExecutedAt, Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()))
    .thenComparing(Trade::getId, Comparator.nullsFirst(Comparator.<Long>naturalOrder()));

  private TradeOrder() {
  }
}
