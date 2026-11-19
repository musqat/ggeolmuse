package com.muscat.trade.infra.client.dto;

import java.math.BigDecimal;

// 분할 하나(분할이 반영된 첫 거래일 yyyy-MM-dd · 분할 전 1주가 분할 뒤 몇 주인지)
public record SplitDto(
  String date,
  BigDecimal ratio
) {
}
