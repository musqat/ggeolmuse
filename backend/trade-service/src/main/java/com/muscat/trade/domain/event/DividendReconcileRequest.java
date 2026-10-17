package com.muscat.trade.domain.event;

// 배당 몫 재계산 요청. trading.dividend.reconcile 의 값
public record DividendReconcileRequest(String userId, Long accountId, String symbol) {
}
