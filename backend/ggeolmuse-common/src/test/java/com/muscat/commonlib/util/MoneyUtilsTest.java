package com.muscat.commonlib.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.commonlib.exception.ServiceException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("금액 · 환율 계산")
class MoneyUtilsTest {

  private static final BigDecimal RATE = new BigDecimal("1300");

  @Test
  @DisplayName("500 ~ 2000원은 경계를 포함해 정상 범위이고, 밖이거나 없으면 아니다")
  void exchangeRateRange() {
    assertThat(MoneyUtils.isExchangeRateInRange(new BigDecimal("500"))).isTrue();
    assertThat(MoneyUtils.isExchangeRateInRange(new BigDecimal("2000"))).isTrue();
    assertThat(MoneyUtils.isExchangeRateInRange(new BigDecimal("499.999999"))).isFalse();
    assertThat(MoneyUtils.isExchangeRateInRange(new BigDecimal("2000.000001"))).isFalse();
    assertThat(MoneyUtils.isExchangeRateInRange(null)).isFalse();
  }

  @Test
  @DisplayName("환율 검증은 0 이하와 정상 범위 밖을 예외로 막는다")
  void validateExchangeRate() {
    assertThatCode(() -> MoneyUtils.validateExchangeRate(new BigDecimal("1331.5"))).doesNotThrowAnyException();
    assertThatThrownBy(() -> MoneyUtils.validateExchangeRate(BigDecimal.ZERO))
      .isInstanceOf(ServiceException.class).hasMessageContaining("0보다 커야");
    assertThatThrownBy(() -> MoneyUtils.validateExchangeRate(new BigDecimal("13.315")))
      .isInstanceOf(ServiceException.class).hasMessageContaining("정상 범위");
  }

  @Nested
  @DisplayName("반올림")
  class Rounding {

    @Test
    @DisplayName("원화는 정수, 달러는 센트, 환율은 소수 여섯째 자리로 반올림하고 값이 없으면 0 이다")
    void roundsByCurrency() {
      assertThat(MoneyUtils.roundKrw(new BigDecimal("1234.5"))).isEqualByComparingTo("1235");
      assertThat(MoneyUtils.roundUsd(new BigDecimal("1.005"))).isEqualByComparingTo("1.01");
      assertThat(MoneyUtils.roundExchangeRate(new BigDecimal("1331.1234565"))).isEqualByComparingTo("1331.123457");
      assertThat(MoneyUtils.roundKrw(null)).isEqualByComparingTo("0");
      assertThat(MoneyUtils.roundUsd(null)).isEqualByComparingTo("0");
      assertThat(MoneyUtils.roundExchangeRate(null)).isEqualByComparingTo("0");
    }
  }

  @Nested
  @DisplayName("환전")
  class Exchange {

    @Test
    @DisplayName("원화를 달러로 바꾸면 센트에서, 달러를 원화로 바꾸면 원에서 반올림한다")
    void convertsAndRounds() {
      assertThat(MoneyUtils.convertKrwToUsd(new BigDecimal("10000"), RATE)).isEqualByComparingTo("7.69");
      assertThat(MoneyUtils.convertUsdToKrw(new BigDecimal("7.69"), new BigDecimal("1300.5"))).isEqualByComparingTo("10001");
      assertThat(MoneyUtils.calculateKrwToUsd(new BigDecimal("10000"), RATE)).isEqualByComparingTo("7.69");
      assertThat(MoneyUtils.calculateUsdToKrw(BigDecimal.TEN, RATE)).isEqualByComparingTo("13000");
    }

    @Test
    @DisplayName("금액이나 환율이 없거나 0 이하이면 막는다")
    void rejectsMissingOrNonPositiveInputs() {
      assertThatThrownBy(() -> MoneyUtils.convertKrwToUsd(BigDecimal.ZERO, RATE))
        .isInstanceOf(ServiceException.class).hasMessageContaining("금액은 0보다 커야");
      assertThatThrownBy(() -> MoneyUtils.convertUsdToKrw(BigDecimal.ONE, null))
        .isInstanceOf(ServiceException.class).hasMessageContaining("환율은 0보다 커야");
      assertThatThrownBy(() -> MoneyUtils.calculateKrwToUsd(null, RATE))
        .isInstanceOf(ServiceException.class).hasMessageContaining("KRW 금액이(가) null");
      assertThatThrownBy(() -> MoneyUtils.calculateUsdToKrw(BigDecimal.TEN, BigDecimal.ZERO))
        .isInstanceOf(ServiceException.class).hasMessageContaining("환율은 0보다 커야");
    }

