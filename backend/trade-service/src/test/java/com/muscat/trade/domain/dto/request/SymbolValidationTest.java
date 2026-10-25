package com.muscat.trade.domain.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("종목 코드 검증")
class SymbolValidationTest {

  private static Validator validator;

  @BeforeAll
  static void init() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  private static long symbolViolations(Set<? extends ConstraintViolation<?>> violations) {
    return violations.stream()
      .filter(v -> v.getPropertyPath().toString().equals("symbol"))
      .count();
  }

  private static TradeRequestDto trade(String symbol) {
    TradeRequestDto request = new TradeRequestDto();
    request.setSymbol(symbol);
    return request;
  }

  private static TradingCapacityRequestDto capacity(String symbol) {
    TradingCapacityRequestDto request = new TradingCapacityRequestDto();
    request.setSymbol(symbol);
    return request;
  }

  @ParameterizedTest
  @ValueSource(strings = {"AAPL", "BRK-B", "BRK.B"})
  @DisplayName("점 · 하이픈이 든 종목도 주문 · 거래 가능 수량 요청이 받는다")
  void acceptsDotAndHyphen(String symbol) {
    assertThat(symbolViolations(validator.validate(trade(symbol)))).isZero();
    assertThat(symbolViolations(validator.validate(capacity(symbol)))).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"aapl", "BRK/B", "BRK B", "ABCDEFGHIJKLMNOPQ"})
  @DisplayName("소문자 · 슬래시 · 빈칸 · 17자는 주문 · 거래 가능 수량 요청 모두 막는다")
  void rejectsOtherFormats(String symbol) {
    assertThat(symbolViolations(validator.validate(trade(symbol)))).isPositive();
    assertThat(symbolViolations(validator.validate(capacity(symbol)))).isPositive();
  }
}
