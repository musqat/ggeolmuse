package com.muscat.trade.domain.dto.response;

import java.math.BigDecimal;

// 계좌마다 넣은 배당과 받을 몫의 합, 그 차이(받을 몫 − 넣은 배당), 다른 배당락일 수
public record PayoutDiffResponse(
  String userId,
  Long accountId,
  BigDecimal settled,
  BigDecimal target,
  BigDecimal delta,
  int differingRows
) {
}
