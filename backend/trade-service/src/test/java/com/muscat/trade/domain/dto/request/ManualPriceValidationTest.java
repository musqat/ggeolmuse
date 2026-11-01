package com.muscat.trade.domain.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.trade.common.enums.type.PriceType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("직접입력 가격 검증")
class ManualPriceValidationTest {

  private static Validator validator;

  @BeforeAll
  static void init() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  // 직접입력 가격 규칙의 위반만 셈(다른 필드 제약과 섞이지 않게)
  private static long manualPriceViolations(Set<? extends ConstraintViolation<?>> violations) {
    return violations.stream()
      .filter(v -> v.getPropertyPath().toString().equals("manualPriceValid"))
      .count();
  }

  private static TradeRequestDto trade(PriceType type, String manualPrice) {
    TradeRequestDto request = new TradeRequestDto();
    request.setPriceType(type);
    request.setManualPrice(manualPrice == null ? null : new BigDecimal(manualPrice));
    return request;
  }

  private static TradingCapacityRequestDto capacity(PriceType type, String manualPrice) {
    TradingCapacityRequestDto request = new TradingCapacityRequestDto();
    request.setPriceType(type);
    request.setManualPrice(manualPrice == null ? null : new BigDecimal(manualPrice));
    return request;
  }

  @Test
  @DisplayName("거래: 직접입력인데 가격이 없으면 막고, 가격이 있거나 직접입력이 아니면 통과한다")
  void tradeRequest() {
    assertThat(manualPriceViolations(validator.validate(trade(PriceType.MANUAL, null)))).isEqualTo(1);
    assertThat(manualPriceViolations(validator.validate(trade(PriceType.MANUAL, "150.00")))).isZero();
    assertThat(manualPriceViolations(validator.validate(trade(PriceType.CLOSE, null)))).isZero();
  }

  @Test
  @DisplayName("거래 가능량: 직접입력인데 가격이 없으면 막고, 가격이 있거나 직접입력이 아니면 통과한다")
  void capacityRequest() {
    assertThat(manualPriceViolations(validator.validate(capacity(PriceType.MANUAL, null)))).isEqualTo(1);
    assertThat(manualPriceViolations(validator.validate(capacity(PriceType.MANUAL, "150.00")))).isZero();
    assertThat(manualPriceViolations(validator.validate(capacity(PriceType.CLOSE, null)))).isZero();
    assertThat(manualPriceViolations(validator.validate(capacity(null, null)))).isZero();
  }
}
