package com.muscat.trade.domain.event;

import java.time.LocalDate;
import java.util.List;

// 배당 몫 재계산 요청(trading.dividend.reconcile 의 값)
public record DividendReconcileRequest(String userId, Long accountId, String symbol, boolean resend,
  List<LocalDate> settledExDates) {

  public static DividendReconcileRequest of(String userId, Long accountId, String symbol) {
    return new DividendReconcileRequest(userId, accountId, symbol, false, List.of());
  }
}
