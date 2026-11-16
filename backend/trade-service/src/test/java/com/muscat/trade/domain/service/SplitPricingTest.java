package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("체결가에 아직 안 들어간 분할 찾기")
class SplitPricingTest {

  private static BigDecimal d(String value) {
    return new BigDecimal(value);
  }

  @Test
  @DisplayName("체결가가 체결일 저가 ~ 고가 안이면 이미 분할 기준이라 0")
  void alreadySplitScale() {
    assertThat(SplitPricing.pendingCount(d("101"), d("99"), d("102"), List.of(d("4")))).hasValue(0);
  }

  @Test
  @DisplayName("체결가를 비율로 나눈 값이 저가 ~ 고가 안이면 1")
  void beforeSplit() {
    assertThat(SplitPricing.pendingCount(d("404"), d("99"), d("102"), List.of(d("4")))).hasValue(1);
  }

  @Test
  @DisplayName("병합(0.1)도 체결가를 0.1 로 나눈 값이 저가 ~ 고가 안이면 1")
  void reverseSplit() {
    assertThat(SplitPricing.pendingCount(d("1.95"), d("19.2"), d("19.8"), List.of(d("0.1")))).hasValue(1);
    assertThat(SplitPricing.pendingCount(d("19.8"), d("19.2"), d("19.8"), List.of(d("0.1")))).hasValue(0);
  }

  @Test
  @DisplayName("분할 둘이면 체결가에 아직 안 들어간 뒤쪽 분할 수")
  void twoSplits() {
    List<BigDecimal> ratios = List.of(d("2"), d("3"));

    assertThat(SplitPricing.pendingCount(d("600"), d("100"), d("100"), ratios)).hasValue(2);
    assertThat(SplitPricing.pendingCount(d("300"), d("100"), d("100"), ratios)).hasValue(1);
    assertThat(SplitPricing.pendingCount(d("100"), d("100"), d("100"), ratios)).hasValue(0);
  }

  @Test
  @DisplayName("슬리피지 1% 와 센트 반올림 폭까지는 저가 ~ 고가를 벗어나도 범위 안")
  void slippageMargin() {
    assertThat(SplitPricing.pendingCount(d("101.004"), d("99"), d("100"), List.of(d("4")))).hasValue(0);
    assertThat(SplitPricing.pendingCount(d("392.036"), d("99"), d("100"), List.of(d("4")))).hasValue(1);
    assertThat(SplitPricing.pendingCount(d("101.006"), d("99"), d("100"), List.of(d("4")))).isEmpty();
  }

  @Test
  @DisplayName("비율 1.05 에서 하루 범위가 좁으면 분할 뒤 고가 체결은 0, 분할 전 저가 체결은 1")
  void smallRatio_NarrowRange() {
    List<BigDecimal> ratios = List.of(d("1.05"));

    assertThat(SplitPricing.pendingCount(d("100.5"), d("99.5"), d("100.5"), ratios)).hasValue(0);
    assertThat(SplitPricing.pendingCount(d("104.475"), d("99.5"), d("100.5"), ratios)).hasValue(1);
  }

  @Test
  @DisplayName("비율 1.05 에서 하루 범위가 넓어 0 과 1 이 둘 다 맞으면 빈 값")
  void smallRatio_WideRange_Ambiguous() {
    assertThat(SplitPricing.pendingCount(d("101"), d("96"), d("104"), List.of(d("1.05")))).isEmpty();
  }

  @Test
  @DisplayName("어느 분할 수로도 저가 ~ 고가에 들지 않으면 빈 값")
  void noCountFits() {
    assertThat(SplitPricing.pendingCount(d("250"), d("99"), d("101"), List.of(d("4")))).isEmpty();
  }

  @Test
  @DisplayName("분할이 없으면 0, 체결가 · 저가 · 고가가 비었거나 0 이하면 빈 값")
  void nothingToCompare() {
    assertThat(SplitPricing.pendingCount(d("400"), d("99"), d("101"), List.of())).hasValue(0);
    assertThat(SplitPricing.pendingCount(d("400"), null, d("101"), List.of(d("4")))).isEmpty();
    assertThat(SplitPricing.pendingCount(d("400"), d("99"), d("0"), List.of(d("4")))).isEmpty();
    assertThat(SplitPricing.pendingCount(null, d("99"), d("101"), List.of(d("4")))).isEmpty();
  }

  @Test
  @DisplayName("뒤쪽 분할 비율의 곱")
  void lastProduct() {
    List<BigDecimal> ratios = List.of(d("2"), d("3"), d("0.5"));

    assertThat(SplitPricing.lastProduct(ratios, 2)).isEqualByComparingTo("1.5");
    assertThat(SplitPricing.lastProduct(ratios, 3)).isEqualByComparingTo("3");
    assertThat(SplitPricing.lastProduct(ratios, 0)).isEqualByComparingTo("1");
  }
}
