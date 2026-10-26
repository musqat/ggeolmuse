package com.muscat.backtest.common.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.domain.model.ComparisonItem;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ComparisonCalculator 테스트")
class ComparisonCalculatorTest {

  @Test
  @DisplayName("여러 항목 비교 계산 성공")
  void calculate_MultipleItems_Success() {
    // Given
    ComparisonItem item1 = ComparisonItem.builder()
      .name("AAPL")
      .totalReturnPercent(BigDecimal.valueOf(20.0))
      .build();

    ComparisonItem item2 = ComparisonItem.builder()
      .name("GOOGL")
      .totalReturnPercent(BigDecimal.valueOf(15.0))
      .build();

    ComparisonItem item3 = ComparisonItem.builder()
      .name("MSFT")
      .totalReturnPercent(BigDecimal.valueOf(25.0))
      .build();

    List<ComparisonItem> items = Arrays.asList(item1, item2, item3);

    // When
    ComparisonCalculationResult result = ComparisonCalculator.calculate(items);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getBestPerformer()).isNotNull();
    assertThat(result.getBestPerformer().getName()).isEqualTo("MSFT");
    assertThat(result.getBestPerformer().getRank()).isEqualTo(1);

    assertThat(result.getWorstPerformer()).isNotNull();
    assertThat(result.getWorstPerformer().getName()).isEqualTo("GOOGL");
    assertThat(result.getWorstPerformer().getRank()).isEqualTo(3);

    assertThat(result.getAverageReturn()).isEqualByComparingTo(BigDecimal.valueOf(20.0));
    assertThat(result.getMedianReturn()).isEqualByComparingTo(BigDecimal.valueOf(20.0));
  }

  @Test
  @DisplayName("2개 항목 비교 계산 성공")
  void calculate_TwoItems_Success() {
    // Given
    ComparisonItem item1 = ComparisonItem.builder()
      .name("DCA 전략")
      .totalReturnPercent(BigDecimal.valueOf(10.0))
      .build();

    ComparisonItem item2 = ComparisonItem.builder()
      .name("조건부 매수 전략")
      .totalReturnPercent(BigDecimal.valueOf(12.0))
      .build();

    List<ComparisonItem> items = Arrays.asList(item1, item2);

    // When
    ComparisonCalculationResult result = ComparisonCalculator.calculate(items);

    // Then
    assertThat(result.getBestPerformer().getName()).isEqualTo("조건부 매수 전략");
    assertThat(result.getWorstPerformer().getName()).isEqualTo("DCA 전략");
    assertThat(result.getAverageReturn()).isEqualByComparingTo(BigDecimal.valueOf(11.0));
    assertThat(result.getMedianReturn()).isEqualByComparingTo(BigDecimal.valueOf(11.0));
  }

  @Test
  @DisplayName("항목들이 올바르게 정렬되고 순위가 매겨짐")
  void calculate_ItemsSortedAndRanked() {
    // Given - 의도적으로 순서를 섞음
    ComparisonItem itemLow = ComparisonItem.builder()
      .name("Low")
      .totalReturnPercent(BigDecimal.valueOf(5.0))
      .build();

    ComparisonItem itemHigh = ComparisonItem.builder()
      .name("High")
      .totalReturnPercent(BigDecimal.valueOf(15.0))
      .build();

    ComparisonItem itemMid = ComparisonItem.builder()
      .name("Mid")
      .totalReturnPercent(BigDecimal.valueOf(10.0))
      .build();

    List<ComparisonItem> items = Arrays.asList(itemLow, itemHigh, itemMid);

    // When
    ComparisonCalculationResult result = ComparisonCalculator.calculate(items);

    // Then - 정렬 확인
    assertThat(items.get(0).getName()).isEqualTo("High");
    assertThat(items.get(0).getRank()).isEqualTo(1);

    assertThat(items.get(1).getName()).isEqualTo("Mid");
    assertThat(items.get(1).getRank()).isEqualTo(2);

    assertThat(items.get(2).getName()).isEqualTo("Low");
    assertThat(items.get(2).getRank()).isEqualTo(3);
  }

  @Test
  @DisplayName("단일 항목 비교 계산")
  void calculate_SingleItem_Success() {
    // Given
    ComparisonItem item = ComparisonItem.builder()
      .name("AAPL")
      .totalReturnPercent(BigDecimal.valueOf(10.0))
      .build();

    List<ComparisonItem> items = Arrays.asList(item);

    // When
    ComparisonCalculationResult result = ComparisonCalculator.calculate(items);

    // Then
    assertThat(result.getBestPerformer()).isSameAs(result.getWorstPerformer());
    assertThat(result.getBestPerformer().getName()).isEqualTo("AAPL");
    assertThat(result.getAverageReturn()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
    assertThat(result.getMedianReturn()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
  }

  @Test
  @DisplayName("음수 수익률도 올바르게 처리")
  void calculate_NegativeReturns_Success() {
    // Given
    ComparisonItem item1 = ComparisonItem.builder()
      .name("Loser")
      .totalReturnPercent(BigDecimal.valueOf(-10.0))
      .build();

    ComparisonItem item2 = ComparisonItem.builder()
      .name("Winner")
      .totalReturnPercent(BigDecimal.valueOf(5.0))
      .build();

    ComparisonItem item3 = ComparisonItem.builder()
      .name("BigLoser")
      .totalReturnPercent(BigDecimal.valueOf(-20.0))
      .build();

    List<ComparisonItem> items = Arrays.asList(item1, item2, item3);

    // When
    ComparisonCalculationResult result = ComparisonCalculator.calculate(items);

    // Then
    assertThat(result.getBestPerformer().getName()).isEqualTo("Winner");
    assertThat(result.getWorstPerformer().getName()).isEqualTo("BigLoser"); // 5 - (-20)
  }

  @Test
  @DisplayName("동일한 수익률 처리")
  void calculate_EqualReturns_Success() {
    // Given
    ComparisonItem item1 = ComparisonItem.builder()
      .name("A")
      .totalReturnPercent(BigDecimal.valueOf(10.0))
      .build();

    ComparisonItem item2 = ComparisonItem.builder()
      .name("B")
      .totalReturnPercent(BigDecimal.valueOf(10.0))
      .build();

    ComparisonItem item3 = ComparisonItem.builder()
      .name("C")
      .totalReturnPercent(BigDecimal.valueOf(10.0))
      .build();

    List<ComparisonItem> items = Arrays.asList(item1, item2, item3);

    // When
    ComparisonCalculationResult result = ComparisonCalculator.calculate(items);

    // Then
    assertThat(result.getAverageReturn()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
    assertThat(result.getMedianReturn()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
    assertThat(items.get(0).getRank()).isEqualTo(1);
    assertThat(items.get(1).getRank()).isEqualTo(2);
    assertThat(items.get(2).getRank()).isEqualTo(3);
  }
}
