package com.muscat.trade.domain.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalInt;

// 체결가와 체결일 저가 · 고가(분할 기준)로 체결가에 아직 안 들어간 분할 찾기
public final class SplitPricing {

  // 계좌 슬리피지율 상한(1%)
  private static final BigDecimal SLIPPAGE_MARGIN = new BigDecimal("0.01");

  // 센트로 저장된 체결가의 반올림 폭
  private static final BigDecimal CENT_ROUNDING = new BigDecimal("0.005");

  private SplitPricing() {
  }

  // ratios 는 거래일 뒤 분할 비율(날짜 순), 체결가가 저가 ~ 고가 × 뒤쪽 분할 곱 범위에 드는 분할 수(0 은 이미 분할 기준, 맞는 수가 없거나 둘 이상이면 빈 값)
  public static OptionalInt pendingCount(BigDecimal price, BigDecimal low, BigDecimal high, List<BigDecimal> ratios) {
    if (ratios.isEmpty()) {
      return OptionalInt.of(0);
    }
    if (!positive(price) || !positive(low) || !positive(high)) {
      return OptionalInt.empty();
    }
    OptionalInt found = OptionalInt.empty();
    for (int count = 0; count <= ratios.size(); count++) {
      BigDecimal product = lastProduct(ratios, count);
      if (!within(price, low.multiply(product), high.multiply(product))) {
        continue;
      }
      if (found.isPresent()) {
        return OptionalInt.empty();
      }
      found = OptionalInt.of(count);
    }
    return found;
  }

  // 뒤쪽 count 개 분할 비율의 곱
  public static BigDecimal lastProduct(List<BigDecimal> ratios, int count) {
    return ratios.subList(ratios.size() - count, ratios.size()).stream()
      .reduce(BigDecimal.ONE, BigDecimal::multiply);
  }

  // 슬리피지 · 센트 반올림 여유를 둔 저가 ~ 고가 안
  private static boolean within(BigDecimal price, BigDecimal low, BigDecimal high) {
    BigDecimal floor = low.multiply(BigDecimal.ONE.subtract(SLIPPAGE_MARGIN)).subtract(CENT_ROUNDING);
    BigDecimal ceiling = high.multiply(BigDecimal.ONE.add(SLIPPAGE_MARGIN)).add(CENT_ROUNDING);
    return price.compareTo(floor) >= 0 && price.compareTo(ceiling) <= 0;
  }

  private static boolean positive(BigDecimal value) {
    return value != null && value.signum() > 0;
  }
}
