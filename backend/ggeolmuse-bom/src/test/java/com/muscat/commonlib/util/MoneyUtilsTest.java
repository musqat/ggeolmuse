package com.muscat.commonlib.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.commonlib.exception.ServiceException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("환율 범위 검사")
class MoneyUtilsTest {

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
}
