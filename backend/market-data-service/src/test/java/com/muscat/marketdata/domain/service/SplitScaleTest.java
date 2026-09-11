package com.muscat.marketdata.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("분할 종가 차이 판정")
class SplitScaleTest {

  private static BigDecimal d(String value) {
    return new BigDecimal(value);
  }

  @Test
  @DisplayName("4배 차이는 분할로 본다")
  void 네배() {
    assertThat(SplitScale.isScaleChange(d("400"), d("100"))).isTrue();
  }

  @Test
  @DisplayName("역분할 방향도 분할로 본다")
  void 역분할() {
    assertThat(SplitScale.isScaleChange(d("1"), d("10"))).isTrue();
  }

  @Test
  @DisplayName("1.25배는 분할로 보고 1.24배는 아니다")
  void 경계() {
    assertThat(SplitScale.isScaleChange(d("125"), d("100"))).isTrue();
    assertThat(SplitScale.isScaleChange(d("124"), d("100"))).isFalse();
  }

  @Test
  @DisplayName("null 이나 0 이면 false")
  void 값_없음() {
    assertThat(SplitScale.isScaleChange(null, d("100"))).isFalse();
    assertThat(SplitScale.isScaleChange(d("100"), BigDecimal.ZERO)).isFalse();
  }
}