    @Test
    @DisplayName("통화별 최소 금액과 원화 1회 최대 금액을 넘는 환전은 막는다")
    void enforcesExchangeLimits() {
      assertThatThrownBy(() -> MoneyUtils.calculateKrwToUsd(new BigDecimal("999"), RATE))
        .isInstanceOf(ServiceException.class).hasMessageContaining("최소 환전 금액은 1000 KRW");
      assertThatThrownBy(() -> MoneyUtils.calculateUsdToKrw(new BigDecimal("0.99"), RATE))
        .isInstanceOf(ServiceException.class).hasMessageContaining("최소 환전 금액은 1.00 USD");
      assertThatThrownBy(() -> MoneyUtils.calculateKrwToUsd(new BigDecimal("50000001"), RATE))
        .isInstanceOf(ServiceException.class).hasMessageContaining("1회 최대 환전 금액은 50000000원");
      assertThatCode(() -> MoneyUtils.calculateKrwToUsd(new BigDecimal("50000000"), RATE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정상 범위 밖 환율이어도 환전 계산은 한다")
    void convertsWithOutOfRangeRate() {
      assertThat(MoneyUtils.calculateUsdToKrw(BigDecimal.TEN, new BigDecimal("3000"))).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("지원하지 않는 통화의 최소 금액 검사는 막는다")
    void rejectsUnsupportedCurrency() {
      assertThatThrownBy(() -> MoneyUtils.validateMinimumExchangeAmount(BigDecimal.TEN, "EUR"))
        .isInstanceOf(ServiceException.class).hasMessageContaining("지원하지 않는 통화: EUR");
    }
  }

  @Nested
  @DisplayName("잔액 검증")
  class Balance {

    @Test
    @DisplayName("잔액이 없거나 필요 금액보다 적으면 막고 같거나 많으면 통과한다")
    void requiresSufficientBalance() {
      assertThatThrownBy(() -> MoneyUtils.validateSufficientBalance(null, BigDecimal.ONE, "USD"))
        .isInstanceOf(ServiceException.class).hasMessageContaining("잔액 부족: 현재 0 USD, 필요 1 USD");
      assertThatThrownBy(() -> MoneyUtils.validateSufficientBalance(new BigDecimal("0.99"), BigDecimal.ONE, "USD"))
        .isInstanceOf(ServiceException.class).hasMessageContaining("잔액 부족");
      assertThatCode(() -> MoneyUtils.validateSufficientBalance(BigDecimal.ONE, BigDecimal.ONE, "USD"))
        .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("필요 금액이 0 이하이면 막는다")
    void rejectsNonPositiveRequiredAmount() {
      assertThatThrownBy(() -> MoneyUtils.validateSufficientBalance(BigDecimal.TEN, BigDecimal.ZERO, "USD"))
        .isInstanceOf(ServiceException.class).hasMessageContaining("필요 금액은(는) 0보다 커야");
    }
  }

  @Nested
  @DisplayName("비교 · 표시 · 계산")
  class CompareFormatCalculate {

    @Test
    @DisplayName("같은지는 통화 정밀도로 반올림해 보고, 둘 다 없으면 같고 하나만 없으면 다르다")
    void comparesByCurrencyScale() {
      assertThat(MoneyUtils.isEqual(new BigDecimal("1000.4"), new BigDecimal("1000"), "KRW")).isTrue();
      assertThat(MoneyUtils.isEqual(new BigDecimal("1.004"), new BigDecimal("1.00"), "USD")).isTrue();
      assertThat(MoneyUtils.isEqual(new BigDecimal("1.006"), new BigDecimal("1.00"), "USD")).isFalse();
      assertThat(MoneyUtils.isEqual(null, null, "KRW")).isTrue();
      assertThat(MoneyUtils.isEqual(BigDecimal.ONE, null, "KRW")).isFalse();
    }

    @Test
    @DisplayName("원화는 천 단위 쉼표와 원, 달러는 $ 와 센트, 그 밖 통화는 숫자 뒤에 통화를 붙이고 값이 없으면 0 이다")
    void formatsByCurrency() {
      assertThat(MoneyUtils.formatAmount(new BigDecimal("1234567.5"), "KRW")).isEqualTo("1,234,568원");
      assertThat(MoneyUtils.formatAmount(new BigDecimal("1234.5"), "usd")).isEqualTo("$1,234.50");
      assertThat(MoneyUtils.formatAmount(new BigDecimal("3.5"), "EUR")).isEqualTo("3.5 EUR");
      assertThat(MoneyUtils.formatAmount(null, "KRW")).isEqualTo("0");
    }

    @Test
    @DisplayName("더하기 · 빼기는 없는 값을 0 으로 보고, 곱하기는 하나라도 없으면 0 이다")
    void treatsMissingValues() {
      assertThat(MoneyUtils.add(null, BigDecimal.ONE)).isEqualByComparingTo("1");
      assertThat(MoneyUtils.add(BigDecimal.ONE, null)).isEqualByComparingTo("1");
      assertThat(MoneyUtils.subtract(null, BigDecimal.ONE)).isEqualByComparingTo("-1");
      assertThat(MoneyUtils.subtract(BigDecimal.ONE, null)).isEqualByComparingTo("1");
      assertThat(MoneyUtils.multiply(new BigDecimal("2"), new BigDecimal("3.5"))).isEqualByComparingTo("7");
      assertThat(MoneyUtils.multiply(null, BigDecimal.ONE)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("수익률은 기준값 대비 백분율을 소수 둘째 자리로 내고, 기준값이 없거나 0 이면 막는다")
    void returnRate() {
      assertThat(MoneyUtils.calculateReturnRate(new BigDecimal("100"), new BigDecimal("112.345"))).isEqualByComparingTo("12.35");
      assertThat(MoneyUtils.calculateReturnRate(new BigDecimal("100"), null)).isEqualByComparingTo("-100");
      assertThatThrownBy(() -> MoneyUtils.calculateReturnRate(BigDecimal.ZERO, BigDecimal.ONE))
        .isInstanceOf(ServiceException.class).hasMessageContaining("기준값은");
      assertThatThrownBy(() -> MoneyUtils.calculateReturnRate(null, BigDecimal.ONE))
        .isInstanceOf(ServiceException.class).hasMessageContaining("기준값은");
    }
  }
}
